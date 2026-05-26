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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerOutcome;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerSkipReason;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerTickRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerTickResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQueueRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentRunWorkerService implements AgentRunWorkerInboundPort {

    private final AgentRunQueueRepositoryPort queueRepository;
    private final AgentRunRepositoryPort runRepository;
    private final AgentCheckpointRepositoryPort checkpointRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final AgentRunLeaseInboundPort leasePort;
    private final AgentRunResumeInboundPort resumePort;
    private final Clock clock;

    public KernelAgentRunWorkerService(AgentRunQueueRepositoryPort queueRepository,
                                       AgentRunRepositoryPort runRepository,
                                       AgentCheckpointRepositoryPort checkpointRepository,
                                       ApprovalRequestQueryPort approvalQueryPort,
                                       AgentRunLeaseInboundPort leasePort,
                                       AgentRunResumeInboundPort resumePort,
                                       Clock clock) {
        this.queueRepository = Objects.requireNonNull(queueRepository, "queueRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.checkpointRepository = Objects.requireNonNull(
                checkpointRepository,
                "checkpointRepository must not be null");
        this.approvalQueryPort = Objects.requireNonNull(approvalQueryPort, "approvalQueryPort must not be null");
        this.leasePort = Objects.requireNonNull(leasePort, "leasePort must not be null");
        this.resumePort = Objects.requireNonNull(resumePort, "resumePort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public AgentRunWorkerTickResult tick(AgentRunWorkerCommand command) {
        AgentRunWorkerCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        int limit = AgentRunWorkerLimits.safeMaxRuns(safeCommand.maxRuns());
        List<AgentRun> candidates = queueRepository.findRunnable(safeCommand.tenantId(), limit, safeCommand.now());
        if (candidates == null || candidates.isEmpty()) {
            return new AgentRunWorkerTickResult(
                    0L,
                    0L,
                    List.of(AgentRunWorkerTickRecord.of(null, AgentRunWorkerOutcome.NO_RUNNABLE_RUN, null)));
        }
        List<AgentRunWorkerTickRecord> records = new ArrayList<>();
        long processed = 0L;
        long leaseConflicts = 0L;
        for (AgentRun candidate : candidates) {
            AgentRun safeCandidate = Objects.requireNonNull(candidate, "candidate run must not be null");
            AgentRunWorkerTickRecord record = processCandidate(safeCandidate, safeCommand);
            records.add(record);
            if (record.outcome() == AgentRunWorkerOutcome.LEASE_CONFLICT) {
                leaseConflicts++;
            }
            if (record.skipReason() == null && (record.outcome() == AgentRunWorkerOutcome.CLAIMED
                    || record.outcome() == AgentRunWorkerOutcome.STEP_COMPLETED
                    || record.outcome() == AgentRunWorkerOutcome.RUN_FINISHED
                    || record.outcome() == AgentRunWorkerOutcome.RUN_FAILED)) {
                processed++;
            }
        }
        return new AgentRunWorkerTickResult(processed, leaseConflicts, records);
    }

    private AgentRunWorkerTickRecord processCandidate(AgentRun candidate, AgentRunWorkerCommand command) {
        if (!candidate.status().isWorkerRunnable()) {
            return notRunnable(candidate);
        }
        boolean acquired = leasePort.acquire(new AgentRunLeaseCommand(
                candidate.runId(),
                command.workerId(),
                command.leaseTtl()));
        if (!acquired) {
            return AgentRunWorkerTickRecord.of(
                    candidate.runId(),
                    AgentRunWorkerOutcome.LEASE_CONFLICT,
                    AgentRunWorkerSkipReason.LEASE_HELD);
        }
        try {
            AgentRun latest = runRepository.findRunById(candidate.runId()).orElse(candidate);
            if (latest.status() == AgentRunStatus.WAITING_APPROVAL) {
                return handleWaitingApproval(latest);
            }
            if (!latest.status().isWorkerRunnable()) {
                return notRunnable(latest);
            }
            return AgentRunWorkerTickRecord.of(candidate.runId(), AgentRunWorkerOutcome.CLAIMED, null);
        } finally {
            leasePort.release(candidate.runId(), command.workerId());
        }
    }

    private AgentRunWorkerTickRecord handleWaitingApproval(AgentRun run) {
        Optional<AgentCheckpoint> checkpoint = checkpointRepository.findLatestByRunId(run.runId())
                .filter(item -> item.checkpointType() == AgentCheckpointType.WAITING_APPROVAL);
        if (checkpoint.isEmpty()) {
            return AgentRunWorkerTickRecord.of(
                    run.runId(),
                    AgentRunWorkerOutcome.WAITING_APPROVAL,
                    AgentRunWorkerSkipReason.MISSING_CHECKPOINT);
        }
        Optional<ApprovalRequest> approval = approvalQueryPort.findLatestByRunIdAndStepId(
                run.runId(),
                checkpoint.orElseThrow().stepId());
        if (approval.isEmpty() || !isResumeDecision(approval.orElseThrow().status())) {
            return AgentRunWorkerTickRecord.of(
                    run.runId(),
                    AgentRunWorkerOutcome.WAITING_APPROVAL,
                    AgentRunWorkerSkipReason.APPROVAL_PENDING);
        }
        AgentRun resumed = resumePort.resume(run.runId());
        return AgentRunWorkerTickRecord.of(run.runId(), outcomeFor(resumed), null);
    }

    private boolean isResumeDecision(ApprovalRequestStatus status) {
        return status == ApprovalRequestStatus.APPROVED || status == ApprovalRequestStatus.MODIFIED;
    }

    private AgentRunWorkerTickRecord notRunnable(AgentRun run) {
        if (run.status() == AgentRunStatus.WAITING_APPROVAL) {
            return AgentRunWorkerTickRecord.of(
                    run.runId(),
                    AgentRunWorkerOutcome.WAITING_APPROVAL,
                    AgentRunWorkerSkipReason.APPROVAL_PENDING);
        }
        return AgentRunWorkerTickRecord.of(
                run.runId(),
                outcomeFor(run),
                run.status().isFinished()
                        ? AgentRunWorkerSkipReason.TERMINAL_STATUS
                        : AgentRunWorkerSkipReason.NOT_WORKER_RUNNABLE);
    }

    private AgentRunWorkerOutcome outcomeFor(AgentRun run) {
        AgentRun safeRun = Objects.requireNonNull(run, "run must not be null");
        if (safeRun.status() == AgentRunStatus.FAILED) {
            return AgentRunWorkerOutcome.RUN_FAILED;
        }
        if (safeRun.status() == AgentRunStatus.CANCELLED) {
            return AgentRunWorkerOutcome.CANCELLED;
        }
        if (safeRun.status().isFinished()) {
            return AgentRunWorkerOutcome.RUN_FINISHED;
        }
        if (safeRun.status() == AgentRunStatus.WAITING_APPROVAL) {
            return AgentRunWorkerOutcome.WAITING_APPROVAL;
        }
        return AgentRunWorkerOutcome.CLAIMED;
    }
}
