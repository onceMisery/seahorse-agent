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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
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
    void shouldCaptureMemoryWhenResponseCompletes() {
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.prompt(GUIDANCE_PROMPT),
                RetrievalContext.builder().build());
        RecordingMemoryEnginePort memoryEnginePort = new RecordingMemoryEnginePort();
        KernelChatPipeline pipeline = pipeline(ports, memoryEnginePort);
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = context(callback);
        context.setQuestion("请记住：我喜欢 Java 后端开发");

        pipeline.execute(context);

        Assertions.assertTrue(callback.completed);
        Assertions.assertNotNull(memoryEnginePort.lastWriteRequest);
        Assertions.assertEquals("conversation-1", memoryEnginePort.lastWriteRequest.conversationId());
        Assertions.assertEquals("user-1", memoryEnginePort.lastWriteRequest.userId());
        Assertions.assertEquals("请记住：我喜欢 Java 后端开发",
                memoryEnginePort.lastWriteRequest.message().getContent());
    }

    @Test
    void shouldCaptureMemoryCandidateWhenResponseErrors() {
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), RetrievalContext.builder().build());
        ports.streamError = new IllegalStateException("model unavailable");
        RecordingMemoryIngestionWorkflowPort workflowPort = new RecordingMemoryIngestionWorkflowPort();
        KernelChatPipeline pipeline = pipeline(ports, workflowPort);
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = context(callback);
        context.setQuestion("我是学生");

        pipeline.execute(context);

        Assertions.assertFalse(callback.completed);
        Assertions.assertEquals("model unavailable", callback.error.getMessage());
        Assertions.assertNotNull(workflowPort.lastCommand);
        Assertions.assertEquals("chat-completed", workflowPort.lastCommand.source());
        Assertions.assertEquals("conversation-1", workflowPort.lastCommand.writeRequest().conversationId());
        Assertions.assertEquals("user-1", workflowPort.lastCommand.writeRequest().userId());
        Assertions.assertEquals("我是学生", workflowPort.lastCommand.writeRequest().message().getContent());
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

    private KernelChatPipeline pipeline(RecordingChatPorts ports, MemoryEnginePort memoryEnginePort) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(ports, memoryEnginePort,
                QueryOptimizerPort.passthrough(), ports, ports, ports, ports);
        ChatResponsePorts responsePorts = new ChatResponsePorts(ports, ports, ports, ports);
        return new KernelChatPipeline(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
    }

    private KernelChatPipeline pipeline(RecordingChatPorts ports, MemoryIngestionWorkflowPort workflowPort) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                ports,
                MemoryEnginePort.noop(),
                workflowPort,
                QueryOptimizerPort.passthrough(),
                ports,
                ports,
                ports,
                ports);
        ChatResponsePorts responsePorts = new ChatResponsePorts(ports, ports, ports, ports);
        return new KernelChatPipeline(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
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
        private RuntimeException streamError;

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
            if (streamError != null) {
                callback.onError(streamError);
            }
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
        private Throwable error;

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
            this.error = error;
        }
    }

    private static final class RecordingMemoryIngestionWorkflowPort implements MemoryIngestionWorkflowPort {

        private MemoryIngestionCommand lastCommand;

        @Override
        public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
            lastCommand = command;
            return MemoryIngestionResult.accepted(MemoryIngestionAction.ADD, List.of("profile:occupation"));
        }
    }

    private static final class RecordingMemoryEnginePort implements MemoryEnginePort {

        private MemoryWriteRequest lastWriteRequest;

        @Override
        public MemoryContext loadMemory(MemoryLoadRequest request) {
            return MemoryContext.builder()
                    .workingMemory(List.of())
                    .shortTermMemories(List.of())
                    .longTermMemories(List.of())
                    .semanticMemories(List.of())
                    .promptMessages(List.of())
                    .build();
        }

        @Override
        public void writeMemory(MemoryWriteRequest request) {
            lastWriteRequest = request;
        }

        @Override
        public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
            return List.of();
        }

        @Override
        public void executeMemoryDecay() {
        }

        @Override
        public MemoryQualityReport assessMemoryQuality(String userId) {
            return MemoryQualityReport.builder().userId(userId).build();
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
