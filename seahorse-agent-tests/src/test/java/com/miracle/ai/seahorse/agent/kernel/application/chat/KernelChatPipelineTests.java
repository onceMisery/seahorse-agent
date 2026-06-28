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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildItemCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextBuildRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachment;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachmentParseStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        Assertions.assertNotNull(ports.lastPromptContext);
        Assertions.assertNull(ports.lastPromptContext.getContextPack());
        Assertions.assertEquals("task-1", ports.boundTaskId);
    }

    @Test
    void shouldPassExpandedTermsFromQueryOptimizationToRetrieval() {
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("知识库内容")
                .intentChunks(Map.of())
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        QueryOptimizerPort optimizerPort = (question, history, memoryContext) -> new QueryOptimizationResult(
                question,
                question,
                Map.of("MQ", "term_mapping"),
                List.of("Pulsar", "Kafka"),
                List.of("term_expansion"));
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                ports,
                MemoryEnginePort.noop(),
                optimizerPort,
                ports,
                ports,
                ports,
                ports);
        ChatResponsePorts responsePorts = new ChatResponsePorts(ports, ports, ports, ports);
        KernelChatPipeline pipeline = new KernelChatPipeline(preparationPorts, responsePorts);

        pipeline.execute(context(new RecordingCallback()));

        Assertions.assertEquals(List.of("Pulsar", "Kafka"), ports.lastExpandedTerms);
    }

    @Test
    void shouldPassKnowledgeBaseScopeToRetrieval() {
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("知识库内容")
                .intentChunks(Map.of())
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        KernelChatPipeline pipeline = pipeline(ports);
        StreamChatContext context = context(new RecordingCallback());
        context.setKnowledgeBaseIds(List.of("42", "43"));

        pipeline.execute(context);

        Assertions.assertNotNull(ports.lastRetrievalFilter);
        Assertions.assertEquals(List.of("42", "43"), ports.lastRetrievalFilter.system().knowledgeBaseIds());
    }

    @Test
    void shouldPassKnowledgeBaseScopeToMemoryActivation() {
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), RetrievalContext.builder().build());
        RecordingMemoryEnginePort memoryEnginePort = new RecordingMemoryEnginePort();
        KernelChatPipeline pipeline = pipeline(ports, memoryEnginePort);
        StreamChatContext context = context(new RecordingCallback());
        context.setKnowledgeBaseIds(List.of("42", "43"));

        pipeline.execute(context);

        Assertions.assertNotNull(memoryEnginePort.lastLoadRequest);
        Assertions.assertEquals(List.of("42", "43"), memoryEnginePort.lastLoadRequest.knowledgeBaseIds());
    }

    @Test
    void shouldExposeProducedContextPackToRagPromptAssembly() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("chunk-1")
                .tenantId("tenant-a")
                .kbId("kb-1")
                .docId("doc-1")
                .text("onboarding requires contract and device pickup")
                .score(0.92F)
                .build();
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("kb context")
                .intentChunks(Map.of(REWRITTEN_QUESTION, List.of(chunk)))
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        RecordingContextPackBuilder builder = new RecordingContextPackBuilder();
        KernelChatPipeline pipeline = pipeline(ports, fixedMemoryEngine(memoryContext()), builder);
        RecordingCallback callback = new RecordingCallback();

        pipeline.execute(context(callback));

        Assertions.assertNotNull(builder.lastRequest);
        Assertions.assertEquals("ctx-run-task-1", builder.lastRequest.runId());
        Assertions.assertEquals("default", builder.lastRequest.tenantId());
        Assertions.assertEquals("user-1", builder.lastRequest.userId());
        Assertions.assertEquals(REWRITTEN_QUESTION, builder.lastRequest.taskGoal());
        Assertions.assertEquals(3, builder.lastRequest.candidates().size());
        Assertions.assertEquals(List.of(ContextItemSourceType.USER_INPUT, ContextItemSourceType.MEMORY,
                        ContextItemSourceType.RAG_CHUNK),
                builder.lastRequest.candidates().stream()
                        .map(ContextBuildItemCandidate::sourceType)
                        .toList());
        Assertions.assertEquals(List.of(ContextResourceType.USER_INPUT.value(), ContextResourceType.MEMORY.value(),
                        ContextResourceType.DOCUMENT.value()),
                builder.lastRequest.candidates().stream()
                        .map(candidate -> candidate.resourceRef().resourceType())
                        .toList());
        Assertions.assertNotNull(ports.lastPromptContext);
        Assertions.assertNotNull(ports.lastPromptContext.getContextPack());
        Assertions.assertFalse(ports.lastPromptContext.getContextPack().items().isEmpty());
    }

    @Test
    void shouldAddConversationAttachmentContentToContextPack() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("chunk-1")
                .tenantId("tenant-a")
                .kbId("kb-1")
                .docId("doc-1")
                .text("baseline retrieval result")
                .score(0.92F)
                .build();
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("kb context")
                .intentChunks(Map.of(REWRITTEN_QUESTION, List.of(chunk)))
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        RecordingContextPackBuilder builder = new RecordingContextPackBuilder();
        InMemoryConversationAttachmentRepository attachments = new InMemoryConversationAttachmentRepository(
                new ConversationAttachment(
                        "attachment-1",
                        "conversation-1",
                        null,
                        "user-1",
                        "requirements.txt",
                        "text/plain",
                        38L,
                        "memory://attachment-1",
                        ConversationAttachmentParseStatus.PENDING,
                        "{}",
                        Instant.EPOCH));
        ConversationAttachmentContextAssembler attachmentContextAssembler =
                new ConversationAttachmentContextAssembler(
                        attachments,
                        new InMemoryObjectStoragePort("must support invoice exports"),
                        DocumentParserPort.plainText());
        KernelChatPipeline pipeline = pipeline(
                ports,
                fixedMemoryEngine(memoryContext()),
                builder,
                attachmentContextAssembler);
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = context(callback);
        context.setAttachmentIds(List.of("attachment-1"));

        pipeline.execute(context);

        Assertions.assertNotNull(builder.lastRequest);
        List<ContextBuildItemCandidate> candidates = builder.lastRequest.candidates();
        Assertions.assertTrue(candidates.stream()
                .anyMatch(candidate -> candidate.sourceType() == ContextItemSourceType.CONVERSATION_ATTACHMENT
                        && candidate.content().contains("must support invoice exports")
                        && candidate.resourceRef().resourceType().equals(ContextResourceType.DOCUMENT.value())
                        && candidate.resourceRef().ownerUserId().equals("user-1")));
        Assertions.assertEquals(ConversationAttachmentParseStatus.PARSED,
                attachments.saved.parseStatus());
        Assertions.assertNotNull(ports.lastPromptContext);
        Assertions.assertNotNull(ports.lastPromptContext.getContextPack());
        Assertions.assertTrue(ports.lastPromptContext.getContextPack().items().stream()
                .anyMatch(item -> item.sourceType() == ContextItemSourceType.CONVERSATION_ATTACHMENT
                        && item.content().contains("must support invoice exports")));
    }

    @Test
    void shouldFallbackToLegacyPromptContextWhenContextPackBuilderFails() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("chunk-1")
                .tenantId("tenant-a")
                .kbId("kb-1")
                .docId("doc-1")
                .text("onboarding requires contract and device pickup")
                .score(0.92F)
                .build();
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("kb context")
                .intentChunks(Map.of(REWRITTEN_QUESTION, List.of(chunk)))
                .build();
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(), retrievalContext);
        KernelChatPipeline pipeline = pipeline(ports, fixedMemoryEngine(memoryContext()), request -> {
            throw new IllegalStateException("context builder unavailable");
        });

        pipeline.execute(context(new RecordingCallback()));

        Assertions.assertTrue(ports.retrieved);
        Assertions.assertNotNull(ports.lastPromptContext);
        Assertions.assertNull(ports.lastPromptContext.getContextPack());
        Assertions.assertNotNull(ports.lastPromptContext.getMemoryContext());
        Assertions.assertNotNull(ports.lastChatRequest);
    }

    @Test
    void shouldKeepSystemPromptStaticAndInjectRuntimeContextAfterSystemPrompt() {
        RecordingChatPorts ports = new RecordingChatPorts(GuidanceDecision.none(),
                RetrievalContext.builder().build());
        RecordingContextPackBuilder builder = new RecordingContextPackBuilder();
        KernelChatPipeline pipeline = pipeline(ports, fixedMemoryEngine(memoryContext()), builder);

        pipeline.execute(context(new RecordingCallback()));

        Assertions.assertNotNull(ports.lastChatRequest);
        List<ChatMessage> messages = ports.lastChatRequest.getMessages();
        Assertions.assertTrue(messages.size() >= 3);
        Assertions.assertEquals(ChatRole.SYSTEM, messages.get(0).getRole());
        Assertions.assertFalse(messages.get(0).getContent().contains("ContextPack context:"));
        Assertions.assertFalse(messages.get(0).getContent().contains("user is interested in HR onboarding"));
        Assertions.assertFalse(messages.get(0).getContent().contains("历史问题"));
        Assertions.assertFalse(messages.get(0).getContent().contains("{currentDateTime}"));
        Assertions.assertEquals(ChatRole.USER, messages.get(1).getRole());
        Assertions.assertTrue(messages.get(1).getContent().contains("当前时间"));
        Assertions.assertTrue(messages.get(1).getContent().contains("ContextPack context:"));
        Assertions.assertTrue(messages.get(1).getContent().contains("user is interested in HR onboarding"));
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

        Assertions.assertEquals(List.of("load-memory", "activate-memory", "memory-conflicts", "optimize-query",
                "query-rewrite", "intent-resolve", "guidance", "retrieval", "stream-response"),
                repository.startedNodeNames());
        Assertions.assertEquals(9, repository.finishedNodes.size());
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

    private KernelChatPipeline pipeline(RecordingChatPorts ports,
                                        MemoryEnginePort memoryEnginePort,
                                        ContextPackBuilderInboundPort contextPackBuilder) {
        return pipeline(ports, memoryEnginePort, contextPackBuilder,
                ConversationAttachmentContextAssembler.noop());
    }

    private KernelChatPipeline pipeline(RecordingChatPorts ports,
                                        MemoryEnginePort memoryEnginePort,
                                        ContextPackBuilderInboundPort contextPackBuilder,
                                        ConversationAttachmentContextAssembler attachmentContextAssembler) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(ports, memoryEnginePort,
                QueryOptimizerPort.passthrough(), ports, ports, ports, ports);
        ChatResponsePorts responsePorts = new ChatResponsePorts(ports, ports, ports, ports);
        return new KernelChatPipeline(preparationPorts, responsePorts, KernelRagTraceRecorder.noop(),
                KernelChatPipeline.EmptyRetrievalStrategy.FALLBACK_GENERIC,
                Optional.of(contextPackBuilder),
                attachmentContextAssembler);
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

    private MemoryContext memoryContext() {
        return MemoryContext.builder()
                .conversationId("conversation-1")
                .userId("user-1")
                .currentQuestion(QUESTION)
                .semanticMemories(List.of(MemoryItem.builder()
                        .id("mem-1")
                        .userId("user-1")
                        .layer(MemoryLayer.SEMANTIC)
                        .content("user is interested in HR onboarding")
                        .relevanceScore(0.88D)
                        .confidenceLevel(0.93D)
                        .build()))
                .build();
    }

    private MemoryEnginePort fixedMemoryEngine(MemoryContext memoryContext) {
        return new MemoryEnginePort() {
            @Override
            public MemoryContext loadMemory(MemoryLoadRequest request) {
                return memoryContext;
            }

            @Override
            public void writeMemory(MemoryWriteRequest request) {
            }

            @Override
            public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
                return memoryContext.getSemanticMemories() == null ? List.of() : memoryContext.getSemanticMemories();
            }

            @Override
            public void executeMemoryDecay() {
            }

            @Override
            public MemoryQualityReport assessMemoryQuality(String userId) {
                return MemoryQualityReport.builder().userId(userId).build();
            }
        };
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
        private PromptContext lastPromptContext;
        private List<String> lastExpandedTerms = List.of();
        private RetrievalFilter lastRetrievalFilter;
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
        public RetrievalContext retrieve(List<SubQuestionIntent> subIntents,
                                         int topK,
                                         RetrievalFilter filter,
                                         com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope traceRunScope,
                                         QueryOptimizationResult queryOptimizationResult) {
            retrieved = true;
            lastRetrievalFilter = filter;
            lastExpandedTerms = queryOptimizationResult == null
                    ? List.of()
                    : queryOptimizationResult.expandedTerms();
            return retrievalContext;
        }

        @Override
        public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                         List<ChatMessage> history,
                                                         String question,
                                                         List<String> subQuestions) {
            lastPromptContext = context;
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

    private static final class RecordingContextPackBuilder implements ContextPackBuilderInboundPort {

        private ContextBuildRequest lastRequest;

        @Override
        public ContextPack build(ContextBuildRequest request) {
            lastRequest = request;
            List<ContextItem> items = request.candidates().stream()
                    .map(this::item)
                    .toList();
            return new ContextPack("ctx-pack-1", request.runId(), request.agentId(), request.versionId(),
                    request.tenantId(), request.userId(), request.taskGoal(), request.budgetTokens(), items,
                    java.time.Instant.EPOCH);
        }

        private ContextItem item(ContextBuildItemCandidate candidate) {
            return new ContextItem(
                    "item-" + candidate.sourceId(),
                    "ctx-pack-1",
                    candidate.sourceType(),
                    candidate.sourceId(),
                    candidate.content(),
                    candidate.summary(),
                    candidate.score(),
                    candidate.confidence(),
                    candidate.sensitivity(),
                    "allow-" + candidate.sourceId(),
                    candidate.citationJson(),
                    candidate.estimatedTokens(),
                    candidate.expiresAt(),
                    java.time.Instant.EPOCH);
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

    private static final class InMemoryConversationAttachmentRepository
            implements ConversationAttachmentRepositoryPort {

        private ConversationAttachment saved;

        private InMemoryConversationAttachmentRepository(ConversationAttachment attachment) {
            this.saved = attachment;
        }

        @Override
        public ConversationAttachment save(ConversationAttachment attachment) {
            saved = attachment;
            return attachment;
        }

        @Override
        public Optional<ConversationAttachment> findById(String attachmentId) {
            if (saved == null || !saved.attachmentId().equals(attachmentId)) {
                return Optional.empty();
            }
            return Optional.of(saved);
        }

        @Override
        public List<ConversationAttachment> listByConversation(String conversationId, String userId) {
            if (saved == null || !saved.belongsTo(conversationId, userId)) {
                return List.of();
            }
            return List.of(saved);
        }

        @Override
        public boolean delete(String attachmentId, String userId) {
            if (saved == null || !saved.attachmentId().equals(attachmentId) || !saved.userId().equals(userId)) {
                return false;
            }
            saved = null;
            return true;
        }
    }

    private static final class InMemoryObjectStoragePort implements ObjectStoragePort {

        private final byte[] content;

        private InMemoryObjectStoragePort(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public StoredObject upload(String bucketName,
                                   InputStream content,
                                   long size,
                                   String originalFilename,
                                   String contentType) {
            return new StoredObject("memory://uploaded", contentType, size, originalFilename);
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }

    private static final class RecordingMemoryEnginePort implements MemoryEnginePort {

        private MemoryLoadRequest lastLoadRequest;
        private MemoryWriteRequest lastWriteRequest;

        @Override
        public MemoryContext loadMemory(MemoryLoadRequest request) {
            lastLoadRequest = request;
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
