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

import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelChatAgentRunStoreTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateAgentRunAndRecordModelAndToolStepsForAgentMode() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService runService = new KernelAgentRunService(
                new EmptyAgentDefinitionRepository(), runRepository,
                () -> Optional.of(new CurrentUser("user-1", "alice", "user", null)), FIXED_CLOCK);
        AgentToolCall toolCall = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need weather", List.of(toolCall)),
                Turn.finalAnswer("Shanghai 21C")));
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("{\"temp\":21}"));
        KernelAgentLoop agentLoop = new KernelAgentLoop(
                model,
                toolRegistry,
                KernelAgentLoopOptions.defaults(),
                new RepositoryAgentRunStepRecorder(runRepository, FIXED_CLOCK));
        RecordingCallback callback = new RecordingCallback();
        KernelChatInboundService service = new KernelChatInboundService(
                newPipeline(),
                StreamTaskPort.noop(),
                Optional.of(agentLoop),
                null,
                null,
                MemoryEnginePort.noop(),
                Optional.of(runService));

        service.streamChat(new StreamChatCommand(
                "What is the weather?", "conversation-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of("Shanghai 21C"), callback.contents);
        AgentRun run = runRepository.runs.values().iterator().next();
        assertEquals("legacy-react-agent", run.agentId());
        assertEquals(run.runId(), callback.runId);
        assertEquals(AgentRunStatus.SUCCEEDED, run.status());
        assertEquals("conversation-1", run.conversationId());
        List<AgentStep> steps = runRepository.listSteps(run.runId());
        assertEquals(3, steps.size());
        assertEquals(AgentStepType.MODEL_TURN, steps.get(0).stepType());
        assertEquals(AgentStepType.TOOL_CALL, steps.get(1).stepType());
        assertEquals(AgentStepType.MODEL_TURN, steps.get(2).stepType());
        assertEquals(AgentStepStatus.SUCCEEDED, steps.get(1).status());
        assertTrue(steps.get(1).inputJson().contains("weather"));
        assertTrue(steps.get(1).outputJson().contains("temp"));
    }

    private static KernelChatPipeline newPipeline() {
        ChatPreparationPorts preparationPorts = new ChatPreparationPorts(
                com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort.noop(),
                MemoryEnginePort.noop(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort.passthrough(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort.passthrough(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort.empty(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort.none(),
                (subIntents, topK) -> RetrievalContext.builder().intentChunks(Map.of()).build());
        ChatResponsePorts responsePorts = new ChatResponsePorts(
                com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort.simple(),
                com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort.empty(),
                StreamingChatModelPort.noop(),
                StreamTaskPort.noop());
        return new KernelChatPipeline(preparationPorts, responsePorts);
    }

    private static final class ScriptedModel implements StreamingChatModelPort {
        private final List<Turn> turns;
        private int index;

        private ScriptedModel(List<Turn> turns) {
            this.turns = turns;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            Turn turn = turns.get(index++);
            callback.onContent(turn.content);
            toolCallCollector.onToolCalls(turn.toolCalls);
            callback.onComplete();
            return () -> {
            };
        }
    }

    private record Turn(String content, List<AgentToolCall> toolCalls) {

        private static Turn finalAnswer(String content) {
            return new Turn(content, List.of());
        }

        private static Turn toolCalls(String content, List<AgentToolCall> toolCalls) {
            return new Turn(content, toolCalls);
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final List<String> contents = new ArrayList<>();
        private String runId;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onThinking(String content) {
        }

        @Override
        public void onRunStarted(String runId) {
            this.runId = runId;
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

    private static final class EmptyAgentDefinitionRepository implements AgentDefinitionRepositoryPort {

        @Override
        public void create(AgentDefinition definition) {
        }

        @Override
        public void update(AgentDefinition definition) {
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.empty();
        }

        @Override
        public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
            return new AgentDefinitionPage(List.of(), 0, size, current, 0);
        }

        @Override
        public long nextVersionNo(String agentId) {
            return 1;
        }

        @Override
        public void saveVersion(AgentVersion version) {
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return Optional.empty();
        }
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private final List<AgentStep> steps = new ArrayList<>();

        @Override
        public void createRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public void updateRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendStep(AgentStep step) {
            steps.add(step);
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return steps.stream()
                    .filter(step -> runId.equals(step.runId()))
                    .toList();
        }
    }
}
