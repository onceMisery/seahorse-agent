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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunLease;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentRunLeaseService implements AgentRunLeaseInboundPort {

    private final AgentRunRepositoryPort runRepository;
    private final AgentRunLeaseRepositoryPort leaseRepository;
    private final Clock clock;

    public KernelAgentRunLeaseService(AgentRunRepositoryPort runRepository,
                                      AgentRunLeaseRepositoryPort leaseRepository,
                                      Clock clock) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.leaseRepository = Objects.requireNonNull(leaseRepository, "leaseRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public boolean acquire(AgentRunLeaseCommand command) {
        AgentRunLeaseCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentRun run = loadRun(safeCommand.runId());
        if (run.status().isTerminal()) {
            return false;
        }
        Instant now = clock.instant();
        return leaseRepository.acquire(
                safeCommand.runId(),
                safeCommand.workerId(),
                now.plus(safeCommand.leaseTtl()),
                now);
    }

    @Override
    public boolean heartbeat(AgentRunLeaseCommand command) {
        AgentRunLeaseCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentRun run = loadRun(safeCommand.runId());
        if (run.status().isTerminal()) {
            return false;
        }
        Instant now = clock.instant();
        return leaseRepository.heartbeat(
                safeCommand.runId(),
                safeCommand.workerId(),
                now.plus(safeCommand.leaseTtl()),
                now);
    }

    @Override
    public boolean release(String runId, String workerId) {
        return leaseRepository.release(requireText(runId, "runId must not be blank"),
                requireText(workerId, "workerId must not be blank"));
    }

    @Override
    public Optional<AgentRunLease> findByRunId(String runId) {
        return leaseRepository.findByRunId(requireText(runId, "runId must not be blank"));
    }

    private AgentRun loadRun(String runId) {
        return runRepository.findRunById(requireText(runId, "runId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
