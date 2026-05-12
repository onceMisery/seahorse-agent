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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.CHAT_SYSTEM_PROMPT_PATH;
import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.DEFAULT_TOP_K;

/**
 * L1 问答主链路内核编排器。
 * <p>
 * 执行顺序严格保持旧 {@code StreamChatPipeline} 的语义：
 * loadMemory -> rewriteQuery -> resolveIntents -> handleGuidance -> handleSystemOnly -> retrieve
 * -> handleEmptyRetrieval -> streamRagResponse。
 */
public class KernelChatPipeline {

    private static final String EMPTY_RETRIEVAL_MESSAGE = "未检索到与问题相关的文档内容。";
    private static final double SYSTEM_RESPONSE_TEMPERATURE = 0.7D;
    private static final double MCP_RESPONSE_TEMPERATURE = 0.3D;
    private static final double MCP_RESPONSE_TOP_P = 0.8D;
    private static final double KB_RESPONSE_TEMPERATURE = 0D;
    private static final double KB_RESPONSE_TOP_P = 1D;
    private static final String TRACE_TYPE_CHAT_STAGE = "CHAT_STAGE";

    private final ChatPreparationPorts preparationPorts;
    private final ChatResponsePorts responsePorts;
    private final KernelRagTraceRecorder traceRecorder;

    public KernelChatPipeline(ChatPreparationPorts preparationPorts, ChatResponsePorts responsePorts) {
        this(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
    }

    public KernelChatPipeline(ChatPreparationPorts preparationPorts,
                              ChatResponsePorts responsePorts,
                              KernelRagTraceRecorder traceRecorder) {
        this.preparationPorts = Objects.requireNonNull(preparationPorts, "问答前置端口集合不能为空");
        this.responsePorts = Objects.requireNonNull(responsePorts, "问答响应端口集合不能为空");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
    }

    /**
     * 执行流式问答管道。
     *
     * @param context Seahorse 流式问答上下文
     */
    public void execute(StreamChatContext context) {
        StreamChatContext safeContext = Objects.requireNonNull(context, "流式问答上下文不能为空");
        TraceRunScope traceRunScope = safeContext.getTraceRunScope();
        traceRecorder.recordNode(traceRunScope, stage("load-memory", "loadMemory"), () -> loadMemory(safeContext));
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

    private void loadMemory(StreamChatContext context) {
        List<ChatMessage> history = preparationPorts.memoryPort().loadAndAppend(
                context.getConversationId(),
                context.getUserId(),
                ChatMessage.user(context.getQuestion())
        );
        context.setHistory(history);
    }

    private void rewriteQuery(StreamChatContext context) {
        RewriteResult rewriteResult = preparationPorts.queryRewritePort()
                .rewriteWithSplit(context.getQuestion(), safeHistory(context));
        context.setRewriteResult(rewriteResult);
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
        List<SubQuestionIntent> subIntents = safeSubIntents(context);
        if (subIntents.isEmpty() || !allSystemOnly(subIntents)) {
            return false;
        }
        String customPrompt = resolveSystemPrompt(subIntents);
        StreamCancellationHandle handle = streamSystemResponse(context, customPrompt);
        responsePorts.streamTaskPort().bindHandle(context.getTaskId(), handle);
        return true;
    }

    private RetrievalContext retrieve(StreamChatContext context) {
        return preparationPorts.retrievalContextPort().retrieve(safeSubIntents(context), DEFAULT_TOP_K);
    }

    private boolean handleEmptyRetrieval(StreamChatContext context, RetrievalContext retrievalContext) {
        if (retrievalContext != null && !retrievalContext.isEmpty()) {
            return false;
        }
        StreamCallback callback = requireCallback(context);
        callback.onContent(EMPTY_RETRIEVAL_MESSAGE);
        callback.onComplete();
        return true;
    }

    private void streamRagResponse(StreamChatContext context, RetrievalContext retrievalContext) {
        IntentGroup mergedGroup = preparationPorts.intentResolutionPort().mergeIntentGroup(safeSubIntents(context));
        RagStreamRequest request = new RagStreamRequest(context.getRewriteResult(), retrievalContext, mergedGroup,
                safeHistory(context), context.isDeepThinking(), requireCallback(context));
        StreamCancellationHandle handle = streamLlmResponse(request);
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

    private StreamCancellationHandle streamSystemResponse(StreamChatContext context, String customPrompt) {
        String systemPrompt = customPrompt == null || customPrompt.isBlank()
                ? responsePorts.promptTemplatePort().load(CHAT_SYSTEM_PROMPT_PATH)
                : customPrompt;
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(safeHistory(context));
        messages.add(ChatMessage.user(requireRewriteResult(context).rewrittenQuestion()));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(SYSTEM_RESPONSE_TEMPERATURE)
                        .thinking(false)
                        .build())
                .build();
        return responsePorts.streamingChatModelPort().streamChat(request, requireCallback(context));
    }

    private StreamCancellationHandle streamLlmResponse(RagStreamRequest request) {
        PromptContext promptContext = PromptContext.builder()
                .question(request.rewriteResult().rewrittenQuestion())
                .mcpContext(request.retrievalContext().getMcpContext())
                .kbContext(request.retrievalContext().getKbContext())
                .mcpIntents(request.intentGroup().mcpIntents())
                .kbIntents(request.intentGroup().kbIntents())
                .intentChunks(request.retrievalContext().getIntentChunks())
                .build();

        List<ChatMessage> messages = responsePorts.ragPromptPort().buildStructuredMessages(
                promptContext,
                request.history(),
                request.rewriteResult().rewrittenQuestion(),
                request.rewriteResult().subQuestions()
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .samplingOptions(ChatSamplingOptions.builder()
                        .thinking(request.deepThinking())
                        .temperature(resolveTemperature(request.retrievalContext()))
                        .topP(resolveTopP(request.retrievalContext()))
                        .build())
                .build();
        return responsePorts.streamingChatModelPort().streamChat(chatRequest, request.callback());
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

    private List<SubQuestionIntent> safeSubIntents(StreamChatContext context) {
        return Objects.requireNonNullElse(context.getSubIntents(), List.of());
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

    private RewriteResult requireRewriteResult(StreamChatContext context) {
        return Objects.requireNonNull(context.getRewriteResult(), "查询改写结果不能为空");
    }

    private TraceNodeStartCommand stage(String nodeName, String methodName) {
        return TraceNodeStartCommand.pipelineStage(nodeName, TRACE_TYPE_CHAT_STAGE, methodName);
    }

    private record RagStreamRequest(RewriteResult rewriteResult,
                                    RetrievalContext retrievalContext,
                                    IntentGroup intentGroup,
                                    List<ChatMessage> history,
                                    boolean deepThinking,
                                    StreamCallback callback) {
    }
}
