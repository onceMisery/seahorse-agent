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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.CHAT_SYSTEM_PROMPT_PATH;

/**
 * 负责聊天主链路中的响应构建与流式返回。
 * <p>
 * 这里集中处理 system-only、空检索兜底、RAG prompt 组装和流式句柄绑定。
 */
final class KernelChatResponseSupport {

    private static final double SYSTEM_RESPONSE_TEMPERATURE = 0.7D;
    private static final double MCP_RESPONSE_TEMPERATURE = 0.3D;
    private static final double MCP_RESPONSE_TOP_P = 0.8D;
    private static final double KB_RESPONSE_TEMPERATURE = 0D;
    private static final double KB_RESPONSE_TOP_P = 1D;

    private final ChatPreparationPorts preparationPorts;
    private final ChatResponsePorts responsePorts;

    KernelChatResponseSupport(ChatPreparationPorts preparationPorts, ChatResponsePorts responsePorts) {
        this.preparationPorts = Objects.requireNonNull(preparationPorts, "preparationPorts must not be null");
        this.responsePorts = Objects.requireNonNull(responsePorts, "responsePorts must not be null");
    }

    boolean handleSystemOnly(StreamChatContext context,
                             List<SubQuestionIntent> subIntents,
                             RewriteResult rewriteResult) {
        if (subIntents.isEmpty() || !allSystemOnly(subIntents)) {
            return false;
        }
        String customPrompt = resolveSystemPrompt(subIntents);
        StreamCancellationHandle handle = streamSystemResponse(context, rewriteResult, customPrompt);
        responsePorts.streamTaskPort().bindHandle(context.getTaskId(), handle);
        return true;
    }

    boolean handleEmptyRetrieval(StreamChatContext context,
                                 RetrievalContext retrievalContext,
                                 KernelChatPipeline.EmptyRetrievalStrategy strategy,
                                 String emptyRetrievalMessage) {
        if (retrievalContext != null && !retrievalContext.isEmpty()) {
            return false;
        }
        KernelChatPipeline.EmptyRetrievalStrategy effective = Objects.requireNonNullElse(strategy,
                KernelChatPipeline.EmptyRetrievalStrategy.FALLBACK_GENERIC);
        if (effective == KernelChatPipeline.EmptyRetrievalStrategy.STATIC_MESSAGE) {
            StreamCallback callback = requireCallback(context);
            callback.onContent(Objects.requireNonNullElse(emptyRetrievalMessage, ""));
            callback.onComplete();
            return true;
        }
        // FALLBACK_GENERIC：复用通用 system prompt 走流式 LLM，对齐 Agent 通用对话能力。
        StreamCancellationHandle handle = streamSystemResponse(context, requireRewriteResultFromContext(context), null);
        responsePorts.streamTaskPort().bindHandle(context.getTaskId(), handle);
        return true;
    }

    private RewriteResult requireRewriteResultFromContext(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getRewriteResult(),
                new RewriteResult(context.getQuestion(), List.of()));
    }

    void streamRagResponse(StreamChatContext context,
                           RetrievalContext retrievalContext,
                           RewriteResult rewriteResult,
                           List<SubQuestionIntent> subIntents,
                           List<ChatMessage> history,
                           MemoryContext memoryContext) {
        IntentGroup mergedGroup = preparationPorts.intentResolutionPort().mergeIntentGroup(subIntents);
        PromptContext promptContext = PromptContext.builder()
                .question(rewriteResult.rewrittenQuestion())
                .mcpContext(retrievalContext.getMcpContext())
                .kbContext(retrievalContext.getKbContext())
                .mcpIntents(mergedGroup.mcpIntents())
                .kbIntents(mergedGroup.kbIntents())
                .intentChunks(retrievalContext.getIntentChunks())
                .contextPack(context.getContextPack())
                .memoryContext(memoryContext)
                .build();

        List<ChatMessage> messages = responsePorts.ragPromptPort().buildStructuredMessages(
                promptContext,
                history,
                rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions()
        );
        if (!messages.isEmpty() && messages.get(0).getRole() == ChatRole.SYSTEM) {
            String enriched = renderStaticSystemPrompt(prependCoreContext(messages.get(0).getContent()));
            messages.set(0, ChatMessage.system(enriched));
            if (!hasRuntimeContextMessage(messages)) {
                runtimeContextMessage(context).ifPresent(runtimeMessage -> messages.add(1, runtimeMessage));
            }
        }
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .samplingOptions(ChatSamplingOptions.builder()
                        .thinking(context.isDeepThinking())
                        .temperature(resolveTemperature(retrievalContext))
                        .topP(resolveTopP(retrievalContext))
                        .build())
                .build();
        StreamCancellationHandle handle = responsePorts.streamingChatModelPort()
                .streamChat(chatRequest, requireCallback(context));
        responsePorts.streamTaskPort().bindHandle(context.getTaskId(), handle);
    }

    private boolean allSystemOnly(List<SubQuestionIntent> subIntents) {
        return subIntents.stream()
                .allMatch(intent -> preparationPorts.intentResolutionPort().isSystemOnly(intent.intentScores()));
    }

    private String resolveSystemPrompt(List<SubQuestionIntent> subIntents) {
        return subIntents.stream()
                .flatMap(intent -> safeNodeScores(intent).stream())
                .map(IntentScore::getNode)
                .filter(Objects::nonNull)
                .map(IntentNode::getPromptTemplate)
                .map(prompt -> Objects.requireNonNullElse(prompt, ""))
                .filter(prompt -> !prompt.isBlank())
                .findFirst()
                .orElse(null);
    }

    private StreamCancellationHandle streamSystemResponse(StreamChatContext context,
                                                          RewriteResult rewriteResult,
                                                          String customPrompt) {
        String systemPrompt = customPrompt == null || customPrompt.isBlank()
                ? responsePorts.promptTemplatePort().load(CHAT_SYSTEM_PROMPT_PATH)
                : customPrompt;
        systemPrompt = prependCoreContext(systemPrompt);
        systemPrompt = renderStaticSystemPrompt(systemPrompt);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        runtimeContextMessage(context).ifPresent(messages::add);
        messages.addAll(safeHistory(context));
        messages.add(ChatMessage.user(rewriteResult.rewrittenQuestion()));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(SYSTEM_RESPONSE_TEMPERATURE)
                        .thinking(false)
                        .build())
                .build();
        return responsePorts.streamingChatModelPort().streamChat(request, requireCallback(context));
    }

    private Optional<ChatMessage> runtimeContextMessage(StreamChatContext context) {
        String contextText = responsePorts.contextWeaverPort()
                .weave(context.getContextPack(), context.getMemoryContext(), ContextBudget.defaults());
        StringBuilder builder = new StringBuilder("<runtime-context>\n")
                .append("当前时间：")
                .append(currentDateTime())
                .append(" Asia/Shanghai");
        if (!contextText.isBlank()) {
            builder.append("\n\n").append(contextText.trim());
        }
        builder.append("\n</runtime-context>");
        return Optional.of(ChatMessage.user(builder.toString()));
    }

    private boolean hasRuntimeContextMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(Objects::nonNull)
                .map(ChatMessage::getContent)
                .filter(Objects::nonNull)
                .anyMatch(content -> content.contains("<runtime-context>"));
    }

    private static final String CORE_CONTEXT_HEADER =
            "你是 SeahorseAgent，一个智能 AI 协作伙伴。当用户问你是谁时，请自我介绍并说明你的协作能力。";

    private String prependCoreContext(String systemPrompt) {
        String safe = Objects.requireNonNullElse(systemPrompt, "").trim();
        if (safe.contains("你是 SeahorseAgent") || safe.contains("SeahorseAgent")) {
            return safe;
        }
        return CORE_CONTEXT_HEADER + "\n\n" + safe;
    }

    private String renderStaticSystemPrompt(String template) {
        if (template == null || template.isBlank()) {
            return template;
        }
        return template
                .replace("{currentDateTime}", "见后续 runtime context 消息")
                .replace("{history}", "见后续 history messages");
    }

    private String currentDateTime() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private double resolveTemperature(RetrievalContext retrievalContext) {
        return retrievalContext.hasMcp() ? MCP_RESPONSE_TEMPERATURE : KB_RESPONSE_TEMPERATURE;
    }

    private double resolveTopP(RetrievalContext retrievalContext) {
        return retrievalContext.hasMcp() ? MCP_RESPONSE_TOP_P : KB_RESPONSE_TOP_P;
    }

    private List<ChatMessage> safeHistory(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getHistory(), List.of());
    }

    private List<IntentScore> safeNodeScores(SubQuestionIntent intent) {
        if (intent == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(intent.intentScores(), List.of());
    }

    private StreamCallback requireCallback(StreamChatContext context) {
        return Objects.requireNonNull(context.getCallback(), "流式回调不能为空");
    }
}
