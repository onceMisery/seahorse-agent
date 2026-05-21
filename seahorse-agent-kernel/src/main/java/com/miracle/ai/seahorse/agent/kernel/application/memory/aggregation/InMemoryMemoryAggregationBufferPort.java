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

package com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class InMemoryMemoryAggregationBufferPort implements MemoryAggregationBufferPort {

    private static final String DEFAULT_TENANT_ID = "default";

    private final MemoryAggregationPolicy policy;
    private final Map<String, MutableBuffer> buffers = new LinkedHashMap<>();

    public InMemoryMemoryAggregationBufferPort(MemoryAggregationPolicy policy) {
        this.policy = Objects.requireNonNullElseGet(policy, MemoryAggregationPolicy::defaults);
    }

    @Override
    public synchronized MemoryBufferState appendTurn(MemoryTurnEvent event) {
        MemoryTurnEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        String key = key(safeEvent.sessionId(), safeEvent.tenantId());
        MutableBuffer buffer = buffers.computeIfAbsent(key, ignored -> new MutableBuffer(safeEvent));
        buffer.append(safeEvent);
        return buffer.state(policy);
    }

    @Override
    public synchronized Optional<MemoryBufferSnapshot> flushReady(String sessionId,
                                                                  String tenantId,
                                                                  MemoryFlushTrigger trigger,
                                                                  Instant now) {
        String key = key(sessionId, tenantId);
        MutableBuffer buffer = buffers.get(key);
        if (buffer == null || buffer.turns.isEmpty()) {
            return Optional.empty();
        }
        Instant safeNow = Objects.requireNonNullElseGet(now, Instant::now);
        MemoryFlushTrigger safeTrigger = Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL);
        if (!isReady(buffer, safeTrigger, safeNow)) {
            return Optional.empty();
        }
        buffers.remove(key);
        return Optional.of(buffer.snapshot(safeTrigger));
    }

    @Override
    public synchronized Optional<MemoryBufferState> state(String sessionId, String tenantId) {
        MutableBuffer buffer = buffers.get(key(sessionId, tenantId));
        return buffer == null ? Optional.empty() : Optional.of(buffer.state(policy));
    }

    @Override
    public synchronized List<MemoryBufferState> listStates(int limit) {
        int safeLimit = limit <= 0 ? policy.maxContextBlocks() : limit;
        return buffers.values().stream()
                .map(buffer -> buffer.state(policy))
                .sorted(Comparator.comparing(MemoryBufferState::lastActivityAt))
                .limit(safeLimit)
                .toList();
    }

    private boolean isReady(MutableBuffer buffer, MemoryFlushTrigger trigger, Instant now) {
        return switch (trigger) {
            case IDLE_TIMEOUT -> !now.isBefore(buffer.lastActivityAt.plusMillis(policy.idleFlushMillis()));
            case FORCE_TURNS -> buffer.turns.size() >= policy.maxTurns();
            case FORCE_TOKENS -> buffer.totalTokens >= policy.maxTokens();
            case MANUAL, SESSION_CLOSED -> true;
        };
    }

    private String key(String sessionId, String tenantId) {
        String safeTenant = normalize(tenantId, DEFAULT_TENANT_ID);
        return safeTenant + ":" + normalize(sessionId, "");
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        if (normalized.isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }

    private static final class MutableBuffer {

        private final String tenantId;
        private final String userId;
        private final String conversationId;
        private final String sessionId;
        private final List<MemoryTurnEvent> turns = new ArrayList<>();
        private int totalTokens;
        private Instant firstActivityAt;
        private Instant lastActivityAt;

        private MutableBuffer(MemoryTurnEvent event) {
            this.tenantId = event.tenantId();
            this.userId = event.userId();
            this.conversationId = event.conversationId();
            this.sessionId = event.sessionId();
        }

        private void append(MemoryTurnEvent event) {
            turns.add(event);
            totalTokens += event.estimatedTokens();
            if (firstActivityAt == null) {
                firstActivityAt = event.completedAt();
            }
            lastActivityAt = event.completedAt();
        }

        private MemoryBufferState state(MemoryAggregationPolicy policy) {
            boolean forceTokens = totalTokens >= policy.maxTokens();
            boolean forceTurns = turns.size() >= policy.maxTurns();
            MemoryFlushTrigger trigger = forceTokens
                    ? MemoryFlushTrigger.FORCE_TOKENS
                    : forceTurns ? MemoryFlushTrigger.FORCE_TURNS : null;
            return new MemoryBufferState(
                    tenantId,
                    userId,
                    conversationId,
                    sessionId,
                    turns.size(),
                    totalTokens,
                    lastActivityAt,
                    forceTokens || forceTurns,
                    trigger);
        }

        private MemoryBufferSnapshot snapshot(MemoryFlushTrigger trigger) {
            return new MemoryBufferSnapshot(
                    "memory-snapshot-" + UUID.randomUUID(),
                    tenantId,
                    userId,
                    conversationId,
                    sessionId,
                    trigger,
                    turns,
                    totalTokens,
                    firstActivityAt,
                    lastActivityAt);
        }
    }
}
