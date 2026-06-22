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
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ResolvedRoleCard;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelChatRoleCardTests {

    @Test
    void shouldResolveRequestedRoleCardIntoRagPromptContext() {
        RecordingRagPromptPort ragPromptPort = new RecordingRagPromptPort();
        RecordingRoleCardPort roleCardPort = new RecordingRoleCardPort();
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline(ragPromptPort),
                StreamTaskPort.noop(),
                Optional.empty(),
                KernelRagTraceRecorder.noop(),
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.of(roleCardPort));
        RecordingCallback callback = new RecordingCallback();

        service.streamChat(new StreamChatCommand(
                "hello",
                "conversation-1",
                "task-1",
                "user-1",
                false,
                ChatMode.RAG,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                99L), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals("user-1", roleCardPort.resolvedUserId);
        assertEquals(99L, roleCardPort.resolvedRoleCardId);
        assertNotNull(ragPromptPort.context);
        assertNotNull(ragPromptPort.context.getRoleCard());
        assertEquals("Coach", ragPromptPort.context.getRoleCard().name());
        assertEquals("Ask short questions.", ragPromptPort.context.getRoleCard().definition());
    }

    @Test
    void shouldInjectDefaultRoleCardAsUserMessageForAgentMode() {
        RecordingAgentLoop agentLoop = new RecordingAgentLoop();
        RecordingRoleCardPort roleCardPort = new RecordingRoleCardPort();
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline(new RecordingRagPromptPort()),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                KernelRagTraceRecorder.noop(),
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.of(roleCardPort));

        service.streamChat(new StreamChatCommand(
                "hello",
                "conversation-1",
                "task-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                99L), new RecordingCallback());

        assertNotNull(agentLoop.request);
        assertEquals(1, agentLoop.request.history().size());
        assertEquals(ChatRole.USER, agentLoop.request.history().get(0).getRole());
        assertTrue(agentLoop.request.history().get(0).getContent().contains("Coach"));
        assertTrue(agentLoop.request.history().get(0).getContent().contains("Ask short questions."));
    }

    @Test
    void shouldInjectHigherPermissionRoleCardIntoAgentRuntimeContext() {
        RecordingAgentLoop agentLoop = new RecordingAgentLoop();
        RecordingRoleCardPort roleCardPort = new RecordingRoleCardPort(
                new ResolvedRoleCard("99", "Operator", "Use terse operational language.", true));
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline(new RecordingRagPromptPort()),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                KernelRagTraceRecorder.noop(),
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ConversationAttachmentContextAssembler.noop(),
                Optional.empty(),
                KernelAgentLoopOptions.defaults(),
                Optional.empty(),
                true,
                null,
                Optional.of(roleCardPort));

        service.streamChat(new StreamChatCommand(
                "hello",
                "conversation-1",
                "task-1",
                "user-1",
                false,
                ChatMode.AGENT,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                99L), new RecordingCallback());

        assertNotNull(agentLoop.request);
        assertTrue(agentLoop.request.history().isEmpty());
        assertNotNull(agentLoop.request.skillRuntimeContext());
        assertTrue(agentLoop.request.skillRuntimeContext().contains("Operator"));
        assertTrue(agentLoop.request.skillRuntimeContext().contains("Use terse operational language."));
    }

    private static KernelChatPipeline pipeline(RagPromptPort ragPromptPort) {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                QueryOptimizerPort.passthrough(),
                QueryRewritePort.passthrough(),
                IntentResolutionPort.empty(),
                IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder()
                        .kbContext("kb context")
                        .intentChunks(Map.of())
                        .build());
        ChatResponsePorts responsePorts = new ChatResponsePorts(
                ragPromptPort,
                PromptTemplatePort.empty(),
                new CompletingStreamingModel(),
                StreamTaskPort.noop());
        return new KernelChatPipeline(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
    }

    private static final class RecordingAgentLoop implements ReActExecutorPort {
        private AgentLoopRequest request;

        @Override
        public AgentLoopResult execute(AgentLoopRequest request) {
            this.request = request;
            return null;
        }

        @Override
        public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
            this.request = request;
            callback.onComplete();
            return () -> {
            };
        }
    }

    private static final class RecordingRagPromptPort implements RagPromptPort {
        private PromptContext context;

        @Override
        public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                         List<ChatMessage> history,
                                                         String question,
                                                         List<String> subQuestions) {
            this.context = context;
            return List.of(ChatMessage.system("system"), ChatMessage.user(question));
        }
    }

    private static final class CompletingStreamingModel implements StreamingChatModelPort {
        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            callback.onContent("ok");
            callback.onComplete();
            return () -> {
            };
        }
    }

    private static final class RecordingRoleCardPort implements RoleCardInboundPort {
        private String resolvedUserId;
        private Long resolvedRoleCardId;
        private final ResolvedRoleCard roleCard;

        private RecordingRoleCardPort() {
            this(new ResolvedRoleCard("99", "Coach", "Ask short questions.", false));
        }

        private RecordingRoleCardPort(ResolvedRoleCard roleCard) {
            this.roleCard = roleCard;
        }

        @Override
        public List<RoleCardRecord> list(String userId) {
            return List.of();
        }

        @Override
        public Long save(RoleCardCommand command) {
            return null;
        }

        @Override
        public void activate(String userId, Long roleCardId) {
        }

        @Override
        public void delete(String userId, Long roleCardId) {
        }

        @Override
        public Optional<ResolvedRoleCard> resolve(String userId, Long requestedRoleCardId) {
            this.resolvedUserId = userId;
            this.resolvedRoleCardId = requestedRoleCardId;
            return Optional.of(roleCard);
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            terminal.countDown();
        }

        private boolean awaitTerminal() {
            try {
                return terminal.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
