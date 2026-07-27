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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentFinalModelTurnPort;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.trace.RagTraceRecorderOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
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
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.TraceTelemetryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("rollout-1", request.rolloutId());
        assertEquals("memory-forget", request.toolId());
        assertEquals("mem-1", request.arguments().get("memoryId"));
        assertEquals(1, model.requests.size());
        AgentLoopRequest resumedRequest = model.requests.get(0);
        assertEquals("resume-model", resumedRequest.modelId());
        assertEquals(0.71D, resumedRequest.samplingOptions().getTemperature());
        assertEquals(0.82D, resumedRequest.samplingOptions().getTopP());
        assertEquals(17, resumedRequest.samplingOptions().getTopK());
        assertEquals(777, resumedRequest.samplingOptions().getMaxTokens());
        assertEquals(true, resumedRequest.samplingOptions().getThinking());
        assertEquals("runtime snapshot", resumedRequest.runtimeContextSnapshot());
        assertEquals("skill snapshot", resumedRequest.skillRuntimeContext());
        assertEquals(2, runRepository.listSteps("run-1").size());
        assertEquals(AgentStepType.TOOL_CALL, runRepository.listSteps("run-1").get(0).stepType());
        assertEquals(AgentStepType.MODEL_TURN, runRepository.listSteps("run-1").get(1).stepType());
    }

    @Test
    void shouldNotExecuteToolWhenAnotherInstanceClaimsResumeFirst() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        runRepository.rejectNextClaim = true;
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun observed = service.resume("run-1");

        assertEquals(AgentRunStatus.RUNNING, observed.status());
        assertEquals(0, toolGateway.requests.size());
        assertEquals(0, runRepository.listSteps("run-1").size());
    }

    @Test
    void shouldReclaimExpiredLeaseAndResumeRunLeftRunningByCrashedOwner() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        AgentRun waiting = waitingRun();
        runRepository.createRun(waiting.withStatus(AgentRunStatus.RUNNING, null, null, null));
        runRepository.installLease(
                "crashed-owner", FIXED_CLOCK.instant().minusSeconds(1));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                new SingleTurnModel("resumed after crash"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun resumed = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertEquals(1, toolGateway.requests.size());
        assertEquals(2, runRepository.listSteps("run-1").size());
        assertNull(runRepository.leaseOwner);
    }

    @Test
    void shouldResumeWhenRunStoresOperatorAliasAndApprovalStoresStableUserId() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun("admin"));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpointForUser("1"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded"));
        CurrentUserPort currentUser = () -> Optional.of(new CurrentUser(
                1L, "admin", "user", null, "tenant-1"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approvalForUser(ApprovalRequestStatus.APPROVED, null, "1")),
                toolGateway,
                new SingleTurnModel("resumed with stable identity"),
                currentUser,
                FIXED_CLOCK);

        AgentRun resumed = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertEquals("1", toolGateway.requests.getFirst().userId());
    }

    @Test
    void shouldRejectResumeWhenApprovalAndCheckpointStableUserIdsDiffer() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun("admin"));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpointForUser("1"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("must not run"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approvalForUser(ApprovalRequestStatus.APPROVED, null, "2")),
                toolGateway,
                new SingleTurnModel("must not run"),
                () -> Optional.of(new CurrentUser(1L, "admin", "user", null, "tenant-1")),
                FIXED_CLOCK);

        AgentRun failed = service.resume("run-1");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals(0, toolGateway.requests.size());
    }

    @Test
    void shouldNotExecuteToolWhenRunFinishesWhileResumeLeaseIsBeingAcquired() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        runRepository.finishRunAfterAcquire = true;
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun observed = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, observed.status());
        assertEquals(0, toolGateway.requests.size());
        assertEquals(0, runRepository.listSteps("run-1").size());
    }

    @Test
    void shouldHeartbeatLeaseDuringSlowToolExecution() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        AgentRunResumeInboundPort service = resumeService(
                runRepository,
                checkpointRepository,
                new SlowToolGateway(Duration.ofMillis(80)),
                Duration.ofMillis(10));

        AgentRun resumed = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertTrue(runRepository.heartbeatCount >= 2);
    }

    @Test
    void shouldNotWriteStepsOrTerminalStateAfterHeartbeatLosesLease() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        runRepository.loseLeaseOnNextHeartbeat = true;
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        AgentRunResumeInboundPort service = resumeService(
                runRepository,
                checkpointRepository,
                new SlowToolGateway(Duration.ofMillis(80)),
                Duration.ofMillis(10));

        AgentRun observed = service.resume("run-1");

        assertEquals(AgentRunStatus.RUNNING, observed.status());
        assertEquals(0, runRepository.listSteps("run-1").size());
        assertEquals("competing-owner", runRepository.leaseOwner);
    }

    @Test
    void shouldNotCallModelWhenLeaseIsLostAtToolStepFence() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        runRepository.loseLeaseOnNextStepAppend = true;
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded"));
        SingleTurnModel model = new SingleTurnModel("must-not-run");
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                model,
                currentUser(),
                FIXED_CLOCK);

        AgentRun observed = service.resume("run-1");

        assertEquals(AgentRunStatus.RUNNING, observed.status());
        assertEquals(1, toolGateway.requests.size());
        assertEquals(0, model.requests.size());
        assertEquals(0, runRepository.listSteps("run-1").size());
        assertEquals("competing-owner", runRepository.leaseOwner);
    }

    @Test
    void shouldResumeModifiedRunWithReplacementArguments() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort approvals = new MemoryApprovalQueryPort(
                approval(ApprovalRequestStatus.MODIFIED, "{\"arguments\":{\"memoryId\":\"mem-2\"}}"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("{\"deleted\":true}"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                approvals,
                toolGateway,
                new SingleTurnModel("Memory deleted"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun resumed = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertEquals(1, toolGateway.requests.size());
        assertEquals("mem-2", toolGateway.requests.get(0).arguments().get("memoryId"));
    }

    @Test
    void shouldNeverFallbackToRawCheckpointWhenResumeEvidenceIsUnavailable() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort approvals = new MemoryApprovalQueryPort(
                approval(ApprovalRequestStatus.APPROVED, "{\"argumentKeys\":[\"memoryId\"]}"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                approvals,
                new RecordingToolGateway(ToolInvocationResult.ok("{\"deleted\":true}")),
                new SingleTurnModel("Memory deleted", ""),
                currentUser(),
                FIXED_CLOCK);

        AgentRun resumed = service.resume("run-1");

        AgentStep modelStep = runRepository.listSteps("run-1").get(1);
        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertTrue(modelStep.inputJson().contains("\"reasonCode\":\"EVIDENCE_UNAVAILABLE\""));
        assertFalse(modelStep.inputJson().contains("Forget memory"));
        assertFalse(modelStep.inputJson().contains("toolCalls"));
    }

    @Test
    void shouldRedactResumedStepWritesWithoutChangingExecutionInputs() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint(
                """
                        [{"role":"USER","content":"Forget memory with api_key=history-secret-value"},
                         {"role":"ASSISTANT","content":"need approval","toolCalls":[
                           {"toolCallId":"call-1","toolId":"memory-forget","arguments":{"memoryId":"mem-1"}}
                         ]}]
                        """,
                """
                        {"toolId":"memory-forget","toolCallId":"call-1","arguments":{"memoryId":"mem-1"},
                         "resourceRefs":{},"idempotencyKey":"run-1:call-1","agentId":"agent-1",
                         "versionId":"version-1","runId":"run-1","tenantId":"tenant-1",
                         "userId":"user-1","agentIdentityId":"user-1","allowedToolIds":["memory-forget"]}
                        """));
        MemoryApprovalQueryPort approvals = new MemoryApprovalQueryPort(
                approval(ApprovalRequestStatus.MODIFIED,
                        "{\"arguments\":{\"memoryId\":\"mem-2\",\"apiKey\":\"secret-api-key-value\"}}"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(
                ToolInvocationResult.ok("{\"authorization\":\"Bearer tool-secret-123456\",\"deleted\":true}"));
        SingleTurnModel model = new SingleTurnModel("Memory deleted with session_token=model-secret-value");
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
        assertEquals("secret-api-key-value", toolGateway.requests.get(0).arguments().get("apiKey"));
        assertEquals("{\"authorization\":\"Bearer tool-secret-123456\",\"deleted\":true}",
                model.messages.get(0).get(2).getContent());
        List<AgentStep> steps = runRepository.listSteps("run-1");
        assertEquals(2, steps.size());
        assertEquals(AgentStepType.TOOL_CALL, steps.get(0).stepType());
        assertEquals(AgentStepType.MODEL_TURN, steps.get(1).stepType());
        assertFalse(steps.get(0).inputJson().contains("secret-api-key-value"));
        assertFalse(steps.get(0).outputJson().contains("tool-secret-123456"));
        assertFalse(steps.get(1).inputJson().contains("history-secret-value"));
        assertFalse(steps.get(1).outputJson().contains("model-secret-value"));
        assertTrue(steps.get(0).inputJson().contains("\"apiKey\":\"[REDACTED]\""), steps.get(0).inputJson());
        assertTrue(steps.get(0).inputJson().contains("\"memoryId\":\"mem-2\""), steps.get(0).inputJson());
        assertFalse(steps.get(1).outputJson().contains("session_token=model-secret-value"));
    }

    @Test
    void shouldRejectModifiedApprovalWithoutReplacementArguments() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort approvals = new MemoryApprovalQueryPort(
                approval(ApprovalRequestStatus.MODIFIED, "{\"argumentKeys\":[\"memoryId\"],\"modified\":true}"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                approvals,
                toolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun failed = service.resume("run-1");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals("Modified approval arguments must be an object", failed.errorMessage());
        assertEquals(0, toolGateway.requests.size());
    }

    @Test
    void shouldFailClosedBeforeToolExecutionWhenResumeDescriptorIsMissing() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        AgentCheckpoint valid = waitingCheckpoint();
        checkpointRepository.save(new AgentCheckpoint(
                valid.checkpointId(), valid.runId(), valid.stepId(), valid.sequenceNo(), valid.checkpointType(),
                "{\"exitReason\":\"WAITING_APPROVAL\"}", valid.messageHistoryJson(), null,
                valid.pendingToolCallJson(), valid.createdAt()));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun failed = service.resume("run-1");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals("Resume descriptor is missing", failed.errorMessage());
        assertEquals(0, toolGateway.requests.size());
    }

    @Test
    void shouldFinishRunAsFailedWhenResumedModelCallFails() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded")),
                (request, messages) -> {
                    throw new IllegalStateException("model failed with api_key=secret-value");
                },
                currentUser(),
                FIXED_CLOCK);

        AgentRun failed = service.resume("run-1");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals("MODEL_TURN_FAILED:IllegalStateException", failed.errorMessage());
        assertEquals(AgentRunStatus.FAILED, runRepository.findRunById("run-1").orElseThrow().status());
        AgentStep failedModelStep = runRepository.listSteps("run-1").get(1);
        assertEquals(AgentStepType.MODEL_TURN, failedModelStep.stepType());
        assertEquals(AgentStepStatus.FAILED, failedModelStep.status());
        assertTrue(failedModelStep.inputJson().contains("\"reasonCode\":\"EVIDENCE_UNAVAILABLE\""));
        assertEquals("MODEL_TURN_FAILED:IllegalStateException", failedModelStep.errorMessage());
    }

    @Test
    void shouldPersistSafeEnvelopeEvidenceWhenResumedModelFails() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded")),
                (request, messages) -> {
                    throw new AgentFinalModelTurnPort.FinalModelTurnException(
                            new IllegalStateException("provider failed"),
                            "{\"schemaVersion\":\"model-context-envelope-v1\","
                                    + "\"reasonCode\":\"PROVIDER_FAILED\",\"selectedInputTokens\":1200}");
                },
                currentUser(),
                FIXED_CLOCK);

        AgentRun failed = service.resume("run-1");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        AgentStep failedModelStep = runRepository.listSteps("run-1").get(1);
        assertEquals(AgentStepStatus.FAILED, failedModelStep.status());
        assertTrue(failedModelStep.inputJson().contains("\"reasonCode\":\"PROVIDER_FAILED\""));
        assertTrue(failedModelStep.inputJson().contains("\"selectedInputTokens\":1200"));
    }

    @Test
    void shouldFailRunWhenCanonicalModelStepWriteFails() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        runRepository.failModelStepWrites = true;
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded")),
                new SingleTurnModel("model succeeded"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun failed = service.resume("run-1");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals(1, runRepository.listSteps("run-1").size());
        assertEquals(AgentStepType.TOOL_CALL, runRepository.listSteps("run-1").getFirst().stepType());
    }

    @Test
    void shouldRejectResumeByAnotherTenantUserBeforeReadingCheckpoint() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("must not run"));
        CurrentUserPort otherUser = () -> Optional.of(new CurrentUser(
                2L, "user-2", "user", null, "tenant-2"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                new SingleTurnModel("must not run"),
                otherUser,
                FIXED_CLOCK);

        assertThrows(IllegalStateException.class, () -> service.resume("run-1"));
        assertEquals(0, toolGateway.requests.size());
        assertEquals(AgentRunStatus.WAITING_APPROVAL, runRepository.findRunById("run-1").orElseThrow().status());
    }

    @Test
    void shouldRejectResumeWhenNumericUserIdCollidesWithAnotherUsersOperatorAlias() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun("42"));
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpointForUser("42"));
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("must not run"));
        CurrentUserPort collidingUser = () -> Optional.of(new CurrentUser(
                42L, "attacker", "user", null, "tenant-1"));
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approvalForUser(ApprovalRequestStatus.APPROVED, null, "42")),
                toolGateway,
                new SingleTurnModel("must not run"),
                collidingUser,
                FIXED_CLOCK);

        assertThrows(IllegalStateException.class, () -> service.resume("run-1"));
        assertEquals(0, toolGateway.requests.size());
        assertEquals(AgentRunStatus.WAITING_APPROVAL, runRepository.findRunById("run-1").orElseThrow().status());
    }

    @Test
    void shouldNotReloadLegacyThinkingContentIntoResumedModelRequest() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint(
                """
                        [{"role":"USER","content":"Forget memory","thinkingContent":"private reasoning",
                          "thinkingDuration":99},
                         {"role":"ASSISTANT","content":"need approval","toolCalls":[
                           {"toolCallId":"call-1","toolId":"memory-forget","arguments":{"memoryId":"mem-1"}}
                         ]}]
                        """,
                waitingCheckpoint().pendingToolCallJson()));
        SingleTurnModel model = new SingleTurnModel("done");
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded")),
                model,
                currentUser(),
                FIXED_CLOCK);

        AgentRun succeeded = service.resume("run-1");

        assertEquals(AgentRunStatus.SUCCEEDED, succeeded.status());
        assertNull(model.messages.getFirst().getFirst().getThinkingContent());
        assertNull(model.messages.getFirst().getFirst().getThinkingDuration());
    }

    @Test
    void shouldUseRunTenantForAdminResumeAndRestoreCallerContext() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository checkpointRepository = new MemoryAgentCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint());
        RecordingToolGateway toolGateway = new RecordingToolGateway(ToolInvocationResult.ok("tool succeeded"));
        SingleTurnModel model = new SingleTurnModel("done");
        RecordingTraceTelemetry telemetry = new RecordingTraceTelemetry();
        KernelRagTraceRecorder traceRecorder = new KernelRagTraceRecorder(
                new NoopTraceRepository(), RagTraceRecorderOptions.always(), telemetry);
        AgentRunResumeInboundPort service = new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                model,
                () -> Optional.of(new CurrentUser(99L, "platform-admin", "admin", null, "admin-tenant")),
                FIXED_CLOCK,
                new ObjectMapper(),
                traceRecorder);

        AgentRun resumed;
        TenantContext.set("admin-tenant");
        try {
            resumed = service.resume("run-1");
            assertEquals("admin-tenant", TenantContext.capture());
        } finally {
            TenantContext.clear();
        }

        assertEquals(AgentRunStatus.SUCCEEDED, resumed.status());
        assertEquals(List.of("tenant-1"), toolGateway.tenantIds);
        assertEquals(List.of("tenant-1"), model.tenantIds);
        assertEquals("tenant-1", telemetry.runCommand.attributes().get("seahorse.tenant.id"));
        assertEquals("tenant-1", telemetry.tenantId);
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

    @Test
    void shouldRedactHistoricalApprovalDecisionCommentWhenRejectedOrExpired() {
        MemoryAgentRunRepository rejectedRunRepository = new MemoryAgentRunRepository();
        rejectedRunRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository rejectedCheckpointRepository = new MemoryAgentCheckpointRepository();
        rejectedCheckpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort rejectedApproval = new MemoryApprovalQueryPort(approval(
                ApprovalRequestStatus.REJECTED,
                null,
                "rejected with access_token=secret-marker"));
        RecordingToolGateway rejectedToolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort rejectedService = new KernelAgentRunResumeService(
                rejectedRunRepository,
                rejectedCheckpointRepository,
                rejectedApproval,
                rejectedToolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);
        MemoryAgentRunRepository expiredRunRepository = new MemoryAgentRunRepository();
        expiredRunRepository.createRun(waitingRun());
        MemoryAgentCheckpointRepository expiredCheckpointRepository = new MemoryAgentCheckpointRepository();
        expiredCheckpointRepository.save(waitingCheckpoint());
        MemoryApprovalQueryPort expiredApproval = new MemoryApprovalQueryPort(approval(
                ApprovalRequestStatus.EXPIRED,
                null,
                "expired with Authorization: Bearer secretmarker123"));
        RecordingToolGateway expiredToolGateway = new RecordingToolGateway(ToolInvocationResult.ok("should-not-run"));
        AgentRunResumeInboundPort expiredService = new KernelAgentRunResumeService(
                expiredRunRepository,
                expiredCheckpointRepository,
                expiredApproval,
                expiredToolGateway,
                new SingleTurnModel("should-not-run"),
                currentUser(),
                FIXED_CLOCK);

        AgentRun rejected = rejectedService.resume("run-1");
        AgentRun expired = expiredService.resume("run-1");

        assertEquals(AgentRunStatus.REJECTED, rejected.status());
        assertEquals("rejected with [REDACTED]", rejected.errorMessage());
        assertEquals("rejected with [REDACTED]",
                rejectedRunRepository.findRunById("run-1").orElseThrow().errorMessage());
        assertEquals(0, rejectedToolGateway.requests.size());
        assertEquals(AgentRunStatus.EXPIRED, expired.status());
        assertEquals("expired with [REDACTED]", expired.errorMessage());
        assertEquals("expired with [REDACTED]",
                expiredRunRepository.findRunById("run-1").orElseThrow().errorMessage());
        assertEquals(0, expiredToolGateway.requests.size());
    }

    private static AgentRun waitingRun() {
        return waitingRun("user-1");
    }

    private static AgentRun waitingRun(String userId) {
        return new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                userId,
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
        return waitingCheckpointForUser("user-1");
    }

    private static AgentCheckpoint waitingCheckpointForUser(String userId) {
        return waitingCheckpoint(
                """
                        [{"role":"USER","content":"Forget memory"},
                         {"role":"ASSISTANT","content":"need approval","toolCalls":[
                           {"toolCallId":"call-1","toolId":"memory-forget","arguments":{"memoryId":"mem-1"}}
                         ]}]
                        """,
                """
                        {"toolId":"memory-forget","toolCallId":"call-1","arguments":{"memoryId":"mem-1"},
                         "resourceRefs":{},"agentId":"agent-1",
                         "versionId":"version-1","runId":"run-1","tenantId":"tenant-1",
                         "userId":"%s","agentIdentityId":"%s","allowedToolIds":["memory-forget"]}
                        """.formatted(userId, userId));
    }

    private static AgentCheckpoint waitingCheckpoint(String messageHistoryJson, String pendingToolCallJson) {
        return new AgentCheckpoint(
                "checkpoint-1",
                "run-1",
                "call-1",
                1L,
                AgentCheckpointType.WAITING_APPROVAL,
                """
                        {"exitReason":"WAITING_APPROVAL","resumeDescriptor":{
                          "schemaVersion":"agent-resume-descriptor-v1",
                          "modelId":"resume-model",
                          "sampling":{"temperature":0.71,"topP":0.82,"topK":17,"maxTokens":777,"thinking":true},
                          "runtimeContextMode":"SNAPSHOT",
                          "runtimeContextSnapshot":"runtime snapshot",
                          "skillRuntimeContext":"skill snapshot",
                          "contextPackId":"context-pack-1",
                          "skillRevisions":[]
                        }}
                        """,
                messageHistoryJson,
                "context-pack-1",
                pendingToolCallJson,
                FIXED_CLOCK.instant());
    }

    private static ApprovalRequest approval(ApprovalRequestStatus status, String argumentsPreviewJson) {
        return approval(status, argumentsPreviewJson, "decided");
    }

    private static ApprovalRequest approval(ApprovalRequestStatus status,
                                             String argumentsPreviewJson,
                                             String decisionComment) {
        return approval(status, argumentsPreviewJson, decisionComment, "user-1");
    }

    private static ApprovalRequest approvalForUser(ApprovalRequestStatus status,
                                                   String argumentsPreviewJson,
                                                   String userId) {
        return approval(status, argumentsPreviewJson, "decided", userId);
    }

    private static ApprovalRequest approval(ApprovalRequestStatus status,
                                            String argumentsPreviewJson,
                                            String decisionComment,
                                            String userId) {
        return new ApprovalRequest(
                "approval-1",
                "run-1",
                "call-1",
                "invocation-1",
                "tenant-1",
                userId,
                "agent-1",
                "rollout-1",
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
                status == ApprovalRequestStatus.PENDING ? null : decisionComment);
    }

    private static CurrentUserPort currentUser() {
        return () -> Optional.of(new CurrentUser(1L, "user-1", "user", null, "tenant-1"));
    }

    private static AgentRunResumeInboundPort resumeService(
            MemoryAgentRunRepository runRepository,
            MemoryAgentCheckpointRepository checkpointRepository,
            ToolGatewayPort toolGateway,
            Duration heartbeatInterval) {
        return new KernelAgentRunResumeService(
                runRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED, null)),
                toolGateway,
                new SingleTurnModel("resumed"),
                currentUser(),
                FIXED_CLOCK,
                new ObjectMapper(),
                KernelRagTraceRecorder.noop(),
                Duration.ofSeconds(5),
                heartbeatInterval,
                Duration.ofSeconds(2));
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private final List<AgentStep> steps = new ArrayList<>();
        private boolean failModelStepWrites;
        private boolean rejectNextClaim;
        private boolean finishRunAfterAcquire;
        private boolean loseLeaseOnNextHeartbeat;
        private boolean loseLeaseOnNextStepAppend;
        private int heartbeatCount;
        private String leaseOwner;
        private Instant leaseUntil;

        @Override
        public void createRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public void updateRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public boolean updateRunIfStatus(AgentRun run, AgentRunStatus expectedStatus) {
            return AgentRunRepositoryPort.super.updateRunIfStatus(run, expectedStatus);
        }

        @Override
        public boolean acquireResumeLease(String runId, String ownerId, Instant nextLeaseUntil, Instant now) {
            if (leaseOwner != null && !leaseOwner.equals(ownerId) && leaseUntil != null && leaseUntil.isAfter(now)) {
                return false;
            }
            leaseOwner = ownerId;
            leaseUntil = nextLeaseUntil;
            if (finishRunAfterAcquire) {
                AgentRun run = runs.get(runId);
                runs.put(runId, run.withStatus(AgentRunStatus.SUCCEEDED, null, null, now));
            }
            return true;
        }

        @Override
        public boolean heartbeatResumeLease(String runId, String ownerId, Instant nextLeaseUntil, Instant now) {
            heartbeatCount++;
            if (loseLeaseOnNextHeartbeat) {
                loseLeaseOnNextHeartbeat = false;
                leaseOwner = "competing-owner";
                leaseUntil = now.plusSeconds(60);
                return false;
            }
            if (!ownerId.equals(leaseOwner) || leaseUntil == null || !leaseUntil.isAfter(now)) {
                return false;
            }
            leaseUntil = nextLeaseUntil;
            return true;
        }

        @Override
        public boolean releaseResumeLease(String runId, String ownerId) {
            if (!ownerId.equals(leaseOwner)) {
                return false;
            }
            leaseOwner = null;
            leaseUntil = null;
            return true;
        }

        @Override
        public boolean updateRunIfStatusAndResumeLeaseOwner(
                AgentRun run,
                AgentRunStatus expectedStatus,
                String ownerId,
                Instant now) {
            if (rejectNextClaim) {
                rejectNextClaim = false;
                AgentRun current = runs.get(run.runId());
                runs.put(run.runId(), current.withStatus(AgentRunStatus.RUNNING, null, null, null));
                leaseOwner = "competing-owner";
                leaseUntil = now.plusSeconds(60);
                return false;
            }
            if (!ownerId.equals(leaseOwner) || leaseUntil == null || !leaseUntil.isAfter(now)) {
                return false;
            }
            return AgentRunRepositoryPort.super.updateRunIfStatus(run, expectedStatus);
        }

        @Override
        public boolean appendStepIfResumeLeaseOwner(AgentStep step, String ownerId, Instant now) {
            if (loseLeaseOnNextStepAppend) {
                loseLeaseOnNextStepAppend = false;
                leaseOwner = "competing-owner";
                leaseUntil = now.plusSeconds(60);
                return false;
            }
            if (!ownerId.equals(leaseOwner) || leaseUntil == null || !leaseUntil.isAfter(now)) {
                return false;
            }
            appendStep(step);
            return true;
        }

        private void installLease(String ownerId, Instant until) {
            leaseOwner = ownerId;
            leaseUntil = until;
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendStep(AgentStep step) {
            if (failModelStepWrites && step.stepType() == AgentStepType.MODEL_TURN) {
                throw new IllegalStateException("model step storage unavailable");
            }
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
        private final List<String> tenantIds = new ArrayList<>();

        private RecordingToolGateway(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requests.add(request);
            tenantIds.add(TenantContext.capture());
            return result;
        }
    }

    private static final class SlowToolGateway implements ToolGatewayPort {
        private final Duration delay;

        private SlowToolGateway(Duration delay) {
            this.delay = delay;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            try {
                Thread.sleep(delay);
                return ToolInvocationResult.ok("slow tool completed");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("slow tool interrupted", ex);
            }
        }
    }

    private static final class SingleTurnModel implements AgentFinalModelTurnPort {
        private final String answer;
        private final String safeEvidenceJson;
        private final List<AgentLoopRequest> requests = new ArrayList<>();
        private final List<List<ChatMessage>> messages = new ArrayList<>();
        private final List<String> tenantIds = new ArrayList<>();

        private SingleTurnModel(String answer) {
            this(answer, "{\"schemaVersion\":\"model-context-envelope-v1\",\"reasonCode\":\"OK\"}");
        }

        private SingleTurnModel(String answer, String safeEvidenceJson) {
            this.answer = answer;
            this.safeEvidenceJson = safeEvidenceJson;
        }

        @Override
        public FinalModelTurnResult requestFinalModelTurn(
                AgentLoopRequest request, List<ChatMessage> messages) {
            requests.add(request);
            this.messages.add(List.copyOf(messages));
            tenantIds.add(TenantContext.capture());
            return new FinalModelTurnResult(answer, safeEvidenceJson);
        }
    }

    private static final class RecordingTraceTelemetry implements TraceTelemetryPort {
        private TraceRunStartCommand runCommand;
        private String tenantId;

        @Override
        public TraceTelemetryLink startRun(String traceId, TraceRunStartCommand command, Instant startTime) {
            runCommand = command;
            tenantId = TenantContext.capture();
            return TraceTelemetryLink.empty();
        }

        @Override
        public void finishRun(String traceId, String errorMessage, Instant endTime) {
        }

        @Override
        public void startNode(String traceId, String nodeId, TraceNodeStartCommand command, Instant startTime) {
        }

        @Override
        public void finishNode(String traceId, String nodeId, String errorMessage, Instant endTime) {
        }

        @Override
        public void recordRunAttribute(String traceId, String key, String value) {
        }
    }

    private static final class NoopTraceRepository implements RagTraceRepositoryPort {
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
        }

        @Override
        public void finishNode(RagTraceNodeFinish finish) {
        }
    }
}
