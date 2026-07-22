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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessRequest;

import java.util.Map;
import java.util.Objects;

/**
 * Adapts Tool Gateway resource references to the canonical resource access policy.
 * Resource ownership and visibility are deliberately not accepted from tool callers.
 */
public class PolicyBackedToolResourceAccessPort implements ToolResourceAccessPort {

    private static final String EMPTY_ATTRIBUTES = "{}";

    private final ResourceAccessPolicyPort policy;

    public PolicyBackedToolResourceAccessPort(ResourceAccessPolicyPort policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public ToolResourceAccessDecision decide(ToolResourceAccessRequest request) {
        if (request == null) {
            return deny("Resource access request is missing");
        }
        if (!hasText(request.tenantId()) || !hasText(request.userId()) || !hasText(request.resourceType())) {
            return deny("Resource access request is incomplete");
        }
        ResourceAction action = resourceAction(request.actionType());
        if (action == null) {
            return deny("Resource action is not supported");
        }
        if (request.resourceRefs().isEmpty()) {
            return ToolResourceAccessDecision.allow();
        }
        if (request.resourceRefs().entrySet().stream()
                .anyMatch(entry -> !hasText(entry.getKey()) || !hasText(entry.getValue()))) {
            return deny("Resource reference is invalid");
        }

        for (String resourceId : distinctResourceIds(request.resourceRefs())) {
            ResourceAccessRequest policyRequest = new ResourceAccessRequest(
                    request.tenantId(),
                    AccessSubjectType.USER_DELEGATED_AGENT,
                    request.userId(),
                    action,
                    new ResourceRef(
                            request.resourceType(),
                            resourceId,
                            request.tenantId(),
                            null,
                            EMPTY_ATTRIBUTES));
            AccessDecision decision;
            try {
                decision = policy.decide(policyRequest);
            } catch (RuntimeException ex) {
                return deny("Resource access policy failed");
            }
            if (decision == null) {
                return deny("Resource access decision is missing");
            }
            if (!matches(policyRequest, decision)) {
                return deny("Resource access decision does not match request");
            }
            if (decision.effect() != AccessDecisionEffect.ALLOW) {
                return deny("Resource access denied");
            }
        }
        return ToolResourceAccessDecision.allow();
    }

    private boolean matches(ResourceAccessRequest request, AccessDecision decision) {
        ResourceRef resource = request.resourceRef();
        return request.tenantId().equals(decision.tenantId())
                && request.subjectType() == decision.subjectType()
                && request.subjectId().equals(decision.subjectId())
                && request.action() == decision.action()
                && resource.resourceType().equals(decision.resourceType())
                && resource.resourceId().equals(decision.resourceId());
    }

    private Iterable<String> distinctResourceIds(Map<String, String> resourceRefs) {
        return resourceRefs.values().stream()
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private ResourceAction resourceAction(ToolActionType actionType) {
        if (actionType == null) {
            return null;
        }
        return switch (actionType) {
            case READ -> ResourceAction.READ;
            case WRITE -> ResourceAction.WRITE;
            case DELETE -> ResourceAction.DELETE;
            case EXECUTE -> ResourceAction.EXECUTE;
            case EXTERNAL_SEND -> null;
        };
    }

    private ToolResourceAccessDecision deny(String reason) {
        return ToolResourceAccessDecision.deny(reason);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
