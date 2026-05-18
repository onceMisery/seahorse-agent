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

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.DEFAULT_TOP_K;

/**
 * L1 问答主链路内核编排器。
 * <p>
 * 执行顺序：
 * loadMemory -> activateMemory -> optimizeQuery -> rewriteQuery -> resolveIntents
 * -> handleGuidance -> handleSystemOnly -> retrieve -> handleEmptyRetrieval -> streamRagResponse
 */
public class KernelChatPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatPipeline.class);
    private static final String EMPTY_RETRIEVAL_MESSAGE = "未检索到与问题相关的文档内容。";
    private static final String TRACE_TYPE_CHAT_STAGE = "CHAT_STAGE";

    private final ChatPreparationPorts preparationPorts;
    private final ChatResponsePorts responsePorts;
    private final KernelRagTraceRecorder traceRecorder;
    private final KernelChatResponseSupport responseSupport;

    public KernelChatPipeline(ChatPreparationPorts preparationPorts, ChatResponsePorts responsePorts) {
        this(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
    }

    public KernelChatPipeline(ChatPreparationPorts preparationPorts,
                              ChatResponsePorts responsePorts,
                              KernelRagTraceRecorder traceRecorder) {
        this.preparationPorts = Objects.requireNonNull(preparationPorts, "问答前置端口集合不能为空");
        this.responsePorts = Objects.requireNonNull(responsePorts, "问答响应端口集合不能为空");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
        this.responseSupport = new KernelChatResponseSupport(this.preparationPorts, this.responsePorts);
    }

    /**
     * 执行流式问答管道。
     *
     * @param context 流式问答上下文
     */
    public void execute(StreamChatContext context) {
        StreamChatContext safeContext = Objects.requireNonNull(context, "流式问答上下文不能为空");
        installMemoryCapture(safeContext);
        TraceRunScope traceRunScope = safeContext.getTraceRunScope();
        traceRecorder.recordNode(traceRunScope, stage("load-memory", "loadMemory"), () -> loadMemory(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("activate-memory", "activateMemory"), () -> activateMemory(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("optimize-query", "optimizeQuery"), () -> optimizeQuery(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("query-rewrite", "rewriteQuery"), () -> rewriteQuery(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("intent-resolve", "resolveIntents"), () -> resolveIntents(safeContext));

        if (traceRecorder.recordNode(traceRunScope, stage("guidance", "handleGuidance"),
                () -> handleGuidance(safeContext))) {
            return;
        }
        if (handleSystemOnly(safeContext)) {
            return;
        }

        RetrievalContext retrievalContext = traceRecorder.recordNode(traceRunScope, stage("retrieval", "retrieve"),
                () -> retrieve(safeContext));
        if (handleEmptyRetrieval(safeContext, retrievalContext)) {
            return;
        }

        traceRecorder.recordNode(traceRunScope, stage("stream-response", "streamRagResponse"),
                () -> streamRagResponse(safeContext, retrievalContext));
    }

    private void installMemoryCapture(StreamChatContext context) {
        StreamCallback callback = context.getCallback();
        if (callback == null) {
            return;
        }
        context.setCallback(MemoryCaptureStage.wrap(callback, preparationPorts.memoryEnginePort(), context));
    }

    private void loadMemory(StreamChatContext context) {
        List<ChatMessage> history = preparationPorts.memoryPort().loadAndAppend(
                context.getConversationId(),
                context.getUserId(),
                ChatMessage.user(context.getQuestion())
        );
        context.setHistory(history);
    }

    /**
     * 从四层记忆引擎激活用户记忆。
     * <p>
     * 该阶段在 loadMemory 之后执行，失败时只降级为无记忆模式，不中断主链路。
     */
    private void activateMemory(StreamChatContext context) {
        MemoryLoadRequest request = MemoryLoadRequest.builder()
                .conversationId(context.getConversationId())
                .userId(context.getUserId())
                .currentQuestion(context.getQuestion())
                .build();
        try {
            MemoryContext memoryContext = preparationPorts.memoryEnginePort().loadMemory(request);
            context.setMemoryContext(memoryContext);
        } catch (Exception ex) {
            LOG.warn("记忆激活失败，降级为无记忆模式: userId={}", context.getUserId(), ex);
        }
    }

    /**
     * 查询优化阶段负责术语保护与扩展，不强制改写问题文本。
     * <p>
     * 当前优化失败时回退到原始问题，保持主链路可用。
     */
    private void optimizeQuery(StreamChatContext context) {
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

    private void rewriteQuery(StreamChatContext context) {
        String input = resolveRewriteInput(context);
        RewriteResult rewriteResult = preparationPorts.queryRewritePort()
                .rewriteWithSplit(input, safeHistory(context));
        context.setRewriteResult(rewriteResult);
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

    private void resolveIntents(StreamChatContext context) {
        List<SubQuestionIntent> subIntents = preparationPorts.intentResolutionPort()
                .resolve(context.getRewriteResult());
        context.setSubIntents(subIntents);
    }

    private boolean handleGuidance(StreamChatContext context) {
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

    private boolean handleSystemOnly(StreamChatContext context) {
        return responseSupport.handleSystemOnly(context, safeSubIntents(context), requireRewriteResult(context));
    }

    private RetrievalContext retrieve(StreamChatContext context) {
        return preparationPorts.retrievalContextPort()
                .retrieve(safeSubIntents(context), DEFAULT_TOP_K, context.getTraceRunScope());
    }

    private boolean handleEmptyRetrieval(StreamChatContext context, RetrievalContext retrievalContext) {
        return responseSupport.handleEmptyRetrieval(context, retrievalContext, EMPTY_RETRIEVAL_MESSAGE);
    }

    private void streamRagResponse(StreamChatContext context, RetrievalContext retrievalContext) {
        responseSupport.streamRagResponse(
                context,
                retrievalContext,
                requireRewriteResult(context),
                safeSubIntents(context),
                safeHistory(context),
                context.getMemoryContext());
    }

    private List<ChatMessage> safeHistory(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getHistory(), List.of());
    }

    private List<SubQuestionIntent> safeSubIntents(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getSubIntents(), List.of());
    }

    private StreamCallback requireCallback(StreamChatContext context) {
        return Objects.requireNonNull(context.getCallback(), "流式回调不能为空");
    }

    private RewriteResult requireRewriteResult(StreamChatContext context) {
        return Objects.requireNonNull(context.getRewriteResult(), "查询改写结果不能为空");
    }

    private TraceNodeStartCommand stage(String nodeName, String methodName) {
        return TraceNodeStartCommand.pipelineStage(nodeName, TRACE_TYPE_CHAT_STAGE, methodName);
    }
}
