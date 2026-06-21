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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopExitReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentScopeReActExecutorTests {

    @Test
    void executesThroughAgentscopeClientAndKeepsSeahorseResultContract() {
        CapturingClient client = new CapturingClient();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .history(List.of(ChatMessage.system("be concise")))
                .samplingOptions(ChatSamplingOptions.builder().build())
                .tenantId("tenant-a")
                .build();

        AgentLoopResult result = executor.execute(request);

        assertEquals("agentscope answer", result.finalAnswer());
        assertEquals("agentscope", executor.engineId());
        assertEquals(2, client.messages.size());
        assertEquals(MsgRole.SYSTEM, client.messages.get(0).getRole());
        assertEquals("plan", client.messages.get(1).getTextContent());
    }

    @Test
    void streamExecuteForwardsAgentscopeStreamEventsBeforeCompleting() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(
                event("first ", false),
                event("second", true));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of("first ", "second"), callback.contents);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    @Test
    void streamExecuteDoesNotAppendFinalResultContentAfterTextDeltas() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(
                new TextBlockDeltaEvent("reply-1", "block-1", "收到"),
                new AgentResultEvent(Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .textContent("收到")
                        .build()));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of("收到"), callback.contents);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    @Test
    void streamExecuteStillEmitsFinalResultContentAfterBlankTextDelta() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(
                new TextBlockDeltaEvent("reply-1", "block-1", ""),
                new AgentResultEvent(Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .textContent("final answer")
                        .build()));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of("final answer"), callback.contents);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    @Test
    void streamExecuteRecordsAgentscopeExecuteObservationWithRunDimensions() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(event("answer", true));
        RecordingObservationPort observationPort = new RecordingObservationPort();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(
                client,
                Runnable::run,
                ApprovalRequestQueryPort.empty(),
                new AgentScopeObservationSupport(observationPort));
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .tenantId("tenant-a")
                .runId("run-1")
                .agentId("agent-1")
                .build();

        executor.streamExecute(request, callback);

        assertEquals(1, observationPort.commands.size());
        assertEquals("agentscope.execute", observationPort.commands.get(0).name());
        assertEquals("tenant-a", observationPort.commands.get(0).tenantId());
        assertEquals("agentscope", observationPort.commands.get(0).attributes().get("engine"));
        assertEquals("run-1", observationPort.commands.get(0).attributes().get("runId"));
        assertEquals("agent-1", observationPort.commands.get(0).attributes().get("agentName"));
        assertEquals(1, observationPort.closed);
    }

    @Test
    void streamExecuteRecordsAgentscopeSpanInProvidedTraceRun() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(event("answer", true));
        RecordingTraceRepository traceRepository = new RecordingTraceRepository();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(
                client,
                Runnable::run,
                ApprovalRequestQueryPort.empty(),
                AgentScopeObservationSupport.noop(),
                new KernelRagTraceRecorder(traceRepository));
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .agentId("agent-1")
                .tenantId("tenant-a")
                .build();

        executor.streamExecute(request, callback, TraceRunScope.active("trace-1", Instant.now()));

        assertEquals(1, traceRepository.startedNodes.size());
        assertEquals("trace-1", traceRepository.startedNodes.get(0).getTraceId());
        assertEquals("agentscope-step", traceRepository.startedNodes.get(0).getNodeName());
        assertEquals("AGENT_STEP", traceRepository.startedNodes.get(0).getNodeType());
        assertEquals(1, traceRepository.finishedNodes.size());
        assertEquals(KernelRagTraceRecorder.STATUS_SUCCESS, traceRepository.finishedNodes.get(0).status());
    }

    @Test
    void streamExecuteFinishesAgentscopeSpanWhenCancelled() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.never();
        RecordingTraceRepository traceRepository = new RecordingTraceRepository();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(
                client,
                Runnable::run,
                ApprovalRequestQueryPort.empty(),
                AgentScopeObservationSupport.noop(),
                new KernelRagTraceRecorder(traceRepository));
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .agentId("agent-1")
                .tenantId("tenant-a")
                .build();

        StreamCancellationHandle handle = executor.streamExecute(
                request,
                callback,
                TraceRunScope.active("trace-1", Instant.now()));
        handle.cancel();

        assertEquals(1, traceRepository.startedNodes.size());
        assertEquals(1, traceRepository.finishedNodes.size());
        assertEquals(KernelRagTraceRecorder.STATUS_FAILED, traceRepository.finishedNodes.get(0).status());
    }

    @Test
    void streamExecuteMapsAgentscopeRuntimeEventsToStepProgress() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(new GenericAgentEvent(AgentEventType.MODEL_CALL_START));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of(StreamEventType.STEP_PROGRESS.value()), callback.eventNames);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    @Test
    void streamExecuteClassifiesAgentscopeRuntimeErrorsAsRecoverableEvents() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.error(new IllegalStateException("model gateway unavailable"));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of(StreamEventType.RECOVERABLE_ERROR.value()), callback.eventNames);
        assertEquals("IllegalStateException", ((Map<?, ?>) callback.eventPayloads.get(0)).get("errorType"));
        assertEquals("model gateway unavailable", ((Map<?, ?>) callback.eventPayloads.get(0)).get("message"));
        assertEquals(1, callback.errors);
    }

    @Test
    void streamExecuteForwardsAgentscopeModelUsage() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(new ModelCallEndEvent("reply-1", ChatUsage.builder()
                .inputTokens(13)
                .outputTokens(8)
                .build()));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of(new ChatTokenUsage(13, 8)), callback.usages);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    @Test
    void executeMapsApprovalRequiredInterruptionToWaitingApprovalResult() {
        CapturingClient client = new CapturingClient();
        client.callError = new AgentScopeToolApprovalRequiredException(
                "call-1", "weather", "approval-1", "TOOL_APPROVAL_REQUIRED");
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        AgentLoopResult result = executor.execute(request);

        assertEquals(AgentLoopExitReason.WAITING_APPROVAL, result.exitReason());
        assertEquals("Waiting for tool approval.", result.finalAnswer());
    }

    @Test
    void executeMapsAgentscopePermissionAskingResultToWaitingApprovalResult() {
        CapturingClient client = new CapturingClient();
        client.response = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("")
                .generateReason(GenerateReason.PERMISSION_ASKING)
                .build();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(
                client,
                Runnable::run,
                approvalQuery("approval-1", "run-1", "agentscope-step", "call-1", "weather"));
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .build();

        AgentLoopResult result = executor.execute(request);

        assertEquals(AgentLoopExitReason.WAITING_APPROVAL, result.exitReason());
        assertEquals("Waiting for tool approval.", result.finalAnswer());
    }

    @Test
    void streamExecuteMapsApprovalRequiredInterruptionToWaitingUserEvent() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.error(new AgentScopeToolApprovalRequiredException(
                "call-1", "weather", "approval-1", "TOOL_APPROVAL_REQUIRED"));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of(StreamEventType.TOOL_CALL_WAITING_USER.value()), callback.eventNames);
        assertEquals(List.of("Waiting for tool approval."), callback.contents);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    @Test
    void streamExecuteMapsAgentscopeRequireUserConfirmEventToWaitingUserEvent() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(
                new RequireUserConfirmEvent("reply-1", List.of(ToolUseBlock.builder()
                        .id("call-1")
                        .name("weather")
                        .input(Map.of())
                        .build())),
                new RequestStopEvent("permission asking", GenerateReason.PERMISSION_ASKING));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(
                client,
                Runnable::run,
                approvalQuery("approval-1", "run-1", "agentscope-step", "call-1", "weather"));
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of(StreamEventType.TOOL_CALL_WAITING_USER.value()), callback.eventNames);
        assertEquals(List.of("Waiting for tool approval."), callback.contents);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    private static AgentEvent event(String text, boolean ignoredLast) {
        return new AgentResultEvent(Msg.builder().role(MsgRole.ASSISTANT).textContent(text).build());
    }

    private static final class GenericAgentEvent extends AgentEvent {
        private final AgentEventType type;

        private GenericAgentEvent(AgentEventType type) {
            this.type = type;
        }

        @Override
        public AgentEventType getType() {
            return type;
        }
    }

    private static final class CapturingClient implements AgentScopeAgentClient {
        private List<Msg> messages = List.of();
        private Flux<AgentEvent> events;
        private RuntimeException callError;
        private Msg response = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("agentscope answer")
                .build();

        @Override
        public Msg call(AgentLoopRequest request, List<Msg> messages) {
            this.messages = List.copyOf(messages);
            if (callError != null) {
                throw callError;
            }
            return response;
        }

        @Override
        public Flux<AgentEvent> stream(AgentLoopRequest request, List<Msg> messages) {
            this.messages = List.copyOf(messages);
            return events;
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final List<String> contents = new ArrayList<>();
        private final List<String> eventNames = new ArrayList<>();
        private final List<Object> eventPayloads = new ArrayList<>();
        private final List<ChatTokenUsage> usages = new ArrayList<>();
        private int completed;
        private int errors;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onComplete() {
            completed++;
        }

        @Override
        public void onEvent(String eventName, Object payload) {
            eventNames.add(eventName);
            eventPayloads.add(payload);
        }

        @Override
        public void onUsage(ChatTokenUsage usage) {
            usages.add(usage);
        }

        @Override
        public void onError(Throwable error) {
            errors++;
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {
        private final List<ObservationCommand> commands = new ArrayList<>();
        private int closed;

        @Override
        public ObservationScope start(ObservationCommand command) {
            commands.add(command);
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                }

                @Override
                public void close() {
                    closed++;
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
        }
    }

    private static final class RecordingTraceRepository implements RagTraceRepositoryPort {
        private final List<RagTraceNode> startedNodes = new ArrayList<>();
        private final List<RagTraceNodeFinish> finishedNodes = new ArrayList<>();

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
    }

    private static ApprovalRequestQueryPort approvalQuery(
            String approvalId,
            String runId,
            String stepId,
            String toolInvocationId,
            String toolId) {
        ApprovalRequest approval = new ApprovalRequest(
                approvalId,
                runId,
                stepId,
                toolInvocationId,
                "tenant-a",
                "user-1",
                "agent-1",
                null,
                toolId,
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool requires approval",
                "{}",
                ApprovalRequestStatus.PENDING,
                Instant.parse("2026-06-20T00:00:00Z"),
                null,
                null,
                null,
                null);
        return new ApprovalRequestQueryPort() {
            @Override
            public Optional<ApprovalRequest> findById(String approvalId) {
                return Optional.of(approval);
            }

            @Override
            public Optional<ApprovalRequest> findLatestByRunIdAndStepId(String requestedRunId, String requestedStepId) {
                return runId.equals(requestedRunId) && stepId.equals(requestedStepId)
                        ? Optional.of(approval)
                        : Optional.empty();
            }

            @Override
            public ApprovalRequestPage page(ApprovalRequestQuery query) {
                return new ApprovalRequestPage(List.of(approval), 1L, 1L, 1L, 1L);
            }
        };
    }
}
