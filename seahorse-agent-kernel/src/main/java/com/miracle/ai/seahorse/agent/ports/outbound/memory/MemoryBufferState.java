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
import java.util.Objects;

public record MemoryBufferState(
        String tenantId,
        String userId,
        String conversationId,
        String sessionId,
        int turnCount,
        int totalTokens,
        Instant lastActivityAt,
        boolean forceFlushRequired,
        MemoryFlushTrigger forceFlushTrigger
) {

    private static final String DEFAULT_TENANT_ID = "default";

    public MemoryBufferState {
        tenantId = normalize(tenantId, DEFAULT_TENANT_ID);
        userId = normalize(userId, "");
        conversationId = normalize(conversationId, "");
        sessionId = normalize(sessionId, conversationId);
        turnCount = Math.max(turnCount, 0);
        totalTokens = Math.max(totalTokens, 0);
        lastActivityAt = Objects.requireNonNullElseGet(lastActivityAt, Instant::now);
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        if (normalized.isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }
}
