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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutCostSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCostSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class KernelAgentRolloutCostSummaryService implements AgentRolloutCostSummaryInboundPort {

    public static final String AGGREGATION_SCOPE_AGENT_ROLLOUT_WINDOW = "AGENT_ROLLOUT_WINDOW";
    public static final String AGGREGATION_SCOPE_AGENT_ROLLOUT_ID = "AGENT_ROLLOUT_ID";

    private final AgentRolloutRepositoryPort rolloutRepository;
    private final CostUsageRepositoryPort costUsageRepository;
    private final AgentRunRepositoryPort runRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final Clock clock;

    public KernelAgentRolloutCostSummaryService(AgentRolloutRepositoryPort rolloutRepository,
                                                CostUsageRepositoryPort costUsageRepository,
                                                Clock clock) {
        this(rolloutRepository, costUsageRepository, null, null, clock);
    }

    public KernelAgentRolloutCostSummaryService(AgentRolloutRepositoryPort rolloutRepository,
                                                CostUsageRepositoryPort costUsageRepository,
                                                AgentRunRepositoryPort runRepository,
                                                ApprovalRequestQueryPort approvalQueryPort,
                                                Clock clock) {
        this.rolloutRepository = Objects.requireNonNull(rolloutRepository, "rolloutRepository must not be null");
        this.costUsageRepository = Objects.requireNonNull(costUsageRepository, "costUsageRepository must not be null");
        this.runRepository = runRepository;
        this.approvalQueryPort = approvalQueryPort;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public AgentRolloutCostSummary getCostSummary(String tenantId, String agentId, String rolloutId) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        String safeAgentId = requireText(agentId, "agentId must not be blank");
        AgentVersionRollout rollout = rolloutRepository.findById(requireText(rolloutId, "rolloutId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("rollout not found"));
        if (!safeTenantId.equals(rollout.tenantId()) || !safeAgentId.equals(rollout.agentId())) {
            throw new IllegalArgumentException("rollout scope does not match query");
        }
        Instant windowFrom = rollout.startedAt();
        Instant windowTo = rollout.finishedAt() == null ? clock.instant() : rollout.finishedAt();
        if (windowTo.isBefore(windowFrom)) {
            windowTo = windowFrom;
        }
        CostUsageAggregate aggregate = costUsageRepository.aggregate(new CostUsageQuery(
                rollout.tenantId(),
                rollout.agentId(),
                null,
                rollout.rolloutId(),
                windowFrom,
                windowTo));
        Map<String, Long> statusCounts = runStatusCounts(rollout, windowFrom, windowTo);
        long totalRuns = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long succeededRuns = statusCounts.getOrDefault(AgentRunStatus.SUCCEEDED.name(), 0L);
        long failedRuns = statusCounts.getOrDefault(AgentRunStatus.FAILED.name(), 0L);
        long waitingApprovalRuns = statusCounts.getOrDefault(AgentRunStatus.WAITING_APPROVAL.name(), 0L);
        double errorRate = totalRuns == 0L ? 0.0 : (double) failedRuns / (double) totalRuns;
        long pendingApprovalCount = pendingApprovalCount(rollout, windowFrom, windowTo);
        return new AgentRolloutCostSummary(
                rollout.rolloutId(),
                rollout.tenantId(),
                rollout.agentId(),
                rollout.versionId(),
                windowFrom,
                windowTo,
                AGGREGATION_SCOPE_AGENT_ROLLOUT_ID,
                aggregate.totalTokens(),
                aggregate.totalCalls(),
                aggregate.totalCost(),
                aggregate.recordCount(),
                statusCounts,
                totalRuns,
                succeededRuns,
                failedRuns,
                waitingApprovalRuns,
                errorRate,
                pendingApprovalCount);
    }

    private Map<String, Long> runStatusCounts(AgentVersionRollout rollout, Instant windowFrom, Instant windowTo) {
        if (runRepository == null) {
            return Map.of();
        }
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (AgentRunStatus status : AgentRunStatus.values()) {
            long total = runRepository.page(new AgentRunQuery(
                    rollout.tenantId(),
                    rollout.agentId(),
                    null,
                    rollout.rolloutId(),
                    status.name(),
                    windowFrom,
                    windowTo,
                    1L,
                    1L)).total();
            statusCounts.put(status.name(), total);
        }
        return statusCounts;
    }

    private long pendingApprovalCount(AgentVersionRollout rollout, Instant windowFrom, Instant windowTo) {
        if (approvalQueryPort == null) {
            return 0L;
        }
        return approvalQueryPort.page(new ApprovalRequestQuery(
                rollout.tenantId(),
                null,
                rollout.agentId(),
                rollout.rolloutId(),
                ApprovalRequestStatus.PENDING,
                windowFrom,
                windowTo,
                1L,
                1L)).total();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
