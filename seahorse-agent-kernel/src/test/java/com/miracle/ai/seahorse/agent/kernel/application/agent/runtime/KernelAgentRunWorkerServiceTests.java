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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerOutcome;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerSkipReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerTickResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQueueRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentRunWorkerServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TEST_LEASE_TTL = Duration.ofSeconds(30);

    @Test
    void noRunnableRunReturnsNoRunOutcome() {
        MemoryRunRepository runRepository = new MemoryRunRepository();
        MemoryRunQueueRepository queueRepository = new MemoryRunQueueRepository(List.of());
        RecordingLeasePort leasePort = new RecordingLeasePort(true);
        RecordingResumePort resumePort = new RecordingResumePort(run("unused", AgentRunStatus.SUCCEEDED));
        KernelAgentRunWorkerService service = service(
                runRepository,
                queueRepository,
                new MemoryCheckpointRepository(),
                new MemoryApprovalQueryPort(List.of()),
                leasePort,
                resumePort);

        AgentRunWorkerTickResult result = service.tick(command());

        assertEquals(0, result.processed());
        assertEquals(0, result.leaseConflicts());
        assertEquals(1, result.records().size());
        assertEquals(AgentRunWorkerOutcome.NO_RUNNABLE_RUN, result.records().get(0).outcome());
        assertNull(result.records().get(0).skipReason());
        assertNull(result.records().get(0).runId());
        assertTrue(leasePort.acquireCommands.isEmpty());
        assertTrue(resumePort.runIds.isEmpty());
    }

    @Test
    void queueReturningTerminalRunIsSkippedByDomainStatus() {
        MemoryRunRepository runRepository = new MemoryRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.SUCCEEDED));
        MemoryRunQueueRepository queueRepository = new MemoryRunQueueRepository(List.of(runRepository.get("run-1")));
        RecordingLeasePort leasePort = new RecordingLeasePort(true);
        RecordingResumePort resumePort = new RecordingResumePort(runRepository.get("run-1"));
        KernelAgentRunWorkerService service = service(
                runRepository,
                queueRepository,
                new MemoryCheckpointRepository(),
                new MemoryApprovalQueryPort(List.of()),
                leasePort,
                resumePort);

        AgentRunWorkerTickResult result = service.tick(command());

        assertEquals(0, result.processed());
        assertEquals(1, result.records().size());
        assertEquals("run-1", result.records().get(0).runId());
        assertEquals(AgentRunWorkerOutcome.RUN_FINISHED, result.records().get(0).outcome());
        assertEquals(AgentRunWorkerSkipReason.TERMINAL_STATUS, result.records().get(0).skipReason());
        assertTrue(leasePort.acquireCommands.isEmpty());
        assertTrue(resumePort.runIds.isEmpty());
    }

    @Test
    void leaseConflictDoesNotCallResume() {
        MemoryRunRepository runRepository = new MemoryRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.RUNNING));
        MemoryRunQueueRepository queueRepository = new MemoryRunQueueRepository(List.of(runRepository.get("run-1")));
        RecordingLeasePort leasePort = new RecordingLeasePort(false);
        RecordingResumePort resumePort = new RecordingResumePort(runRepository.get("run-1"));
        KernelAgentRunWorkerService service = service(
                runRepository,
                queueRepository,
                new MemoryCheckpointRepository(),
                new MemoryApprovalQueryPort(List.of()),
                leasePort,
                resumePort);

        AgentRunWorkerTickResult result = service.tick(command());

        assertEquals(0, result.processed());
        assertEquals(1, result.leaseConflicts());
        assertEquals(1, result.records().size());
        assertEquals("run-1", result.records().get(0).runId());
        assertEquals(AgentRunWorkerOutcome.LEASE_CONFLICT, result.records().get(0).outcome());
        assertEquals(AgentRunWorkerSkipReason.LEASE_HELD, result.records().get(0).skipReason());
        assertEquals(1, leasePort.acquireCommands.size());
        assertEquals(0, leasePort.releaseCalls);
        assertTrue(resumePort.runIds.isEmpty());
    }

    @Test
    void waitingApprovalRunReleasesLeaseWithoutResume() {
        MemoryRunRepository runRepository = new MemoryRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.RUNNING));
        MemoryRunQueueRepository queueRepository = new MemoryRunQueueRepository(List.of(runRepository.get("run-1")));
        RecordingLeasePort leasePort = new RecordingLeasePort(true);
        RecordingResumePort resumePort = new RecordingResumePort(runRepository.get("run-1"));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint("run-1", "step-1"));
        KernelAgentRunWorkerService service = service(
                runRepository,
                queueRepository,
                checkpointRepository,
                new MemoryApprovalQueryPort(List.of(approval("run-1", "step-1", ApprovalRequestStatus.PENDING))),
                leasePort,
                resumePort);
        runRepository.updateRun(run("run-1", AgentRunStatus.WAITING_APPROVAL));

        AgentRunWorkerTickResult result = service.tick(command());

        assertEquals(0, result.processed());
        assertEquals(1, result.records().size());
        assertEquals("run-1", result.records().get(0).runId());
        assertEquals(AgentRunWorkerOutcome.WAITING_APPROVAL, result.records().get(0).outcome());
        assertEquals(AgentRunWorkerSkipReason.APPROVAL_PENDING, result.records().get(0).skipReason());
        assertEquals(1, leasePort.acquireCommands.size());
        assertEquals(1, leasePort.releaseCalls);
        assertTrue(resumePort.runIds.isEmpty());
    }

    @Test
    void approvedCheckpointDelegatesToResumePort() {
        MemoryRunRepository runRepository = new MemoryRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.WAITING_APPROVAL));
        MemoryRunQueueRepository queueRepository = new MemoryRunQueueRepository(
                List.of(run("run-1", AgentRunStatus.RUNNING)));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(waitingCheckpoint("run-1", "step-1"));
        MemoryApprovalQueryPort approvalQueryPort = new MemoryApprovalQueryPort(
                List.of(approval("run-1", "step-1", ApprovalRequestStatus.APPROVED)));
        RecordingLeasePort leasePort = new RecordingLeasePort(true);
        RecordingResumePort resumePort = new RecordingResumePort(run("run-1", AgentRunStatus.SUCCEEDED));
        KernelAgentRunWorkerService service = service(
                runRepository,
                queueRepository,
                checkpointRepository,
                approvalQueryPort,
                leasePort,
                resumePort);

        AgentRunWorkerTickResult result = service.tick(command());

        assertEquals(1, result.processed());
        assertEquals(1, result.records().size());
        assertEquals("run-1", result.records().get(0).runId());
        assertEquals(AgentRunWorkerOutcome.RUN_FINISHED, result.records().get(0).outcome());
        assertNull(result.records().get(0).skipReason());
        assertEquals(List.of("run-1"), resumePort.runIds);
        assertEquals(1, leasePort.releaseCalls);
    }

    private static KernelAgentRunWorkerService service(MemoryRunRepository runRepository,
                                                       MemoryRunQueueRepository queueRepository,
                                                       MemoryCheckpointRepository checkpointRepository,
                                                       MemoryApprovalQueryPort approvalQueryPort,
                                                       RecordingLeasePort leasePort,
                                                       RecordingResumePort resumePort) {
        return new KernelAgentRunWorkerService(
                queueRepository,
                runRepository,
                checkpointRepository,
                approvalQueryPort,
                leasePort,
                resumePort,
                FIXED_CLOCK);
    }

    private static AgentRunWorkerCommand command() {
        return new AgentRunWorkerCommand("tenant-1", "worker-1", 5, TEST_LEASE_TTL, NOW);
    }

    private static AgentRun run(String runId, AgentRunStatus status) {
        return new AgentRun(
                runId,
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                null,
                AgentRunTriggerType.API,
                "summary",
                status,
                null,
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                status.isFinished() ? NOW.plusSeconds(1) : null);
    }

    private static AgentCheckpoint waitingCheckpoint(String runId, String stepId) {
        return new AgentCheckpoint(
                "checkpoint-" + runId,
                runId,
                stepId,
                1L,
                AgentCheckpointType.WAITING_APPROVAL,
                "{\"exitReason\":\"WAITING_APPROVAL\"}",
                "[]",
                null,
                "{\"toolId\":\"memory-forget\"}",
                NOW);
    }

    private static ApprovalRequest approval(String runId, String stepId, ApprovalRequestStatus status) {
        return new ApprovalRequest(
                "approval-" + runId,
                runId,
                stepId,
                "invocation-1",
                "tenant-1",
                "user-1",
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                "{\"argumentKeys\":[\"memoryId\"]}",
                status,
                NOW.minusSeconds(60),
                null,
                status == ApprovalRequestStatus.PENDING ? null : "admin-1",
                status == ApprovalRequestStatus.PENDING ? null : NOW.minusSeconds(1),
                status == ApprovalRequestStatus.PENDING ? null : "decided");
    }

    private static final class MemoryRunRepository implements AgentRunRepositoryPort {
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

        private AgentRun get(String runId) {
            return runs.get(runId);
        }
    }

    private static final class MemoryRunQueueRepository implements AgentRunQueueRepositoryPort {
        private final List<AgentRun> candidates;
        private String lastTenantId;
        private int lastLimit;
        private Instant lastNow;

        private MemoryRunQueueRepository(List<AgentRun> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<AgentRun> findRunnable(String tenantId, int limit, Instant now) {
            lastTenantId = tenantId;
            lastLimit = limit;
            lastNow = now;
            return candidates.stream().limit(limit).toList();
        }
    }

    private static final class MemoryCheckpointRepository implements AgentCheckpointRepositoryPort {
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
        private final List<ApprovalRequest> approvals;

        private MemoryApprovalQueryPort(List<ApprovalRequest> approvals) {
            this.approvals = approvals;
        }

        @Override
        public Optional<ApprovalRequest> findById(String approvalId) {
            return approvals.stream()
                    .filter(approval -> approvalId.equals(approval.approvalId()))
                    .findFirst();
        }

        @Override
        public Optional<ApprovalRequest> findLatestByRunIdAndStepId(String runId, String stepId) {
            return approvals.stream()
                    .filter(approval -> runId.equals(approval.runId()))
                    .filter(approval -> stepId.equals(approval.stepId()))
                    .findFirst();
        }

        @Override
        public ApprovalRequestPage page(ApprovalRequestQuery query) {
            return new ApprovalRequestPage(List.of(), 0L, query.size(), query.current(), 0L);
        }
    }

    private static final class RecordingLeasePort implements AgentRunLeaseInboundPort {
        private final boolean acquireResult;
        private final List<AgentRunLeaseCommand> acquireCommands = new ArrayList<>();
        private int releaseCalls;

        private RecordingLeasePort(boolean acquireResult) {
            this.acquireResult = acquireResult;
        }

        @Override
        public boolean acquire(AgentRunLeaseCommand command) {
            acquireCommands.add(command);
            return acquireResult;
        }

        @Override
        public boolean heartbeat(AgentRunLeaseCommand command) {
            return false;
        }

        @Override
        public boolean release(String runId, String workerId) {
            releaseCalls++;
            return true;
        }

        @Override
        public Optional<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunLease> findByRunId(
                String runId) {
            return Optional.empty();
        }
    }

    private static final class RecordingResumePort implements AgentRunResumeInboundPort {
        private final AgentRun result;
        private final List<String> runIds = new ArrayList<>();

        private RecordingResumePort(AgentRun result) {
            this.result = result;
        }

        @Override
        public AgentRun resume(String runId) {
            runIds.add(runId);
            return result;
        }
    }
}
