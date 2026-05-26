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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAclRuleTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");

    @Test
    void shouldRejectInvalidAclRuleState() {
        IllegalArgumentException blankRuleId = assertThrows(IllegalArgumentException.class,
                () -> rule(" ", AccessDecisionEffect.ALLOW, ResourceAclRuleStatus.ENABLED, 100, null));
        IllegalArgumentException unsupportedEffect = assertThrows(IllegalArgumentException.class,
                () -> rule("rule-1", AccessDecisionEffect.MASK, ResourceAclRuleStatus.ENABLED, 100, null));

        assertEquals("ruleId must not be blank", blankRuleId.getMessage());
        assertEquals("effect must be ALLOW or DENY", unsupportedEffect.getMessage());
    }

    @Test
    void shouldOnlyTreatEnabledUnexpiredExactResourceRulesAsEffective() {
        ResourceAclRule active = rule("rule-1", AccessDecisionEffect.ALLOW,
                ResourceAclRuleStatus.ENABLED, 100, NOW.plusSeconds(60));
        ResourceAclRule expired = rule("rule-2", AccessDecisionEffect.ALLOW,
                ResourceAclRuleStatus.ENABLED, 100, NOW.minusSeconds(1));
        ResourceAclRule disabled = rule("rule-3", AccessDecisionEffect.ALLOW,
                ResourceAclRuleStatus.DISABLED, 100, null);

        assertTrue(active.isEffectiveAt(NOW));
        assertFalse(expired.isEffectiveAt(NOW));
        assertFalse(disabled.isEffectiveAt(NOW));
    }

    @Test
    void shouldSortEffectiveRulesByDenyThenPriorityThenCreatedAt() {
        ResourceAclRule lowPriorityDeny = rule("rule-1", AccessDecisionEffect.DENY,
                ResourceAclRuleStatus.ENABLED, 10, null);
        ResourceAclRule highPriorityAllow = rule("rule-2", AccessDecisionEffect.ALLOW,
                ResourceAclRuleStatus.ENABLED, 100, null);
        ResourceAclRule highPriorityDeny = rule("rule-3", AccessDecisionEffect.DENY,
                ResourceAclRuleStatus.ENABLED, 100, null);
        ResourceAclRule olderHighPriorityDeny = new ResourceAclRule(
                "rule-4",
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                "doc-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                AccessDecisionEffect.DENY,
                ResourceAclRuleStatus.ENABLED,
                100,
                null,
                "admin-1",
                NOW.minusSeconds(5),
                NOW.minusSeconds(5));

        List<ResourceAclRule> sorted = List.of(lowPriorityDeny, highPriorityAllow,
                highPriorityDeny, olderHighPriorityDeny).stream()
                .sorted(ResourceAclRule.effectiveOrder())
                .toList();

        assertEquals(List.of("rule-4", "rule-3", "rule-1", "rule-2"),
                sorted.stream().map(ResourceAclRule::ruleId).toList());
    }

    @Test
    void shouldDisableRuleIdempotently() {
        ResourceAclRule active = rule("rule-1", AccessDecisionEffect.ALLOW,
                ResourceAclRuleStatus.ENABLED, 100, null);

        ResourceAclRule disabled = active.disable(NOW.plusSeconds(10));
        ResourceAclRule disabledAgain = disabled.disable(NOW.plusSeconds(20));

        assertEquals(ResourceAclRuleStatus.DISABLED, disabled.status());
        assertEquals(NOW.plusSeconds(10), disabled.updatedAt());
        assertEquals(disabled, disabledAgain);
    }

    private static ResourceAclRule rule(String ruleId,
                                        AccessDecisionEffect effect,
                                        ResourceAclRuleStatus status,
                                        int priority,
                                        Instant expiresAt) {
        return new ResourceAclRule(
                ruleId,
                "tenant-1",
                ResourceAclRuleScope.EXACT_RESOURCE,
                ContextResourceType.DOCUMENT.value(),
                "doc-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                effect,
                status,
                priority,
                expiresAt,
                "admin-1",
                NOW,
                NOW);
    }
}
