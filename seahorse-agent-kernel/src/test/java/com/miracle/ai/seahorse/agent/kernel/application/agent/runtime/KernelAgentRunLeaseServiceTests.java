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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunLease;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentRunLeaseServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration TEST_LEASE_TTL = Duration.ofSeconds(30);

    @Test
    void shouldAcquireLeaseForRunnableRun() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.RUNNING));
        MemoryAgentRunLeaseRepository leaseRepository = new MemoryAgentRunLeaseRepository();
        KernelAgentRunLeaseService service = new KernelAgentRunLeaseService(runRepository, leaseRepository, FIXED_CLOCK);

        boolean acquired = service.acquire(new AgentRunLeaseCommand("run-1", "worker-1", TEST_LEASE_TTL));

        assertTrue(acquired);
        AgentRunLease lease = leaseRepository.findByRunId("run-1").orElseThrow();
        assertEquals("worker-1", lease.workerId());
        assertEquals(FIXED_CLOCK.instant().plus(TEST_LEASE_TTL), lease.leaseUntil());
        assertEquals(FIXED_CLOCK.instant(), lease.heartbeatAt());
    }

    @Test
    void shouldAcquireLeaseForRetryingRun() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.RETRYING));
        MemoryAgentRunLeaseRepository leaseRepository = new MemoryAgentRunLeaseRepository();
        KernelAgentRunLeaseService service = new KernelAgentRunLeaseService(runRepository, leaseRepository, FIXED_CLOCK);

        boolean acquired = service.acquire(new AgentRunLeaseCommand("run-1", "worker-1", TEST_LEASE_TTL));

        assertTrue(acquired);
        assertEquals("worker-1", leaseRepository.findByRunId("run-1").orElseThrow().workerId());
    }

    @Test
    void shouldRenewLeaseOnlyForCurrentOwner() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.RUNNING));
        MemoryAgentRunLeaseRepository leaseRepository = new MemoryAgentRunLeaseRepository();
        KernelAgentRunLeaseService service = new KernelAgentRunLeaseService(runRepository, leaseRepository, FIXED_CLOCK);
        service.acquire(new AgentRunLeaseCommand("run-1", "worker-1", TEST_LEASE_TTL));

        assertFalse(service.heartbeat(new AgentRunLeaseCommand("run-1", "worker-2", TEST_LEASE_TTL)));
        assertTrue(service.heartbeat(new AgentRunLeaseCommand("run-1", "worker-1", TEST_LEASE_TTL)));

        AgentRunLease lease = leaseRepository.findByRunId("run-1").orElseThrow();
        assertEquals("worker-1", lease.workerId());
        assertEquals(FIXED_CLOCK.instant().plus(TEST_LEASE_TTL), lease.leaseUntil());
    }

    @Test
    void shouldNotAcquireLeaseForNonRunnableRun() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        MemoryAgentRunLeaseRepository leaseRepository = new MemoryAgentRunLeaseRepository();
        KernelAgentRunLeaseService service = new KernelAgentRunLeaseService(runRepository, leaseRepository, FIXED_CLOCK);

        for (AgentRunStatus status : List.of(
                AgentRunStatus.WAITING_APPROVAL,
                AgentRunStatus.SUCCEEDED,
                AgentRunStatus.FAILED,
                AgentRunStatus.REJECTED,
                AgentRunStatus.EXPIRED,
                AgentRunStatus.CANCELLED)) {
            String runId = "run-" + status.name().toLowerCase();
            runRepository.createRun(run(runId, status));

            boolean acquired = service.acquire(new AgentRunLeaseCommand(runId, "worker-1", TEST_LEASE_TTL));

            assertFalse(acquired, "status " + status + " should not be leased");
            assertTrue(leaseRepository.findByRunId(runId).isEmpty());
        }
    }

    @Test
    void shouldReleaseOnlyOwnedLease() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", AgentRunStatus.RUNNING));
        MemoryAgentRunLeaseRepository leaseRepository = new MemoryAgentRunLeaseRepository();
        KernelAgentRunLeaseService service = new KernelAgentRunLeaseService(runRepository, leaseRepository, FIXED_CLOCK);
        service.acquire(new AgentRunLeaseCommand("run-1", "worker-1", TEST_LEASE_TTL));

        assertFalse(service.release("run-1", "worker-2"));
        assertTrue(leaseRepository.findByRunId("run-1").isPresent());
        assertTrue(service.release("run-1", "worker-1"));
        assertTrue(leaseRepository.findByRunId("run-1").isEmpty());
    }

    @Test
    void shouldRejectLeaseForMissingRun() {
        KernelAgentRunLeaseService service = new KernelAgentRunLeaseService(
                new MemoryAgentRunRepository(), new MemoryAgentRunLeaseRepository(), FIXED_CLOCK);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.acquire(new AgentRunLeaseCommand("missing-run", "worker-1", TEST_LEASE_TTL)));

        assertEquals("Agent run does not exist", error.getMessage());
    }

    private static AgentRun run(String runId, AgentRunStatus status) {
        return new AgentRun(
                runId,
                "agent-1",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                "user-1",
                null,
                AgentRunTriggerType.API,
                "summary",
                status,
                null,
                0L,
                0L,
                AgentRun.ZERO_COST,
                null,
                null,
                FIXED_CLOCK.instant(),
                null);
    }

    private static class MemoryAgentRunRepository implements AgentRunRepositoryPort {
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

    private static class MemoryAgentRunLeaseRepository implements AgentRunLeaseRepositoryPort {
        private final Map<String, AgentRunLease> leases = new LinkedHashMap<>();

        @Override
        public boolean acquire(String runId, String workerId, Instant leaseUntil, Instant now) {
            AgentRunLease current = leases.get(runId);
            if (current != null && !workerId.equals(current.workerId()) && !current.isExpiredAt(now)) {
                return false;
            }
            leases.put(runId, new AgentRunLease(runId, workerId, leaseUntil, now));
            return true;
        }

        @Override
        public boolean heartbeat(String runId, String workerId, Instant leaseUntil, Instant now) {
            AgentRunLease current = leases.get(runId);
            if (current == null || !workerId.equals(current.workerId()) || current.isExpiredAt(now)) {
                return false;
            }
            leases.put(runId, new AgentRunLease(runId, workerId, leaseUntil, now));
            return true;
        }

        @Override
        public boolean release(String runId, String workerId) {
            AgentRunLease current = leases.get(runId);
            if (current == null || !workerId.equals(current.workerId())) {
                return false;
            }
            leases.remove(runId);
            return true;
        }

        @Override
        public Optional<AgentRunLease> findByRunId(String runId) {
            return Optional.ofNullable(leases.get(runId));
        }
    }
}
