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

package com.miracle.ai.seahorse.agent.kernel.application.agent.quota;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaUsage;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaPolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelQuotaDecisionService implements QuotaManagementInboundPort {

    private static final List<QuotaScope> MATCH_ORDER = List.of(
            QuotaScope.RUN,
            QuotaScope.AGENT,
            QuotaScope.USER,
            QuotaScope.TOOL,
            QuotaScope.MODEL,
            QuotaScope.TENANT);

    private final QuotaPolicyRepositoryPort repository;
    private final Clock clock;

    public KernelQuotaDecisionService(QuotaPolicyRepositoryPort repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public QuotaPolicy upsertPolicy(QuotaPolicyUpsertCommand command) {
        QuotaPolicyUpsertCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        return repository.upsert(new QuotaPolicy(
                safeCommand.policyId(),
                safeCommand.tenantId(),
                safeCommand.scope(),
                safeCommand.subjectId(),
                safeCommand.status(),
                safeCommand.tokenLimit(),
                safeCommand.callLimit(),
                safeCommand.costLimit(),
                safeCommand.warnRatio() == null ? QuotaPolicyLimits.DEFAULT_WARN_RATIO : safeCommand.warnRatio(),
                safeCommand.createdAt() == null ? clock.instant() : safeCommand.createdAt(),
                safeCommand.updatedAt() == null ? clock.instant() : safeCommand.updatedAt()));
    }

    @Override
    public void disablePolicy(String policyId) {
        repository.disable(requireText(policyId, "policyId must not be blank"), clock.instant());
    }

    @Override
    public QuotaDecisionResult evaluate(QuotaDecisionCommand command) {
        QuotaDecisionCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        QuotaUsage requestedUsage = Objects.requireNonNull(safeCommand.requestedUsage(),
                "requestedUsage must not be null");
        Optional<QuotaPolicy> policy = findMatchingPolicy(safeCommand);
        if (policy.isEmpty()) {
            return noPolicyDecision(safeCommand.riskLevel(), requestedUsage);
        }
        QuotaPolicy matched = policy.orElseThrow();
        if (matched.status() == QuotaPolicyStatus.DISABLED) {
            return decision(
                    QuotaDecisionEffect.WARN,
                    QuotaDecisionReasonCode.POLICY_DISABLED,
                    matched.policyId(),
                    requestedUsage);
        }
        if (matched.exceededBy(requestedUsage)) {
            return decision(
                    QuotaDecisionEffect.DENY,
                    QuotaDecisionReasonCode.HARD_LIMIT_EXCEEDED,
                    matched.policyId(),
                    requestedUsage);
        }
        if (matched.warnThresholdReachedBy(requestedUsage)) {
            return decision(
                    QuotaDecisionEffect.WARN,
                    QuotaDecisionReasonCode.WARN_THRESHOLD_EXCEEDED,
                    matched.policyId(),
                    requestedUsage);
        }
        return decision(
                QuotaDecisionEffect.ALLOW,
                QuotaDecisionReasonCode.POLICY_MATCHED,
                matched.policyId(),
                requestedUsage);
    }

    public Optional<QuotaPolicy> findActiveForAgent(String tenantId, String agentId) {
        Optional<QuotaPolicy> agentPolicy = repository.findActive(
                requireText(tenantId, "tenantId must not be blank"),
                QuotaScope.AGENT,
                requireText(agentId, "agentId must not be blank"));
        if (agentPolicy.isPresent()) {
            return agentPolicy;
        }
        return repository.findActive(tenantId.trim(), QuotaScope.TENANT, tenantId.trim());
    }

    private Optional<QuotaPolicy> findMatchingPolicy(QuotaDecisionCommand command) {
        for (QuotaScope scope : MATCH_ORDER) {
            String subjectId = subjectId(command, scope);
            if (!hasText(subjectId)) {
                continue;
            }
            Optional<QuotaPolicy> policy = repository.findActive(command.tenantId(), scope, subjectId);
            if (policy.isPresent()) {
                return policy;
            }
        }
        return Optional.empty();
    }

    private String subjectId(QuotaDecisionCommand command, QuotaScope scope) {
        return switch (scope) {
            case RUN -> command.runId();
            case AGENT -> command.agentId();
            case USER -> command.userId();
            case TOOL -> command.toolId();
            case MODEL -> command.modelId();
            case TENANT -> command.tenantId();
        };
    }

    private QuotaDecisionResult noPolicyDecision(AgentRiskLevel riskLevel, QuotaUsage requestedUsage) {
        if (riskLevel == AgentRiskLevel.HIGH || riskLevel == AgentRiskLevel.CRITICAL) {
            return decision(
                    QuotaDecisionEffect.REQUIRE_APPROVAL,
                    QuotaDecisionReasonCode.NO_POLICY_HIGH_RISK,
                    null,
                    requestedUsage);
        }
        return decision(QuotaDecisionEffect.WARN, QuotaDecisionReasonCode.NO_POLICY_LOW_RISK, null, requestedUsage);
    }

    private QuotaDecisionResult decision(QuotaDecisionEffect effect,
                                         QuotaDecisionReasonCode reasonCode,
                                         String policyId,
                                         QuotaUsage requestedUsage) {
        return new QuotaDecisionResult(effect, reasonCode, policyId, requestedUsage, clock.instant());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
