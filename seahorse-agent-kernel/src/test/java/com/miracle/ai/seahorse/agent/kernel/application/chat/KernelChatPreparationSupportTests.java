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
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
}
