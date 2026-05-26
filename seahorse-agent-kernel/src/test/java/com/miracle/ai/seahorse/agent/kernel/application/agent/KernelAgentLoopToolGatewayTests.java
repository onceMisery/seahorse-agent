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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopExitReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamApprovalRequiredEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentLoopToolGatewayTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldInvokeToolsThroughGatewayWithRunContext() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need weather", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway();
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("weather?")
                .allowedToolIds(List.of("weather"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .memoryContext(MemoryContext.builder()
                        .conversationId("conversation-1")
                        .userId("user-1")
                        .currentQuestion("weather?")
                        .build())
                .runId("run-1")
                .build());

        assertEquals("done", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("run-1", request.runId());
        assertEquals("call-1", request.toolCallId());
        assertEquals("call-1", request.stepId());
        assertEquals("weather", request.toolId());
        assertEquals("Shanghai", request.arguments().get("city"));
        assertEquals("user-1", request.userId());
        assertEquals("conversation-1", request.arguments().get("_seahorseConversationId"));
        assertEquals(List.of("weather"), request.allowedToolIds());
    }

    @Test
    void shouldSendHallucinatedDisallowedToolCallsThroughGatewayPolicyBoundary() {
        AgentToolCall toolCall = AgentToolCall.of("call-2", "delete-memory", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("try delete", List.of(toolCall)),
                Turn.finalAnswer("blocked")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.failed("TOOL_NOT_BOUND"));
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("delete memory")
                .allowedToolIds(List.of("weather"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-2")
                .build());

        assertEquals("blocked", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("run-2", request.runId());
        assertEquals("delete-memory", request.toolId());
        assertEquals(List.of("weather"), request.allowedToolIds());
        assertEquals("TOOL_NOT_BOUND", result.steps().get(0).observations().get(0).error());
    }

    @Test
    void shouldNotExposeAnyToolsWhenAllowlistIsEmpty() {
        ToolListRecordingModel model = new ToolListRecordingModel();
        RecordingToolGateway gateway = new RecordingToolGateway();
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("answer directly")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-empty-tools")
                .build());

        assertEquals("direct answer", result.finalAnswer());
        assertEquals(List.of(), model.tools);
        assertEquals(0, gateway.requests.size());
    }

    @Test
    void shouldPauseRunAndCheckpointPendingToolWhenGatewayRequiresApproval() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "memory-forget", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need approval", List.of(toolCall)),
                Turn.finalAnswer("should not be reached")));
        RecordingToolGateway gateway = new RecordingToolGateway(
                ToolInvocationResult.failed(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(new AgentRun(
                "run-approval",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "forget memory",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults(),
                RepositoryAgentApprovalWaitHandler.fromRepositories(
                        runRepository,
                        checkpointRepository,
                        FIXED_CLOCK));

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("forget memory")
                .allowedToolIds(List.of("memory-forget"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-approval")
                .agentId("agent-1")
                .versionId("version-1")
                .tenantId("tenant-1")
                .userId("user-1")
                .build());

        assertEquals(AgentLoopExitReason.WAITING_APPROVAL, result.exitReason());
        assertFalse(result.truncated());
        assertEquals(1, model.index);
        assertEquals(AgentRunStatus.WAITING_APPROVAL, runRepository.runs.get("run-approval").status());
        AgentCheckpoint checkpoint = checkpointRepository.findLatestByRunId("run-approval").orElseThrow();
        assertEquals(AgentCheckpointType.WAITING_APPROVAL, checkpoint.checkpointType());
        assertEquals("call-1", checkpoint.stepId());
        assertTrue(checkpoint.pendingToolCallJson().contains("\"toolId\":\"memory-forget\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"toolCallId\":\"call-1\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"idempotencyKey\":\"run-approval:call-1\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"agentId\":\"agent-1\""));
        assertTrue(checkpoint.pendingToolCallJson().contains("\"tenantId\":\"tenant-1\""));
    }

    @Test
    void shouldEmitStepToolAndApprovalEventsDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "memory-forget", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(Turn.toolCalls("need approval", List.of(toolCall))));
        RecordingToolGateway gateway = new RecordingToolGateway(
                ToolInvocationResult.failed(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, "approval-1"));
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("forget memory")
                .allowedToolIds(List.of("memory-forget"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-approval")
                .agentId("agent-1")
                .versionId("version-1")
                .tenantId("tenant-1")
                .userId("user-1")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        assertEquals(List.of(), callback.errors);
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.STEP_STARTED.value().equals(event.eventName())));
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.TOOL_CALL_STARTED.value().equals(event.eventName())));
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.TOOL_CALL_WAITING_USER.value().equals(event.eventName())));
        StreamApprovalRequiredEvent approvalEvent = callback.events.stream()
                .filter(event -> StreamEventType.TOOL_CALL_WAITING_USER.value().equals(event.eventName()))
                .map(StreamEvent::payload)
                .filter(StreamApprovalRequiredEvent.class::isInstance)
                .map(StreamApprovalRequiredEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("approval-1", approvalEvent.approvalId());
        assertEquals("memory-forget", approvalEvent.toolId());
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.STEP_FINISHED.value().equals(event.eventName())));
    }

    @Test
    void shouldEmitSourcesFromWebSearchObservationDuringStreamingExecution() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "web_search", Map.of("query", "seahorse ai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need search", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.ok("""
                {"sources":[{"title":"Seahorse","url":"https://example.com","snippet":"AI source","score":0.9}]}
                """));
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("research")
                .allowedToolIds(List.of("web_search"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-web-search")
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.SOURCE_FOUND.value().equals(event.eventName())
                        && String.valueOf(event.payload()).contains("https://example.com")));
    }

    @Test
    void shouldEmitMarkdownArtifactWhenExpectedOutputIsMarkdown() {
        ScriptedModel model = new ScriptedModel(List.of(Turn.finalAnswer("# Report\n\nFinal research result.")));
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                new RecordingToolGateway(),
                KernelAgentLoopOptions.defaults());
        RecordingStreamCallback callback = new RecordingStreamCallback();

        loop.streamExecute(AgentLoopRequest.builder()
                .question("write report")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-report")
                .expectedOutputArtifactType(OutputArtifactType.MARKDOWN)
                .build(), callback);

        assertTrue(callback.awaitTerminal());
        assertTrue(callback.events.stream()
                .anyMatch(event -> StreamEventType.ARTIFACT_CREATED.value().equals(event.eventName())
                        && String.valueOf(event.payload()).contains("Final research result.")));
    }

    private static final class RecordingToolGateway implements ToolGatewayPort {
        private final List<ToolInvocationRequest> requests = new ArrayList<>();
        private final ToolInvocationResult result;

        private RecordingToolGateway() {
            this(ToolInvocationResult.ok("{\"temp\":21}"));
        }

        private RecordingToolGateway(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static final class ListingOnlyToolRegistry implements ToolRegistryPort {
        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(
                    new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                    new ToolDescriptor("memory-forget", "Memory Forget", "Forget memory", "{}"),
                    new ToolDescriptor("web_search", "Web Search", "Search public Web sources", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            throw new AssertionError("KernelAgentLoop must invoke tools through ToolGatewayPort");
        }
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
            if (!turn.toolCalls().isEmpty()) {
                assertFalse(request.getTools().isEmpty());
            }
            callback.onContent(turn.content);
            toolCallCollector.onToolCalls(turn.toolCalls);
            callback.onComplete();
            return () -> {
            };
        }
    }

    private static final class ToolListRecordingModel implements StreamingChatModelPort {
        private List<ToolDescriptor> tools = List.of();

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            tools = List.copyOf(request.getTools());
            callback.onContent("direct answer");
            toolCallCollector.onToolCalls(List.of());
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

    private static final class RecordingStreamCallback implements StreamCallback {
        private final java.util.concurrent.CountDownLatch terminal = new java.util.concurrent.CountDownLatch(1);
        private final List<StreamEvent> events = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onEvent(String eventName, Object payload) {
            events.add(new StreamEvent(eventName, payload));
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
            terminal.countDown();
        }

        private boolean awaitTerminal() {
            try {
                return terminal.await(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private record StreamEvent(String eventName, Object payload) {
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

    private static final class MemoryAgentCheckpointRepository implements AgentCheckpointRepositoryPort {
        private final List<AgentCheckpoint> checkpoints = new ArrayList<>();

        @Override
        public void save(AgentCheckpoint checkpoint) {
            checkpoints.add(checkpoint);
        }

        @Override
        public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .max(java.util.Comparator.comparingLong(AgentCheckpoint::sequenceNo));
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .sorted(java.util.Comparator.comparingLong(AgentCheckpoint::sequenceNo))
                    .toList();
        }
    }
}
