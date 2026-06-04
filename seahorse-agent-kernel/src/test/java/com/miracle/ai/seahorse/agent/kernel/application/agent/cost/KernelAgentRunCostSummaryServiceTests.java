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

package com.miracle.ai.seahorse.agent.kernel.application.agent.cost;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAgentRunCostSummaryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAggregateOwnerRunCostByRunTenantAndAgent() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository(run("user-1"));
        CapturingCostUsageRepository costRepository = new CapturingCostUsageRepository();
        KernelAgentRunCostSummaryService service = new KernelAgentRunCostSummaryService(
                runRepository,
                costRepository,
                currentUser(1L, "user"));

        CostUsageAggregate aggregate = service.getCostSummary("run-1");

        assertEquals("tenant-a", aggregate.tenantId());
        assertEquals("agent-1", aggregate.agentId());
        assertEquals("run-1", aggregate.runId());
        assertEquals("tenant-a", costRepository.lastQuery.tenantId());
        assertEquals("agent-1", costRepository.lastQuery.agentId());
        assertEquals("run-1", costRepository.lastQuery.runId());
    }

    @Test
    void shouldAllowAdminToReadAnotherUsersRunCostSummary() {
        KernelAgentRunCostSummaryService service = new KernelAgentRunCostSummaryService(
                new MemoryAgentRunRepository(run("user-1")),
                new CapturingCostUsageRepository(),
                currentUser(1L, "admin"));

        CostUsageAggregate aggregate = service.getCostSummary("run-1");

        assertEquals(321L, aggregate.totalTokens());
    }

    @Test
    void shouldRejectUnrelatedUserRunCostSummary() {
        KernelAgentRunCostSummaryService service = new KernelAgentRunCostSummaryService(
                new MemoryAgentRunRepository(run("user-1")),
                new CapturingCostUsageRepository(),
                currentUser(3L, "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getCostSummary("run-1"));

        assertEquals("权限不足", error.getMessage());
    }

    private static AgentRun run(String userId) {
        return new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-a",
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "input",
                AgentRunStatus.SUCCEEDED,
                "trace-1",
                100L,
                221L,
                BigDecimal.valueOf(0.42d),
                null,
                null,
                NOW,
                NOW.plusSeconds(30));
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, role + "-" + userId, role, null));
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {

        private final AgentRun run;

        private MemoryAgentRunRepository(AgentRun run) {
            this.run = run;
        }

        @Override
        public void createRun(AgentRun run) {
        }

        @Override
        public void updateRun(AgentRun run) {
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            if (run == null || !run.runId().equals(runId)) {
                return Optional.empty();
            }
            return Optional.of(run);
        }

        @Override
        public void appendStep(AgentStep step) {
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return List.of();
        }
    }

    private static final class CapturingCostUsageRepository implements CostUsageRepositoryPort {

        private CostUsageQuery lastQuery;

        @Override
        public CostUsageRecord append(CostUsageRecord record) {
            return record;
        }

        @Override
        public CostUsageAggregate aggregate(CostUsageQuery query) {
            this.lastQuery = query;
            return new CostUsageAggregate(
                    query.tenantId() == null ? AgentDefinition.DEFAULT_TENANT_ID : query.tenantId(),
                    query.agentId(),
                    query.runId(),
                    321L,
                    4L,
                    0.42d,
                    2L);
        }
    }
}
