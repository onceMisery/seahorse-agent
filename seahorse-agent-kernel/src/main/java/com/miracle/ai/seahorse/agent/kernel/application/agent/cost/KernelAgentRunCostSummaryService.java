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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunCostSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.Objects;

public class KernelAgentRunCostSummaryService implements AgentRunCostSummaryInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "权限不足";
    private static final String RUN_NOT_FOUND = "Agent run not found";

    private final AgentRunRepositoryPort runRepository;
    private final CostUsageRepositoryPort costUsageRepository;
    private final CurrentUserPort currentUserPort;

    public KernelAgentRunCostSummaryService(AgentRunRepositoryPort runRepository,
                                            CostUsageRepositoryPort costUsageRepository,
                                            CurrentUserPort currentUserPort) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.costUsageRepository = Objects.requireNonNull(costUsageRepository, "costUsageRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public CostUsageAggregate getCostSummary(String runId) {
        String safeRunId = requireText(runId, "runId must not be blank");
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        AgentRun run = runRepository.findRunById(safeRunId)
                .orElseThrow(() -> new IllegalArgumentException(RUN_NOT_FOUND));
        requireReadable(run, currentUser);
        return costUsageRepository.aggregate(new CostUsageQuery(
                run.tenantId(),
                run.agentId(),
                run.runId(),
                null,
                null));
    }

    private void requireReadable(AgentRun run, CurrentUser currentUser) {
        if (isAdmin(currentUser) || run.userId().equals(currentUserId(currentUser))) {
            return;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null ? null : currentUser.operator();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
