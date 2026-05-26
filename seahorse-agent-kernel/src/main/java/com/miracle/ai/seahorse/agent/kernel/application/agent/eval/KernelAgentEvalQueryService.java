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

package com.miracle.ai.seahorse.agent.kernel.application.agent.eval;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummaryPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummaryHistoryQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummarySaveCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentEvalQueryService implements AgentEvalInboundPort {

    private final AgentEvalSummaryRepositoryPort repository;
    private final Clock clock;

    public KernelAgentEvalQueryService(AgentEvalSummaryRepositoryPort repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public AgentEvalSummary saveSummary(AgentEvalSummarySaveCommand command) {
        AgentEvalSummarySaveCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentEvalSummary summary = new AgentEvalSummary(
                safeCommand.summaryId(),
                safeCommand.tenantId(),
                safeCommand.agentId(),
                safeCommand.versionId(),
                safeCommand.evalType(),
                safeCommand.status(),
                safeCommand.score(),
                safeCommand.passThreshold(),
                safeCommand.warnThreshold(),
                safeCommand.caseCount(),
                safeCommand.datasetRef(),
                safeCommand.evalRunRef(),
                safeCommand.evidenceRefs(),
                safeCommand.createdBy(),
                safeCommand.createdAt() == null ? clock.instant() : safeCommand.createdAt());
        return repository.append(summary);
    }

    @Override
    public Optional<AgentEvalSummary> latestSummary(String tenantId,
                                                    String agentId,
                                                    String versionId,
                                                    AgentEvalType evalType) {
        return repository.findLatest(
                requireText(tenantId, "tenantId must not be blank"),
                requireText(agentId, "agentId must not be blank"),
                requireText(versionId, "versionId must not be blank"),
                Objects.requireNonNull(evalType, "evalType must not be null"));
    }

    @Override
    public AgentEvalSummaryPage history(AgentEvalSummaryHistoryQuery query) {
        AgentEvalSummaryHistoryQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        return repository.findHistory(new AgentEvalSummaryQuery(
                safeQuery.tenantId(),
                safeQuery.agentId(),
                safeQuery.versionId(),
                safeQuery.evalType(),
                safeQuery.current(),
                safeQuery.size()));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
