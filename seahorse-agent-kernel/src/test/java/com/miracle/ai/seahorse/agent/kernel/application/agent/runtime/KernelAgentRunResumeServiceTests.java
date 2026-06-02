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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelAgentRunResumeServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldResumeApprovedRunFromLatestWaitingApprovalCheckpoint() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort approvals = new MemoryApprovalQueryPort(
                approval(ApprovalRequestStatus.APPROVED, "{\"argumentKeys\":[\"memoryId\"]}"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("{\"deleted\":true}"));
        SingleTurnModel model = new SingleTurnModel("Memory deleted");
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                approvals,
                toolGateway,
                model,
                currentUser(),
                FIXED_CLOCK);

        AgentRun resumed = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertEquals(1, toolGateway.requests.size());
        ToolInvocationRequest request = toolGateway.requests.get(0);
        assertEquals("run-1:call-1", request.idempotencyKey());
        assertEquals("memory-forget", request.toolId());
        assertEquals("mem-1", request.arguments().get("memoryId"));
        assertEquals(1, model.requests.size());
        assertEquals(2, runRepository.listSteps("run-1").size());
        assertEquals(AgentStepType.TOOL_CALL, runRepository.listSteps("run-1").get(0).stepType());
        assertEquals(AgentStepType.MODEL_TURN, runRepository.listSteps("run-1").get(1).stepType());
    }

    @Test
    void shouldNotExecuteToolWhenApprovalWasRejected() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort approvals = new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.REJECTED, null));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                approvals,
                toolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun rejected = service.resume("run-1");

        assertEquals(AgentRunStatus.REJECTED, rejected.status());
        assertEquals(0, toolGateway.requests.size());
        assertEquals(AgentRunStatus.REJECTED, runRepository.findRunById("run-1").orElseThrow().status());
    }

    private static AgentRun waitingRun() {
        return new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "forget memory",
                AgentRunStatus.WAITING_APPROVAL,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
    }

    private static AgentCheckpoint waitingCheckpoint() {
        return new AgentCheckpoint(
                "checkpoint-1",
                "run-1",
                "call-1",
                1L,
                AgentCheckpointType.WAITING_APPROVAL,
                "{\"exitReason\":\"WAITING_APPROVAL\"}",
                """
                        [{"role":"USER","content":"Forget memory"},
                         {"role":"ASSISTANT","content":"need approval","toolCalls":[
                           {"toolCallId":"call-1","toolId":"memory-forget","arguments":{"memoryId":"mem-1"}}
                         ]}]
                        """,
                null,
                """
                        {"toolId":"memory-forget","toolCallId":"call-1","arguments":{"memoryId":"mem-1"},
                         "resourceRefs":{},"idempotencyKey":"run-1:call-1","agentId":"agent-1",
                         "versionId":"version-1","runId":"run-1","tenantId":"tenant-1",
                         "userId":"user-1","agentIdentityId":"user-1","allowedToolIds":["memory-forget"]}
                        """,
                FIXED_CLOCK.instant());
    }

    private static ApprovalRequest approval(ApprovalRequestStatus status, String argumentsPreviewJson) {
        return new ApprovalRequest(
                "approval-1",
                "run-1",
                "call-1",
                "invocation-1",
                "tenant-1",
                "user-1",
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                argumentsPreviewJson == null ? "{\"argumentKeys\":[\"memoryId\"]}" : argumentsPreviewJson,
                status,
                FIXED_CLOCK.instant().minusSeconds(60),
                null,
                status == ApprovalRequestStatus.PENDING ? null : "admin-1",
                status == ApprovalRequestStatus.PENDING ? null : FIXED_CLOCK.instant().minusSeconds(1),
                status == ApprovalRequestStatus.PENDING ? null : "decided");
    }

    private static CurrentUserPort currentUser() {
        return () -> Optional.of(new CurrentUser(1L, "alice", "user", null));
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
                    .max(Comparator.comparingLong(AgentCheckpoint::sequenceNo));
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .sorted(Comparator.comparingLong(AgentCheckpoint::sequenceNo))
                    .toList();
        }
    }

    private static final class MemoryApprovalQueryPort implements ApprovalRequestQueryPort {
        private final ApprovalRequest approval;

        private MemoryApprovalQueryPort(ApprovalRequest approval) {
            this.approval = approval;
        }

        @Override
        public Optional<ApprovalRequest> findById(String approvalId) {
            return approval.approvalId().equals(approvalId) ? Optional.of(approval) : Optional.empty();
        }

        @Override
        public Optional<ApprovalRequest> findLatestByRunIdAndStepId(String runId, String stepId) {
            return runId.equals(approval.runId()) && stepId.equals(approval.stepId())
                    ? Optional.of(approval)
                    : Optional.empty();
        }

        @Override
        public ApprovalRequestPage page(ApprovalRequestQuery query) {
            return new ApprovalRequestPage(List.of(approval), 1L, query.size(), query.current(), 1L);
        }
    }

    private static final class RecordingToolGateway implements ToolGatewayPort {
        private final ToolInvocationResult result;
        private final List<ToolInvocationRequest> requests = new ArrayList<>();

        private RecordingToolGateway(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static final class SingleTurnModel implements StreamingChatModelPort {
        private final String answer;
        private final List<ChatRequest> requests = new ArrayList<>();

        private SingleTurnModel(String answer) {
            this.answer = answer;
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
            requests.add(request);
            callback.onContent(answer);
            toolCallCollector.onToolCalls(List.of());
            callback.onComplete();
            return () -> {
            };
        }
    }
}
