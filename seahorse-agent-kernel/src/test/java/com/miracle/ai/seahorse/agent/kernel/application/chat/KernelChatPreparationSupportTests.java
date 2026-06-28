package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelChatPreparationSupportTests {

    @Test
    void shouldLoadBranchPathWithoutAppendingUserMessageWhenRegeneratingAssistant() {
        RecordingMemoryPort memoryPort = new RecordingMemoryPort();
        KernelChatPreparationSupport support = new KernelChatPreparationSupport(new ChatPreparationPorts(
                memoryPort,
                MemoryEnginePort.noop(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build()));
        StreamChatContext context = StreamChatContext.builder()
                .question("hello")
                .conversationId("conversation-1")
                .userId("user-1")
                .branchLeafMessageId(123L)
                .assistantParentMessageId(123L)
                .build();

        support.loadMemory(context);

        assertTrue(memoryPort.loadBranchPathCalled);
        assertFalse(memoryPort.loadAndAppendCalled);
        assertEquals(123L, memoryPort.branchLeafMessageId);
        assertEquals(List.of("hello"), context.getHistory().stream().map(ChatMessage::getContent).toList());
    }

    @Test
    void shouldEmitInteractiveMemoryConflictPromptForPendingHighSeverityConflict() {
        RecordingMemoryPort memoryPort = new RecordingMemoryPort();
        RecordingConflictLogRepository conflicts = new RecordingConflictLogRepository(List.of(
                new MemoryConflictRecord(
                        "mem-conflict-1",
                        "user-1",
                        "memory-a",
                        "memory-b",
                        "CONTRADICTION",
                        "HIGH",
                        "PENDING",
                        "",
                        "",
                        Instant.EPOCH,
                        Instant.parse("2026-06-27T00:00:00Z"))));
        KernelChatPreparationSupport support = new KernelChatPreparationSupport(new ChatPreparationPorts(
                memoryPort,
                MemoryEnginePort.noop(),
                command -> MemoryIngestionResult.ignored("noop"),
                MemoryAggregationServicePort.noop(),
                com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy.defaults(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build(),
                conflicts));
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = StreamChatContext.builder()
                .question("hello")
                .conversationId("conversation-1")
                .userId("user-1")
                .callback(callback)
                .build();

        support.emitInteractiveMemoryConflicts(context);

        assertEquals("user-1", conflicts.lastUserId);
        assertEquals("PENDING", conflicts.lastStatus);
        assertEquals(1, callback.events.size());
        RecordedEvent event = callback.events.get(0);
        assertEquals("memory.conflict.prompt", event.name());
        Map<?, ?> payload = (Map<?, ?>) event.payload();
        assertEquals("mem-conflict-1", payload.get("conflictId"));
        assertEquals("memory-a", payload.get("memoryId1"));
        assertEquals("memory-b", payload.get("memoryId2"));
        assertEquals("HIGH", payload.get("severity"));
    }

    @Test
    void shouldForwardInteractiveMemoryConflictPromptThroughAggregationCapture() {
        assertConflictPromptForwardedThroughCapture(
                com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy
                        .defaults()
                        .withEnabled(true));
    }

    @Test
    void shouldForwardInteractiveMemoryConflictPromptThroughDirectCapture() {
        assertConflictPromptForwardedThroughCapture(
                com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy
                        .defaults());
    }

    @Test
    void shouldLoadAgentBranchPathWithoutAppendingUserMessageWhenRegeneratingAssistant() throws Exception {
        RecordingMemoryPort memoryPort = new RecordingMemoryPort();
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline(memoryPort),
                StreamTaskPort.noop(),
                Optional.empty(),
                KernelRagTraceRecorder.noop(),
                memoryPort,
                MemoryEnginePort.noop());

        List<ChatMessage> history = loadAgentHistory(service, new StreamChatCommand(
                "hello", "conversation-1", "task-1", "user-1", false,
                ChatMode.AGENT, null, null, null, List.of(), List.of(), List.of(),
                null, 123L, 123L, null));

        assertTrue(memoryPort.loadBranchPathCalled);
        assertFalse(memoryPort.loadAndAppendCalled);
        assertEquals(123L, memoryPort.branchLeafMessageId);
        assertEquals(List.of("hello"), history.stream().map(ChatMessage::getContent).toList());
    }

    private void assertConflictPromptForwardedThroughCapture(
            com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy policy) {
        RecordingConflictLogRepository conflicts = new RecordingConflictLogRepository(List.of(
                new MemoryConflictRecord(
                        "mem-conflict-1",
                        "user-1",
                        "memory-a",
                        "memory-b",
                        "CONTRADICTION",
                        "HIGH",
                        "PENDING",
                        "",
                        "",
                        Instant.EPOCH,
                        Instant.parse("2026-06-27T00:00:00Z"))));
        KernelChatPreparationSupport support = new KernelChatPreparationSupport(new ChatPreparationPorts(
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                command -> MemoryIngestionResult.ignored("noop"),
                MemoryAggregationServicePort.noop(),
                policy,
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build(),
                conflicts));
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = StreamChatContext.builder()
                .question("hello")
                .conversationId("conversation-1")
                .userId("user-1")
                .callback(callback)
                .build();

        support.installMemoryCapture(context);
        support.emitInteractiveMemoryConflicts(context);

        assertEquals(1, callback.events.size());
        assertEquals("memory.conflict.prompt", callback.events.get(0).name());
    }

    @SuppressWarnings("unchecked")
    private static List<ChatMessage> loadAgentHistory(
            KernelChatInboundService service,
            StreamChatCommand command) throws Exception {
        Method method = KernelChatInboundService.class.getDeclaredMethod("loadAgentHistory", StreamChatCommand.class);
        method.setAccessible(true);
        return (List<ChatMessage>) method.invoke(service, command);
    }

    private static KernelChatPipeline pipeline(ConversationMemoryPort memoryPort) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                memoryPort,
                MemoryEnginePort.noop(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build());
        ChatResponsePorts responsePorts = new ChatResponsePorts(
                RagPromptPort.simple(),
                PromptTemplatePort.empty(),
                new NoopStreamingModel(),
                StreamTaskPort.noop());
        return new KernelChatPipeline(preparationPorts, responsePorts);
    }

    private static final class NoopStreamingModel implements StreamingChatModelPort {

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            return () -> {
            };
        }
    }

    private static final class RecordingMemoryPort implements ConversationMemoryPort {

        private boolean loadAndAppendCalled;
        private boolean loadBranchPathCalled;
        private Long branchLeafMessageId;

        @Override
        public List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
            loadAndAppendCalled = true;
            return List.of();
        }

        @Override
        public List<ChatMessage> loadAndAppend(
                String conversationId,
                String userId,
                ChatMessage message,
                Long branchLeafMessageId) {
            loadAndAppendCalled = true;
            this.branchLeafMessageId = branchLeafMessageId;
            return List.of();
        }

        @Override
        public List<ChatMessage> loadBranchPath(String conversationId, String userId, Long branchLeafMessageId) {
            loadBranchPathCalled = true;
            this.branchLeafMessageId = branchLeafMessageId;
            return List.of(ChatMessage.user("hello"));
        }
    }

    private static final class RecordingCallback implements StreamCallback {

        private final List<RecordedEvent> events = new ArrayList<>();

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onEvent(String eventName, Object payload) {
            events.add(new RecordedEvent(eventName, payload));
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    private record RecordedEvent(String name, Object payload) {
    }

    private static final class RecordingConflictLogRepository implements MemoryConflictLogRepositoryPort {

        private final List<MemoryConflictRecord> records;
        private String lastUserId;
        private String lastStatus;

        private RecordingConflictLogRepository(List<MemoryConflictRecord> records) {
            this.records = records;
        }

        @Override
        public List<MemoryConflictRecord> listByUser(String userId, String status, int limit) {
            lastUserId = userId;
            lastStatus = status;
            return records.stream().limit(limit).toList();
        }

        @Override
        public void save(MemoryConflictRecord record) {
        }

        @Override
        public boolean resolve(String conflictId, String action, String resolvedBy) {
            return false;
        }
    }
}
