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

package com.miracle.ai.seahorse.agent.kernel.application.agent.rollout;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutCostSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelAgentRolloutCostSummaryServiceTests {

    private static final Instant STARTED_AT = Instant.parse("2026-05-26T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-05-26T00:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldAggregateRunningRolloutCostWithinRolloutWindow() {
        MemoryRolloutRepository rolloutRepository = new MemoryRolloutRepository();
        rolloutRepository.records.put("rollout-1", rollout("rollout-1"));
        RecordingCostUsageRepository costUsageRepository = new RecordingCostUsageRepository(
                new CostUsageAggregate("tenant-1", "agent-1", null, 1200, 9, 0.42, 3));
        KernelAgentRolloutCostSummaryService service =
                new KernelAgentRolloutCostSummaryService(rolloutRepository, costUsageRepository, CLOCK);

        AgentRolloutCostSummary summary = service.getCostSummary("tenant-1", "agent-1", "rollout-1");

        assertEquals("rollout-1", summary.rolloutId());
        assertEquals("version-1", summary.versionId());
        assertEquals(STARTED_AT, summary.windowFrom());
        assertEquals(NOW, summary.windowTo());
        assertEquals("AGENT_ROLLOUT_ID", summary.aggregationScope());
        assertEquals(1200, summary.totalTokens());
        assertEquals(9, summary.totalCalls());
        assertEquals(0.42, summary.totalCost());
        assertEquals(3, summary.recordCount());
        assertEquals(new CostUsageQuery("tenant-1", "agent-1", null, "rollout-1", STARTED_AT, NOW),
                costUsageRepository.lastQuery);
    }

    @Test
    void shouldIncludeRolloutRunHealthAndPendingApprovalCounts() {
        MemoryRolloutRepository rolloutRepository = new MemoryRolloutRepository();
        rolloutRepository.records.put("rollout-1", rollout("rollout-1"));
        RecordingCostUsageRepository costUsageRepository = new RecordingCostUsageRepository(
                new CostUsageAggregate("tenant-1", "agent-1", null, 1200, 9, 0.42, 3));
        RecordingAgentRunRepository runRepository = new RecordingAgentRunRepository(Map.of(
                AgentRunStatus.SUCCEEDED.name(), 7L,
                AgentRunStatus.FAILED.name(), 2L,
                AgentRunStatus.WAITING_APPROVAL.name(), 1L));
        RecordingApprovalRequestQueryPort approvalQueryPort = new RecordingApprovalRequestQueryPort(4L);
        KernelAgentRolloutCostSummaryService service = new KernelAgentRolloutCostSummaryService(
                rolloutRepository,
                costUsageRepository,
                runRepository,
                approvalQueryPort,
                CLOCK);

        AgentRolloutCostSummary summary = service.getCostSummary("tenant-1", "agent-1", "rollout-1");

        assertEquals(10L, summary.totalRuns());
        assertEquals(7L, summary.succeededRuns());
        assertEquals(2L, summary.failedRuns());
        assertEquals(1L, summary.waitingApprovalRuns());
        assertEquals(0.2, summary.errorRate());
        assertEquals(4L, summary.pendingApprovalCount());
        assertEquals(7L, summary.runStatusCounts().get(AgentRunStatus.SUCCEEDED.name()));
        assertEquals(new AgentRunQuery(
                        "tenant-1",
                        "agent-1",
                        null,
                        "rollout-1",
                        AgentRunStatus.FAILED.name(),
                        STARTED_AT,
                        NOW,
                        1L,
                        1L),
                runRepository.queries.stream()
                        .filter(query -> AgentRunStatus.FAILED.name().equals(query.status()))
                        .findFirst()
                        .orElseThrow());
        assertEquals(new ApprovalRequestQuery(
                        "tenant-1",
                        null,
                        "agent-1",
                        "rollout-1",
                        ApprovalRequestStatus.PENDING,
                        STARTED_AT,
                        NOW,
                        1L,
                        1L),
                approvalQueryPort.lastQuery);
    }

    @Test
    void shouldUseExactRolloutIdAttributionForCostRunsAndApprovals() {
        MemoryRolloutRepository rolloutRepository = new MemoryRolloutRepository();
        rolloutRepository.records.put("rollout-1", rollout("rollout-1"));
        RecordingCostUsageRepository costUsageRepository = new RecordingCostUsageRepository(
                new CostUsageAggregate("tenant-1", "agent-1", null, 80, 2, 0.08, 1));
        RecordingAgentRunRepository runRepository = new RecordingAgentRunRepository(Map.of(
                AgentRunStatus.SUCCEEDED.name(), 1L));
        RecordingApprovalRequestQueryPort approvalQueryPort = new RecordingApprovalRequestQueryPort(1L);
        KernelAgentRolloutCostSummaryService service = new KernelAgentRolloutCostSummaryService(
                rolloutRepository,
                costUsageRepository,
                runRepository,
                approvalQueryPort,
                CLOCK);

        AgentRolloutCostSummary summary = service.getCostSummary("tenant-1", "agent-1", "rollout-1");

        assertEquals("AGENT_ROLLOUT_ID", summary.aggregationScope());
        assertEquals("rollout-1", costUsageRepository.lastQuery.rolloutId());
        assertEquals("rollout-1", runRepository.queries.stream()
                .filter(query -> AgentRunStatus.SUCCEEDED.name().equals(query.status()))
                .findFirst()
                .orElseThrow()
                .rolloutId());
        assertEquals("rollout-1", approvalQueryPort.lastQuery.rolloutId());
    }

    private static AgentVersionRollout rollout(String rolloutId) {
        return new AgentVersionRollout(
                rolloutId,
                "tenant-1",
                "agent-1",
                "version-1",
                AgentRolloutLimits.DEFAULT_CANARY_PERCENT,
                AgentRolloutStatus.RUNNING,
                null,
                null,
                "operator-1",
                STARTED_AT,
                STARTED_AT,
                null);
    }

    private static final class MemoryRolloutRepository implements AgentRolloutRepositoryPort {

        private final Map<String, AgentVersionRollout> records = new LinkedHashMap<>();

        @Override
        public AgentVersionRollout save(AgentVersionRollout rollout) {
            records.put(rollout.rolloutId(), rollout);
            return rollout;
        }

        @Override
        public Optional<AgentVersionRollout> findById(String rolloutId) {
            return Optional.ofNullable(records.get(rolloutId));
        }

        @Override
        public Optional<AgentVersionRollout> findLatest(String tenantId, String agentId, String versionId) {
            return records.values().stream()
                    .filter(rollout -> rollout.tenantId().equals(tenantId))
                    .filter(rollout -> rollout.agentId().equals(agentId))
                    .filter(rollout -> rollout.versionId().equals(versionId))
                    .findFirst();
        }
    }

    private static final class RecordingCostUsageRepository implements CostUsageRepositoryPort {

        private final CostUsageAggregate aggregate;
        private CostUsageQuery lastQuery;

        private RecordingCostUsageRepository(CostUsageAggregate aggregate) {
            this.aggregate = aggregate;
        }

        @Override
        public CostUsageRecord append(CostUsageRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CostUsageAggregate aggregate(CostUsageQuery query) {
            lastQuery = query;
            return aggregate;
        }
    }

    private static final class RecordingAgentRunRepository implements AgentRunRepositoryPort {

        private final Map<String, Long> totalsByStatus;
        private final List<AgentRunQuery> queries = new ArrayList<>();

        private RecordingAgentRunRepository(Map<String, Long> totalsByStatus) {
            this.totalsByStatus = totalsByStatus;
        }

        @Override
        public void createRun(AgentRun run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRun(AgentRun run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRunPage page(AgentRunQuery query) {
            queries.add(query);
            long total = totalsByStatus.getOrDefault(query.status(), 0L);
            return new AgentRunPage(List.of(), total, query.size(), query.current(), total > 0 ? 1L : 0L);
        }

        @Override
        public void appendStep(com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep step) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> listSteps(String runId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingApprovalRequestQueryPort implements ApprovalRequestQueryPort {

        private final long total;
        private ApprovalRequestQuery lastQuery;

        private RecordingApprovalRequestQueryPort(long total) {
            this.total = total;
        }

        @Override
        public Optional<ApprovalRequest> findById(String approvalId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApprovalRequestPage page(ApprovalRequestQuery query) {
            lastQuery = query;
            return new ApprovalRequestPage(List.of(), total, query.size(), query.current(), total > 0 ? 1L : 0L);
        }
    }
}
