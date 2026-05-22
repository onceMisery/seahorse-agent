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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MemoryTraceEvent(
        String traceId,
        String tenantId,
        String userId,
        String conversationId,
        String sessionId,
        String component,
        String eventType,
        String status,
        String subjectId,
        String subjectType,
        Map<String, Object> details,
        Instant occurredAt) {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_IGNORED = "IGNORED";

    public MemoryTraceEvent {
        traceId = normalize(traceId, "");
        tenantId = normalize(tenantId, "default");
        userId = normalize(userId, "");
        conversationId = normalize(conversationId, "");
        sessionId = normalize(sessionId, "");
        component = normalize(component, "memory");
        eventType = normalize(eventType, "event");
        status = normalize(status, "");
        subjectId = normalize(subjectId, "");
        subjectType = normalize(subjectType, "memory");
        details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
        occurredAt = Objects.requireNonNullElseGet(occurredAt, Instant::now);
    }

    public MemoryTraceEvent(String component,
                            String eventType,
                            String status,
                            String userId,
                            String tenantId,
                            String subjectId,
                            Map<String, Object> details,
                            Instant occurredAt) {
        this("",
                tenantId,
                userId,
                "",
                "",
                component,
                eventType,
                status,
                subjectId,
                "memory",
                details,
                occurredAt);
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}
