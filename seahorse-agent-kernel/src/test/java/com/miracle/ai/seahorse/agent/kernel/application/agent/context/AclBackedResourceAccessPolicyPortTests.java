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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclLookup;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclNaturalKey;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRulePage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AclBackedResourceAccessPolicyPortTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldUsePersistedAclBeforeDefaultPolicy() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        repository.save(rule("rule-1", AccessDecisionEffect.ALLOW, 100, null));
        AclBackedResourceAccessPolicyPort policy = new AclBackedResourceAccessPolicyPort(
                repository,
                ResourceAccessPolicyPort.denyAll(),
                FIXED_CLOCK);

        AccessDecision decision = policy.decide(request("doc-private", "owner-2", "{}"));

        assertEquals(AccessDecisionEffect.ALLOW, decision.effect());
        assertEquals(ResourceAccessReasonCodes.RESOURCE_ACL_ALLOW, decision.reasonCode());
        assertEquals(NOW, decision.createdAt());
    }

    @Test
    void shouldPreferDenyWhenMultipleAclRulesMatch() {
        MemoryResourceAclRepository repository = new MemoryResourceAclRepository();
        repository.save(rule("rule-1", AccessDecisionEffect.ALLOW, 100, null));
        repository.save(rule("rule-2", AccessDecisionEffect.DENY, 100, null));
        AclBackedResourceAccessPolicyPort policy = new AclBackedResourceAccessPolicyPort(
                repository,
                new DefaultResourceAccessPolicyPort(FIXED_CLOCK),
                FIXED_CLOCK);

        AccessDecision decision = policy.decide(request("doc-private", "user-1", "{}"));

        assertEquals(AccessDecisionEffect.DENY, decision.effect());
        assertEquals(ResourceAccessReasonCodes.RESOURCE_ACL_DENY, decision.reasonCode());
    }

    @Test
    void shouldDelegateToDefaultPolicyWhenNoAclRuleMatches() {
        AclBackedResourceAccessPolicyPort policy = new AclBackedResourceAccessPolicyPort(
                new MemoryResourceAclRepository(),
                new DefaultResourceAccessPolicyPort(FIXED_CLOCK),
                FIXED_CLOCK);

        AccessDecision decision = policy.decide(request("doc-public", "owner-2", "{\"visibility\":\"public\"}"));

        assertEquals(AccessDecisionEffect.ALLOW, decision.effect());
        assertEquals(ResourceAccessReasonCodes.PUBLIC_RESOURCE, decision.reasonCode());
    }

    private static ResourceAccessRequest request(String resourceId,
                                                 String ownerUserId,
                                                 String attributesJson) {
        return new ResourceAccessRequest(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                new ResourceRef(ContextResourceType.DOCUMENT, resourceId, "tenant-1", ownerUserId, attributesJson));
    }

    private static ResourceAclRule rule(String ruleId,
                                        AccessDecisionEffect effect,
                                        int priority,
                                        Instant expiresAt) {
        return new ResourceAclRule(
                ruleId,
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                "doc-private",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                effect,
                ResourceAclRuleStatus.ENABLED,
                priority,
                expiresAt,
                "admin-1",
                NOW,
                NOW);
    }

    private static final class MemoryResourceAclRepository implements ResourceAclRepositoryPort {

        private final List<ResourceAclRule> rules = new ArrayList<>();

        @Override
        public ResourceAclRule save(ResourceAclRule rule) {
            rules.add(rule);
            return rule;
        }

        @Override
        public Optional<ResourceAclRule> findById(String ruleId) {
            return rules.stream().filter(rule -> rule.ruleId().equals(ruleId)).findFirst();
        }

        @Override
        public ResourceAclRulePage page(ResourceAclQuery query) {
            return new ResourceAclRulePage(rules, rules.size(), query.size(), query.current(), 1L);
        }

        @Override
        public List<ResourceAclRule> findEffective(ResourceAclLookup lookup) {
            return rules.stream()
                    .filter(rule -> rule.matches(lookup, NOW))
                    .sorted(ResourceAclRule.effectiveOrder().thenComparing(
                            Comparator.comparing(ResourceAclRule::ruleId)))
                    .toList();
        }

        @Override
        public List<ResourceAclRule> findByNaturalKey(ResourceAclNaturalKey naturalKey, Instant now) {
            return rules.stream()
                    .filter(naturalKey::matches)
                    .filter(rule -> rule.isEffectiveAt(now))
                    .toList();
        }
    }
}
