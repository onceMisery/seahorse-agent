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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclLookup;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRule;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class AclBackedResourceAccessPolicyPort implements ResourceAccessPolicyPort {

    private static final String DECISION_ID_PREFIX = "acl_rule_";

    private final ResourceAclRepositoryPort repository;
    private final ResourceAccessPolicyPort delegate;
    private final Clock clock;

    public AclBackedResourceAccessPolicyPort(ResourceAclRepositoryPort repository,
                                             ResourceAccessPolicyPort delegate,
                                             Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public AccessDecision decide(ResourceAccessRequest request) {
        ResourceAccessRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        ResourceRef resource = safeRequest.resourceRef();
        List<ResourceAclRule> rules = repository.findEffective(new ResourceAclLookup(
                safeRequest.tenantId(),
                resource.resourceType(),
                resource.resourceId(),
                safeRequest.subjectType(),
                safeRequest.subjectId(),
                safeRequest.action(),
                now));
        if (rules.isEmpty()) {
            return delegate.decide(safeRequest);
        }
        ResourceAclRule rule = rules.stream()
                .sorted(ResourceAclRule.effectiveOrder())
                .findFirst()
                .orElseThrow();
        return decision(safeRequest, rule.effect(), reasonCode(rule.effect()), now);
    }

    private AccessDecision decision(ResourceAccessRequest request,
                                    AccessDecisionEffect effect,
                                    String reasonCode,
                                    Instant now) {
        ResourceRef resource = request.resourceRef();
        return new AccessDecision(
                DECISION_ID_PREFIX + SnowflakeIds.nextIdString(),
                request.tenantId(),
                request.subjectType(),
                request.subjectId(),
                request.action(),
                resource.resourceType(),
                resource.resourceId(),
                effect,
                reasonCode,
                now);
    }

    private String reasonCode(AccessDecisionEffect effect) {
        return effect == AccessDecisionEffect.DENY
                ? ResourceAccessReasonCodes.RESOURCE_ACL_DENY
                : ResourceAccessReasonCodes.RESOURCE_ACL_ALLOW;
    }
}
