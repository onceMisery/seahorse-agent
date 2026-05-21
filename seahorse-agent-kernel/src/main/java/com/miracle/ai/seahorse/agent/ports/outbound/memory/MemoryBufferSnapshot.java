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
import java.util.List;
import java.util.Objects;

public record MemoryBufferSnapshot(
        String snapshotId,
        String tenantId,
        String userId,
        String conversationId,
        String sessionId,
        MemoryFlushTrigger trigger,
        List<MemoryTurnEvent> turns,
        int totalTokens,
        Instant from,
        Instant to
) {

    private static final String DEFAULT_TENANT_ID = "default";

    public MemoryBufferSnapshot {
        snapshotId = normalize(snapshotId, "");
        tenantId = normalize(tenantId, DEFAULT_TENANT_ID);
        userId = normalize(userId, "");
        conversationId = normalize(conversationId, "");
        sessionId = normalize(sessionId, conversationId);
        trigger = Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL);
        turns = List.copyOf(Objects.requireNonNullElse(turns, List.of()));
        totalTokens = Math.max(totalTokens, 0);
        from = Objects.requireNonNullElseGet(from, Instant::now);
        to = Objects.requireNonNullElse(to, from);
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        if (normalized.isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }
}
