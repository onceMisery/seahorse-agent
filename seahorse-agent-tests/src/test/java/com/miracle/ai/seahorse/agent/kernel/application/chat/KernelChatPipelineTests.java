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
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 问答内核流水线契约测试。
 */
class KernelChatPipelineTests {

    private static final String QUESTION = "入职流程是什么";
    private static final String REWRITTEN_QUESTION = "入职流程";
    private static final String GUIDANCE_PROMPT = "请选择具体系统";

    @Test
    void shouldShortCircuitWhenGuidancePromptIsRequired() {
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.prompt(GUIDANCE_PROMPT),
                RetrievalContext.builder().build());
        KernelChatPipeline pipeline = pipeline(ports);
        RecordingCallback callback = new RecordingCallback();

        pipeline.execute(context(callback));

        Assertions.assertEquals(GUIDANCE_PROMPT, callback.content);
        Assertions.assertTrue(callback.completed);
        Assertions.assertFalse(ports.retrieved);
        Assertions.assertNull(ports.lastChatRequest);
    }

    @Test
    void shouldStreamRagResponseWithMcpSamplingParameters() {
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .mcpContext("实时数据")
                .intentChunks(Map.of())
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        KernelChatPipeline pipeline = pipeline(ports);
        RecordingCallback callback = new RecordingCallback();

        pipeline.execute(context(callback));

        Assertions.assertTrue(ports.retrieved);
        Assertions.assertNotNull(ports.lastChatRequest);
        Assertions.assertEquals(0.3D, ports.lastChatRequest.getTemperature());
        Assertions.assertEquals(0.8D, ports.lastChatRequest.getTopP());
        Assertions.assertEquals("task-1", ports.boundTaskId);
    }

    @Test
    void shouldRecordTraceNodesForMainPipelineStages() {
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("知识库内容")
                .intentChunks(Map.of())
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        RecordingTraceRepository repository = new RecordingTraceRepository();
        KernelRagTraceRecorder recorder = new KernelRagTraceRecorder(repository);
        KernelChatPipeline pipeline = pipeline(ports, recorder);
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = context(callback);
        context.setTraceRunScope(recorder.startRun(new TraceRunStartCommand(
                "stream-chat", "KernelChatInboundService#streamChat", "conversation-1", "task-1", "user-1")));

        pipeline.execute(context);

        Assertions.assertEquals(List.of("load-memory", "activate-memory", "optimize-query", "query-rewrite",
                "intent-resolve", "guidance", "retrieval", "stream-response"), repository.startedNodeNames());
        Assertions.assertEquals(8, repository.finishedNodes.size());
        Assertions.assertTrue(repository.finishedNodes.stream()
                .allMatch(finish -> KernelRagTraceRecorder.STATUS_SUCCESS.equals(finish.status())));
    }

    private KernelChatPipeline pipeline(RecordingChatPorts ports) {
        return pipeline(ports, KernelRagTraceRecorder.noop());
    }

    private KernelChatPipeline pipeline(RecordingChatPorts ports, KernelRagTraceRecorder traceRecorder) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(ports, ports, ports, ports, ports);
        ChatResponsePorts responsePorts = new ChatResponsePorts(ports, ports, ports, ports);
        return new KernelChatPipeline(preparationPorts, responsePorts, traceRecorder);
    }

    private StreamChatContext context(StreamCallback callback) {
        return StreamChatContext.builder()
                .question(QUESTION)
                .conversationId("conversation-1")
                .taskId("task-1")
                .userId("user-1")
                .callback(callback)
                .build();
    }

    private static final class RecordingChatPorts implements ConversationMemoryPort,
            QueryRewritePort,
            IntentResolutionPort,
            IntentGuidancePort,
            RetrievalContextPort,
            RagPromptPort,
            PromptTemplatePort,
            StreamingChatModelPort,
            StreamTaskPort {

        private final GuidanceDecision guidanceDecision;
        private final RetrievalContext retrievalContext;
        private boolean retrieved;
        private ChatRequest lastChatRequest;
        private String boundTaskId;

        private RecordingChatPorts(GuidanceDecision guidanceDecision, RetrievalContext retrievalContext) {
            this.guidanceDecision = guidanceDecision;
            this.retrievalContext = retrievalContext;
        }

        @Override
        public List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
            return List.of(ChatMessage.user("历史问题"));
        }

        @Override
        public RewriteResult rewriteWithSplit(String question, List<ChatMessage> history) {
            return new RewriteResult(REWRITTEN_QUESTION, List.of(REWRITTEN_QUESTION));
        }

        @Override
        public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
            return List.of(new SubQuestionIntent(REWRITTEN_QUESTION, List.of()));
        }

        @Override
        public boolean isSystemOnly(List<IntentScore> intentScores) {
            return false;
        }

        @Override
        public IntentGroup mergeIntentGroup(List<SubQuestionIntent> subIntents) {
            return new IntentGroup(List.of(), List.of());
        }

        @Override
        public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
            return guidanceDecision;
        }

        @Override
        public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
            retrieved = true;
            return retrievalContext;
        }

        @Override
        public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                         List<ChatMessage> history,
                                                         String question,
                                                         List<String> subQuestions) {
            return List.of(ChatMessage.user(question));
        }

        @Override
        public String load(String path) {
            return "系统提示";
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            lastChatRequest = request;
            return () -> {
            };
        }

        @Override
        public void register(String taskId,
                             StreamEventSender sender,
                             Supplier<StreamCompletionPayload> onCancelSupplier) {
        }

        @Override
        public void bindHandle(String taskId, StreamCancellationHandle handle) {
            boundTaskId = taskId;
        }

        @Override
        public boolean isCancelled(String taskId) {
            return false;
        }

        @Override
        public void cancel(String taskId) {
        }

        @Override
        public void unregister(String taskId) {
        }
    }

    private static final class RecordingCallback implements StreamCallback {

        private String content;
        private boolean completed;

        @Override
        public void onContent(String content) {
            this.content = content;
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {

        private final List<RagTraceNode> startedNodes = new java.util.ArrayList<>();
        private final List<RagTraceNodeFinish> finishedNodes = new java.util.ArrayList<>();

        @Override
        public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
            return new RagTracePage<>(1, 10, 0, List.of());
        }

        @Override
        public Optional<RagTraceRun> findRun(String traceId) {
            return Optional.empty();
        }

        @Override
        public List<RagTraceNode> listNodes(String traceId) {
            return List.of();
        }

        @Override
        public void startRun(RagTraceRun run) {
        }

        @Override
        public void finishRun(RagTraceRunFinish finish) {
        }

        @Override
        public void startNode(RagTraceNode node) {
            startedNodes.add(node);
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
            finishedNodes.add(finish);
        }

        private List<String> startedNodeNames() {
            return startedNodes.stream()
                    .map(RagTraceNode::getNodeName)
                    .toList();
        }
    }
}
