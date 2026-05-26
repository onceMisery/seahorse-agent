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

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public record ResourceAclRule(String ruleId,
                              String tenantId,
                              ResourceAclRuleScope scope,
                              String resourceType,
                              String resourceId,
                              AccessSubjectType subjectType,
                              String subjectId,
                              ResourceAction action,
                              AccessDecisionEffect effect,
                              ResourceAclRuleStatus status,
                              int priority,
                              Instant expiresAt,
                              String createdBy,
                              Instant createdAt,
                              Instant updatedAt) {

    public ResourceAclRule {
        ruleId = requireText(ruleId, "ruleId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        resourceType = requireText(resourceType, "resourceType must not be blank");
        resourceId = requireText(resourceId, "resourceId must not be blank");
        subjectType = Objects.requireNonNull(subjectType, "subjectType must not be null");
        subjectId = requireText(subjectId, "subjectId must not be blank");
        action = Objects.requireNonNull(action, "action must not be null");
        effect = Objects.requireNonNull(effect, "effect must not be null");
        if (effect == AccessDecisionEffect.MASK) {
            throw new IllegalArgumentException("effect must be ALLOW or DENY");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        createdBy = requireText(createdBy, "createdBy must not be blank");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean isEffectiveAt(Instant now) {
        Instant safeNow = Objects.requireNonNull(now, "now must not be null");
        return status == ResourceAclRuleStatus.ENABLED
                && scope == ResourceAclRuleScope.EXACT_RESOURCE
                && (expiresAt == null || expiresAt.isAfter(safeNow));
    }

    public boolean matches(ResourceAclLookup lookup, Instant now) {
        ResourceAclLookup safeLookup = Objects.requireNonNull(lookup, "lookup must not be null");
        return isEffectiveAt(now)
                && tenantId.equals(safeLookup.tenantId())
                && resourceType.equals(safeLookup.resourceType())
                && resourceId.equals(safeLookup.resourceId())
                && subjectType == safeLookup.subjectType()
                && subjectId.equals(safeLookup.subjectId())
                && action == safeLookup.action();
    }

    public ResourceAclRule disable(Instant updatedAt) {
        if (status == ResourceAclRuleStatus.DISABLED) {
            return this;
        }
        return new ResourceAclRule(
                ruleId,
                tenantId,
                scope,
                resourceType,
                resourceId,
                subjectType,
                subjectId,
                action,
                effect,
                ResourceAclRuleStatus.DISABLED,
                priority,
                expiresAt,
                createdBy,
                createdAt,
                Objects.requireNonNull(updatedAt, "updatedAt must not be null"));
    }

    public static Comparator<ResourceAclRule> effectiveOrder() {
        return Comparator
                .comparingInt(ResourceAclRule::effectRank)
                .thenComparing(Comparator.comparingInt(ResourceAclRule::priority).reversed())
                .thenComparing(ResourceAclRule::createdAt);
    }

    private int effectRank() {
        return effect == AccessDecisionEffect.DENY ? 0 : 1;
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
