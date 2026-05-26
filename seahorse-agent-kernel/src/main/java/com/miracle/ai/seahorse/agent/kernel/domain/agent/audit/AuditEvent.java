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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.audit;

import java.time.Instant;
import java.util.Objects;

public record AuditEvent(String auditId,
                         String tenantId,
                         AuditEventType eventType,
                         AuditActorType actorType,
                         String actorId,
                         String runId,
                         String agentId,
                         String resourceType,
                         String resourceId,
                         String redactedPayload,
                         Instant occurredAt) {

    public static final String EMPTY_PAYLOAD = "{}";

    public AuditEvent {
        auditId = requireText(auditId, "auditId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        actorId = requireText(actorId, "actorId must not be blank");
        runId = trimToNull(runId);
        agentId = trimToNull(agentId);
        resourceType = trimToNull(resourceType);
        resourceId = trimToNull(resourceId);
        redactedPayload = defaultPayload(redactedPayload);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public AuditEvent withRedactedPayload(String payload) {
        return new AuditEvent(
                auditId,
                tenantId,
                eventType,
                actorType,
                actorId,
                runId,
                agentId,
                resourceType,
                resourceId,
                payload,
                occurredAt);
    }

    private static String defaultPayload(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? EMPTY_PAYLOAD : trimmed;
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
