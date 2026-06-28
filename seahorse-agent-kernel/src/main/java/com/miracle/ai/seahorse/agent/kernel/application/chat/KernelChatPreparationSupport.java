/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.InteractiveMemoryConflictPrompt;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.DEFAULT_TOP_K;

/**
 * 负责聊天主链路的前置阶段：历史加载、记忆激活、查询优化、改写、意图和检索。
 * <p>
 * 这些阶段都采用 fail-open 语义，失败时尽量回退到已有上下文，避免阻断流式响应。
 */
final class KernelChatPreparationSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatPreparationSupport.class);
    private static final String MEMORY_CONFLICT_PROMPT_EVENT = "memory.conflict.prompt";
    private static final String PENDING_STATUS = "PENDING";
    private static final int CONFLICT_SCAN_LIMIT = 20;
    private static final int MAX_INTERACTIVE_CONFLICTS = 3;

    private final ChatPreparationPorts preparationPorts;

    KernelChatPreparationSupport(ChatPreparationPorts preparationPorts) {
        this.preparationPorts = Objects.requireNonNull(preparationPorts, "preparationPorts must not be null");
    }

    void installMemoryCapture(StreamChatContext context) {
        StreamCallback callback = context.getCallback();
        if (callback == null) {
            return;
        }
        if (preparationPorts.memoryAggregationPolicy().enabled()) {
            context.setCallback(MemoryTurnCaptureStage.wrap(
                    callback,
                    preparationPorts.memoryAggregationServicePort(),
                    preparationPorts.memoryAggregationPolicy(),
                    context));
            return;
        }
        context.setCallback(MemoryCaptureStage.wrap(callback, preparationPorts.memoryIngestionWorkflowPort(), context));
    }

    void loadMemory(StreamChatContext context) {
        List<ChatMessage> history;
        if (context.getAssistantParentMessageId() != null) {
            history = preparationPorts.memoryPort().loadBranchPath(
                    context.getConversationId(),
                    context.getUserId(),
                    context.getAssistantParentMessageId());
        } else {
            history = preparationPorts.memoryPort().loadAndAppend(
                    context.getConversationId(),
                    context.getUserId(),
                    ChatMessage.user(context.getQuestion()),
                    context.getBranchLeafMessageId()
            );
        }
        context.setHistory(history);
    }

    void activateMemory(StreamChatContext context) {
        MemoryLoadRequest request = MemoryLoadRequest.builder()
                .conversationId(context.getConversationId())
                .userId(context.getUserId())
                .currentQuestion(context.getQuestion())
                .knowledgeBaseIds(context.getKnowledgeBaseIds())
                .build();
        try {
            MemoryContext memoryContext = preparationPorts.memoryEnginePort().loadMemory(request);
            context.setMemoryContext(memoryContext);
        } catch (Exception ex) {
            LOG.warn("记忆激活失败，降级为无记忆模式: userId={}", context.getUserId(), ex);
        }
    }

    void emitInteractiveMemoryConflicts(StreamChatContext context) {
        StreamCallback callback = context.getCallback();
        if (callback == null || isBlank(context.getUserId())) {
            return;
        }
        try {
            var repository = preparationPorts.memoryConflictLogRepositoryPort();
            List<MemoryConflictRecord> records = repository.listByUser(
                    context.getUserId(), PENDING_STATUS, CONFLICT_SCAN_LIMIT);
            List<InteractiveMemoryConflictPrompt> prompts = records.stream()
                    .filter(Objects::nonNull)
                    .filter(this::isInteractiveCandidate)
                    .sorted(Comparator
                            .comparingInt(this::severityRank)
                            .thenComparing(MemoryConflictRecord::createTime, Comparator.reverseOrder()))
                    .limit(MAX_INTERACTIVE_CONFLICTS)
                    .map(this::toPrompt)
                    .toList();
            context.setInteractiveMemoryConflictPrompts(prompts);
            for (InteractiveMemoryConflictPrompt prompt : prompts) {
                callback.onEvent(MEMORY_CONFLICT_PROMPT_EVENT, prompt.toEventPayload());
            }
        } catch (Exception ex) {
            LOG.warn("交互式记忆冲突提示加载失败，按普通聊天降级: userId={}", context.getUserId(), ex);
        }
    }

    void optimizeQuery(StreamChatContext context) {
        try {
            QueryOptimizationResult result = preparationPorts.queryOptimizerPort().optimize(
                    context.getOriginalQuestion(),
                    safeHistory(context),
                    context.getMemoryContext());
            context.setQueryOptimizationResult(result);
            if (result != null && !result.protectedTerms().isEmpty()) {
                LOG.debug("查询优化检测到保护词: question={}, protected={}",
                        context.getOriginalQuestion(), result.protectedTerms().keySet());
            }
            if (result != null && !result.expandedTerms().isEmpty()) {
                LOG.debug("查询优化检测到扩展词: question={}, expanded={}",
                        context.getOriginalQuestion(), result.expandedTerms());
            }
        } catch (Exception ex) {
            LOG.warn("查询优化失败，降级使用原始问题: question={}", context.getOriginalQuestion(), ex);
        }
    }

    void rewriteQuery(StreamChatContext context) {
        String input = resolveRewriteInput(context);
        RewriteResult rewriteResult = preparationPorts.queryRewritePort()
                .rewriteWithSplit(input, safeHistory(context));
        context.setRewriteResult(rewriteResult);
    }

    void resolveIntents(StreamChatContext context) {
        List<SubQuestionIntent> subIntents = preparationPorts.intentResolutionPort()
                .resolve(context.getRewriteResult());
        context.setSubIntents(subIntents);
    }

    boolean handleGuidance(StreamChatContext context) {
        RewriteResult rewriteResult = requireRewriteResult(context);
        GuidanceDecision decision = preparationPorts.intentGuidancePort()
                .detectAmbiguity(rewriteResult.rewrittenQuestion(), safeSubIntents(context));
        if (!decision.isPrompt()) {
            return false;
        }
        StreamCallback callback = requireCallback(context);
        callback.onContent(decision.getPrompt());
        callback.onComplete();
        return true;
    }

    RetrievalContext retrieve(StreamChatContext context) {
        return preparationPorts.retrievalContextPort()
                .retrieve(safeSubIntents(context), DEFAULT_TOP_K, retrievalFilter(context), context.getTraceRunScope(),
                        context.getQueryOptimizationResult());
    }

    private RetrievalFilter retrievalFilter(StreamChatContext context) {
        List<String> knowledgeBaseIds = context == null ? List.of() : context.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return null;
        }
        return RetrievalFilter.builder()
                .system(SystemRetrievalFilter.builder()
                        .knowledgeBaseIds(knowledgeBaseIds)
                        .build())
                .build();
    }

    List<ChatMessage> safeHistory(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getHistory(), List.of());
    }

    List<SubQuestionIntent> safeSubIntents(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getSubIntents(), List.of());
    }

    RewriteResult requireRewriteResult(StreamChatContext context) {
        return Objects.requireNonNull(context.getRewriteResult(), "查询改写结果不能为空");
    }

    private String resolveRewriteInput(StreamChatContext context) {
        QueryOptimizationResult optimizationResult = context.getQueryOptimizationResult();
        if (optimizationResult != null
                && optimizationResult.optimizedQuestion() != null
                && !optimizationResult.optimizedQuestion().isBlank()) {
            return optimizationResult.optimizedQuestion();
        }
        return context.getOriginalQuestion();
    }

    private boolean isInteractiveCandidate(MemoryConflictRecord record) {
        if (record == null) {
            return false;
        }
        String status = normalize(record.resolutionStatus());
        String type = normalize(record.conflictType());
        int severity = severityRank(record);
        return PENDING_STATUS.equals(status)
                && !"DUPLICATE_NEAR".equals(type)
                && severity < 2;
    }

    private int severityRank(MemoryConflictRecord record) {
        String severity = normalize(record == null ? null : record.severity());
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private InteractiveMemoryConflictPrompt toPrompt(MemoryConflictRecord record) {
        return new InteractiveMemoryConflictPrompt(
                record.id(),
                record.memoryId1(),
                record.memoryId2(),
                "",
                "",
                record.conflictType(),
                record.severity(),
                "我发现两条记忆可能不一致，请确认应保留哪一条。",
                InteractiveMemoryConflictPrompt.DEFAULT_OPTIONS);
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private StreamCallback requireCallback(StreamChatContext context) {
        return Objects.requireNonNull(context.getCallback(), "流式回调不能为空");
    }
}
