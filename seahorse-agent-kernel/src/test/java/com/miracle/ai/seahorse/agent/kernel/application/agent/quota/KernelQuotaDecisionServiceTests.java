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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaUsage;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelQuotaDecisionServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldNotSilentlyAllowWhenNoActivePolicyExists() {
        KernelQuotaDecisionService service = new KernelQuotaDecisionService(new MemoryQuotaPolicyRepository(), CLOCK);

        var lowRisk = service.evaluate(command(AgentRiskLevel.LOW, usage(10, 1, 0.1d)));
        var highRisk = service.evaluate(command(AgentRiskLevel.HIGH, usage(10, 1, 0.1d)));

        assertEquals(QuotaDecisionEffect.WARN, lowRisk.effect());
        assertEquals(QuotaDecisionReasonCode.NO_POLICY_LOW_RISK, lowRisk.reasonCode());
        assertEquals(QuotaDecisionEffect.REQUIRE_APPROVAL, highRisk.effect());
        assertEquals(QuotaDecisionReasonCode.NO_POLICY_HIGH_RISK, highRisk.reasonCode());
    }

    @Test
    void shouldDenyHardLimitAndWarnThresholdBeforeAllowing() {
        MemoryQuotaPolicyRepository repository = new MemoryQuotaPolicyRepository();
        repository.upsert(policy("policy-1", QuotaScope.AGENT, "agent-1", 100L, 10L, 10.0d));
        KernelQuotaDecisionService service = new KernelQuotaDecisionService(repository, CLOCK);

        var denied = service.evaluate(command(AgentRiskLevel.MEDIUM, usage(120, 1, 0.1d)));
        var warned = service.evaluate(command(AgentRiskLevel.MEDIUM, usage(85, 1, 0.1d)));
        var allowed = service.evaluate(command(AgentRiskLevel.MEDIUM, usage(20, 1, 0.1d)));

        assertEquals(QuotaDecisionEffect.DENY, denied.effect());
        assertEquals(QuotaDecisionReasonCode.HARD_LIMIT_EXCEEDED, denied.reasonCode());
        assertEquals("policy-1", denied.policyId());
        assertEquals(QuotaDecisionEffect.WARN, warned.effect());
        assertEquals(QuotaDecisionReasonCode.WARN_THRESHOLD_EXCEEDED, warned.reasonCode());
        assertEquals(QuotaDecisionEffect.ALLOW, allowed.effect());
        assertEquals(QuotaDecisionReasonCode.POLICY_MATCHED, allowed.reasonCode());
    }

    private static QuotaDecisionCommand command(AgentRiskLevel riskLevel, QuotaUsage requestedUsage) {
        return new QuotaDecisionCommand(
                "tenant-a",
                "agent-1",
                "user-1",
                "tool-1",
                "model-1",
                "run-1",
                riskLevel,
                requestedUsage);
    }

    private static QuotaUsage usage(long tokens, long calls, double cost) {
        return new QuotaUsage(tokens, calls, cost);
    }

    private static QuotaPolicy policy(String policyId,
                                      QuotaScope scope,
                                      String subjectId,
                                      Long tokenLimit,
                                      Long callLimit,
                                      Double costLimit) {
        return new QuotaPolicy(
                policyId,
                "tenant-a",
                scope,
                subjectId,
                QuotaPolicyStatus.ACTIVE,
                tokenLimit,
                callLimit,
                costLimit,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW);
    }

    private static final class MemoryQuotaPolicyRepository implements QuotaPolicyRepositoryPort {

        private final List<QuotaPolicy> policies = new ArrayList<>();

        @Override
        public QuotaPolicy upsert(QuotaPolicy policy) {
            policies.removeIf(existing -> existing.policyId().equals(policy.policyId()));
            policies.add(policy);
            return policy;
        }

        @Override
        public Optional<QuotaPolicy> findActive(String tenantId, QuotaScope scope, String subjectId) {
            return policies.stream()
                    .filter(policy -> policy.tenantId().equals(tenantId))
                    .filter(policy -> policy.scope() == scope)
                    .filter(policy -> policy.subjectId().equals(subjectId))
                    .filter(policy -> policy.status() == QuotaPolicyStatus.ACTIVE)
                    .max(Comparator.comparing(QuotaPolicy::updatedAt).thenComparing(QuotaPolicy::policyId));
        }

        @Override
        public void disable(String policyId, Instant updatedAt) {
            policies.replaceAll(policy -> policy.policyId().equals(policyId)
                    ? policy.disable(updatedAt)
                    : policy);
        }
    }
}
