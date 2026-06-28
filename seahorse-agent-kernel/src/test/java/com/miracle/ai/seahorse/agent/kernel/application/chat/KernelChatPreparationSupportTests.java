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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
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
        RecordingMemoryStore memoryStore = new RecordingMemoryStore()
                .put(memory("memory-a", "Memory A says the workspace preference is quiet mode"))
                .put(memory("memory-b", "Memory B says the workspace preference is collaboration mode"));
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
                conflicts,
                List.of(memoryStore),
                InteractiveMemoryConflictPromptPolicy.defaults()));
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
        assertEquals("Memory A says the workspace preference is quiet mode", payload.get("contentA"));
        assertEquals("Memory B says the workspace preference is collaboration mode", payload.get("contentB"));
        assertEquals("HIGH", payload.get("severity"));
    }

    @Test
    void shouldSkipInteractiveMemoryConflictPromptsWhenPolicyIsDisabled() {
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
                com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy.defaults(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build(),
                conflicts,
                List.of(),
                new InteractiveMemoryConflictPromptPolicy(false, 20, 3, Duration.ofMinutes(5), 2, Clock.systemUTC())));
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = StreamChatContext.builder()
                .question("hello")
                .conversationId("conversation-1")
                .userId("user-1")
                .callback(callback)
                .build();

        support.emitInteractiveMemoryConflicts(context);

        assertEquals(0, callback.events.size());
        assertEquals(null, conflicts.lastUserId);
    }

    @Test
    void shouldLimitInteractiveMemoryConflictPromptsPerTurn() {
        RecordingConflictLogRepository conflicts = new RecordingConflictLogRepository(List.of(
                conflict("mem-conflict-1", "memory-a", "memory-b", "HIGH", "2026-06-27T00:00:00Z"),
                conflict("mem-conflict-2", "memory-c", "memory-d", "HIGH", "2026-06-27T00:01:00Z")));
        InteractiveMemoryConflictPromptPolicy policy = new InteractiveMemoryConflictPromptPolicy(
                true, 20, 1, Duration.ZERO, 3, Clock.systemUTC());
        KernelChatPreparationSupport support = new KernelChatPreparationSupport(new ChatPreparationPorts(
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                command -> MemoryIngestionResult.ignored("noop"),
                MemoryAggregationServicePort.noop(),
                com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy.defaults(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build(),
                conflicts,
                List.of(),
                policy));
        RecordingCallback callback = new RecordingCallback();
        StreamChatContext context = StreamChatContext.builder()
                .question("hello")
                .conversationId("conversation-1")
                .userId("user-1")
                .callback(callback)
                .build();

        support.emitInteractiveMemoryConflicts(context);

        assertEquals(1, callback.events.size());
        assertEquals("mem-conflict-2", ((Map<?, ?>) callback.events.get(0).payload()).get("conflictId"));
    }

    @Test
    void shouldApplyPromptCooldownAndRepeatCapPerConflict() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-27T00:00:00Z"));
        RecordingConflictLogRepository conflicts = new RecordingConflictLogRepository(List.of(
                conflict("mem-conflict-1", "memory-a", "memory-b", "HIGH", "2026-06-27T00:00:00Z")));
        InteractiveMemoryConflictPromptPolicy policy = new InteractiveMemoryConflictPromptPolicy(
                true, 20, 1, Duration.ofMinutes(10), 2, clock);
        KernelChatPreparationSupport support = new KernelChatPreparationSupport(new ChatPreparationPorts(
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                command -> MemoryIngestionResult.ignored("noop"),
                MemoryAggregationServicePort.noop(),
                com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy.defaults(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build(),
                conflicts,
                List.of(),
                policy));
        StreamChatContext context = StreamChatContext.builder()
                .question("hello")
                .conversationId("conversation-1")
                .userId("user-1")
                .callback(new RecordingCallback())
                .build();

        support.emitInteractiveMemoryConflicts(context);
        RecordingCallback callback = (RecordingCallback) context.getCallback();
        assertEquals(1, callback.events.size());
        assertEquals("mem-conflict-1", ((Map<?, ?>) callback.events.get(0).payload()).get("conflictId"));

        callback.events.clear();
        support.emitInteractiveMemoryConflicts(context);
        assertEquals(0, callback.events.size());

        clock.advance(Duration.ofMinutes(11));
        support.emitInteractiveMemoryConflicts(context);
        assertEquals(1, callback.events.size());

        callback.events.clear();
        clock.advance(Duration.ofMinutes(11));
        support.emitInteractiveMemoryConflicts(context);
        assertEquals(0, callback.events.size());
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

    private static MemoryConflictRecord conflict(String id,
                                                 String memoryId1,
                                                 String memoryId2,
                                                 String severity,
                                                 String createTime) {
        return new MemoryConflictRecord(
                id,
                "user-1",
                memoryId1,
                memoryId2,
                "CONTRADICTION",
                severity,
                "PENDING",
                "",
                "",
                Instant.EPOCH,
                Instant.parse(createTime));
    }

    private static MemoryRecord memory(String id, String content) {
        return new MemoryRecord(
                id,
                "short_term",
                "PROFILE",
                content,
                Map.of("userId", "user-1", "tenantId", "default"),
                Instant.EPOCH);
    }

    private static final class RecordingMemoryStore implements MemoryStorePort {

        private final Map<String, MemoryRecord> records = new HashMap<>();

        private RecordingMemoryStore put(MemoryRecord record) {
            records.put(record.id(), record);
            return this;
        }

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return List.of();
        }

        @Override
        public void save(MemoryRecord record) {
            records.put(record.id(), record);
        }

        @Override
        public boolean deleteById(String id) {
            return records.remove(id) != null;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class RecordingConflictLogRepository implements MemoryConflictLogRepositoryPort {

        private final List<MemoryConflictRecord> records;
        private String lastUserId;
        private String lastStatus;
        private int lastLimit;

        private RecordingConflictLogRepository(List<MemoryConflictRecord> records) {
            this.records = records;
        }

        @Override
        public List<MemoryConflictRecord> listByUser(String userId, String status, int limit) {
            lastUserId = userId;
            lastStatus = status;
            lastLimit = limit;
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
