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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class DefaultResourceAccessPolicyPort implements ResourceAccessPolicyPort {

    private static final String DECISION_ID_PREFIX = "acl_";
    private static final String ATTRIBUTE_VISIBILITY = "visibility";
    private static final String ATTRIBUTE_PUBLIC = "public";
    private static final TypeReference<Map<String, Object>> ATTRIBUTE_MAP_TYPE = new TypeReference<>() {
    };

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public DefaultResourceAccessPolicyPort() {
        this(Clock.systemUTC(), new ObjectMapper());
    }

    public DefaultResourceAccessPolicyPort(Clock clock) {
        this(clock, new ObjectMapper());
    }

    DefaultResourceAccessPolicyPort(Clock clock, ObjectMapper objectMapper) {
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public AccessDecision decide(ResourceAccessRequest request) {
        ResourceAccessRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        ResourceRef resource = safeRequest.resourceRef();
        if (!safeRequest.tenantId().equals(resource.tenantId())) {
            return deny(safeRequest, ResourceAccessReasonCodes.TENANT_MISMATCH);
        }
        if (safeRequest.action() != ResourceAction.READ) {
            return deny(safeRequest, ResourceAccessReasonCodes.READ_ONLY_POLICY);
        }
        if (safeRequest.subjectType() != AccessSubjectType.USER_DELEGATED_AGENT) {
            return deny(safeRequest, ResourceAccessReasonCodes.SUBJECT_NOT_SUPPORTED);
        }
        ContextResourceType resourceType = parseResourceType(resource.resourceType());
        if (resourceType == null) {
            return deny(safeRequest, ResourceAccessReasonCodes.RESOURCE_TYPE_NOT_SUPPORTED);
        }
        return switch (resourceType) {
            case USER_INPUT, MEMORY -> ownerOnly(safeRequest);
            case DOCUMENT -> document(safeRequest);
        };
    }

    private AccessDecision ownerOnly(ResourceAccessRequest request) {
        if (ownedBySubject(request)) {
            return allow(request, ResourceAccessReasonCodes.OWNER_MATCH);
        }
        return deny(request, ResourceAccessReasonCodes.OWNER_REQUIRED);
    }

    private AccessDecision document(ResourceAccessRequest request) {
        if (publicResource(request.resourceRef())) {
            return allow(request, ResourceAccessReasonCodes.PUBLIC_RESOURCE);
        }
        if (ownedBySubject(request)) {
            return allow(request, ResourceAccessReasonCodes.OWNER_MATCH);
        }
        return deny(request, ResourceAccessReasonCodes.PUBLIC_OR_OWNER_REQUIRED);
    }

    private boolean ownedBySubject(ResourceAccessRequest request) {
        return request.subjectId().equals(request.resourceRef().ownerUserId());
    }

    private boolean publicResource(ResourceRef resource) {
        Map<String, Object> attributes = attributes(resource);
        Object visibility = attributes.get(ATTRIBUTE_VISIBILITY);
        return visibility instanceof String value && ATTRIBUTE_PUBLIC.equalsIgnoreCase(value);
    }

    private Map<String, Object> attributes(ResourceRef resource) {
        try {
            return objectMapper.readValue(resource.attributesJson(), ATTRIBUTE_MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private ContextResourceType parseResourceType(String resourceType) {
        try {
            return ContextResourceType.valueOf(resourceType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AccessDecision allow(ResourceAccessRequest request, String reasonCode) {
        return decision(request, AccessDecisionEffect.ALLOW, reasonCode);
    }

    private AccessDecision deny(ResourceAccessRequest request, String reasonCode) {
        return decision(request, AccessDecisionEffect.DENY, reasonCode);
    }

    private AccessDecision decision(ResourceAccessRequest request,
                                    AccessDecisionEffect effect,
                                    String reasonCode) {
        ResourceRef resource = request.resourceRef();
        Instant now = clock.instant();
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
}
