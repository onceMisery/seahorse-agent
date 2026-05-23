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

package com.miracle.ai.seahorse.agent.adapters.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed aggregation buffer suitable for distributed deployments.
 *
 * <p>State is stored as a JSON document per (tenant, session) key inside a Redisson bucket,
 * protected by a per-key Redis lock. The schema mirrors {@link com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.InMemoryMemoryAggregationBufferPort}
 * so consumers observe identical behaviour regardless of backend.
 */
public class RedisMemoryAggregationBufferPort implements MemoryAggregationBufferPort {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String BUFFER_KEY_PREFIX = "seahorse:agent:memory:aggregation:buffer:";
    private static final String LOCK_KEY_PREFIX = "seahorse:agent:memory:aggregation:lock:";
    private static final String SCAN_PATTERN = BUFFER_KEY_PREFIX + "*";
    private static final long LOCK_WAIT_MILLIS = 250L;
    private static final long LOCK_LEASE_MILLIS = 2_000L;

    private final RedissonClient redissonClient;
    private final MemoryAggregationPolicy policy;
    private final ObjectMapper objectMapper;

    public RedisMemoryAggregationBufferPort(RedissonClient redissonClient,
                                            MemoryAggregationPolicy policy) {
        this(redissonClient, policy, new ObjectMapper());
    }

    public RedisMemoryAggregationBufferPort(RedissonClient redissonClient,
                                            MemoryAggregationPolicy policy,
                                            ObjectMapper objectMapper) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.policy = Objects.requireNonNullElseGet(policy, MemoryAggregationPolicy::defaults);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public MemoryBufferState appendTurn(MemoryTurnEvent event) {
        MemoryTurnEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        String bufferKey = bufferKey(safeEvent.sessionId(), safeEvent.tenantId());
        String lockKey = lockKey(safeEvent.sessionId(), safeEvent.tenantId());
        return withLock(lockKey, () -> {
            BufferDocument document = readDocument(bufferKey)
                    .orElseGet(() -> BufferDocument.initial(safeEvent));
            document.append(safeEvent);
            writeDocument(bufferKey, document);
            return document.toState(policy);
        });
    }

    @Override
    public Optional<MemoryBufferSnapshot> flushReady(String sessionId,
                                                     String tenantId,
                                                     MemoryFlushTrigger trigger,
                                                     Instant now) {
        String bufferKey = bufferKey(sessionId, tenantId);
        String lockKey = lockKey(sessionId, tenantId);
        MemoryFlushTrigger safeTrigger = Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL);
        Instant safeNow = Objects.requireNonNullElseGet(now, Instant::now);
        return withLock(lockKey, () -> {
            Optional<BufferDocument> documentOpt = readDocument(bufferKey);
            if (documentOpt.isEmpty() || documentOpt.get().turns.isEmpty()) {
                return Optional.<MemoryBufferSnapshot>empty();
            }
            BufferDocument document = documentOpt.get();
            if (!isReady(document, safeTrigger, safeNow)) {
                return Optional.<MemoryBufferSnapshot>empty();
            }
            deleteDocument(bufferKey);
            return Optional.of(document.toSnapshot(safeTrigger));
        });
    }

    @Override
    public Optional<MemoryBufferState> state(String sessionId, String tenantId) {
        return readDocument(bufferKey(sessionId, tenantId))
                .map(document -> document.toState(policy));
    }

    @Override
    public List<MemoryBufferState> listStates(int limit) {
        int safeLimit = limit <= 0 ? policy.maxContextBlocks() : limit;
        RKeys keys = redissonClient.getKeys();
        List<MemoryBufferState> states = new ArrayList<>();
        for (String key : keys.getKeysByPattern(SCAN_PATTERN)) {
            readDocument(key).ifPresent(document -> states.add(document.toState(policy)));
        }
        states.sort(Comparator.comparing(MemoryBufferState::lastActivityAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        if (states.size() <= safeLimit) {
            return List.copyOf(states);
        }
        return List.copyOf(states.subList(0, safeLimit));
    }

    @Override
    public void discardSnapshot(String snapshotId) {
        // Snapshots are not retained server-side; flushReady already deleted the buffer document.
    }

    private boolean isReady(BufferDocument document, MemoryFlushTrigger trigger, Instant now) {
        Instant lastActivity = document.lastActivityAt() == null ? Instant.EPOCH : document.lastActivityAt();
        return switch (trigger) {
            case IDLE_TIMEOUT -> !now.isBefore(lastActivity.plusMillis(policy.idleFlushMillis()));
            case FORCE_TURNS -> document.turns.size() >= policy.maxTurns();
            case FORCE_TOKENS -> document.totalTokens >= policy.maxTokens();
            case TOPIC_SHIFT, MANUAL, SESSION_CLOSED -> true;
        };
    }

    private Optional<BufferDocument> readDocument(String bufferKey) {
        RBucket<String> bucket = redissonClient.getBucket(bufferKey, StringCodec.INSTANCE);
        String json = bucket.get();
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, BufferDocument.class));
        } catch (JsonProcessingException ex) {
            bucket.delete();
            return Optional.empty();
        }
    }

    private void writeDocument(String bufferKey, BufferDocument document) {
        try {
            String json = objectMapper.writeValueAsString(document);
            RBucket<String> bucket = redissonClient.getBucket(bufferKey, StringCodec.INSTANCE);
            bucket.set(json, Duration.ofMillis(policy.bufferTtlMillis()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize aggregation buffer", ex);
        }
    }

    private void deleteDocument(String bufferKey) {
        redissonClient.getBucket(bufferKey, StringCodec.INSTANCE).delete();
    }

    private <T> T withLock(String lockKey, java.util.function.Supplier<T> work) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_MILLIS, LOCK_LEASE_MILLIS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("failed to acquire aggregation buffer lock: " + lockKey);
            }
            return work.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while acquiring aggregation buffer lock", ex);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static String bufferKey(String sessionId, String tenantId) {
        return BUFFER_KEY_PREFIX + normalize(tenantId, DEFAULT_TENANT_ID) + ":" + normalize(sessionId, "");
    }

    private static String lockKey(String sessionId, String tenantId) {
        return LOCK_KEY_PREFIX + normalize(tenantId, DEFAULT_TENANT_ID) + ":" + normalize(sessionId, "");
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        if (normalized.isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }

    /**
     * Mutable JSON document that mirrors the in-memory buffer schema.
     *
     * <p>Jackson uses the no-arg setters; the class is package-private and not part of the kernel
     * domain to keep the wire format owned by this adapter. {@code Instant} fields are serialized
     * as epoch millis to keep the wire format independent of Jackson's java.time module.
     */
    static final class BufferDocument {

        public String tenantId;
        public String userId;
        public String conversationId;
        public String sessionId;
        public List<TurnDocument> turns = new ArrayList<>();
        public int totalTokens;
        public Long firstActivityAtMillis;
        public Long lastActivityAtMillis;

        static BufferDocument initial(MemoryTurnEvent event) {
            BufferDocument document = new BufferDocument();
            document.tenantId = event.tenantId();
            document.userId = event.userId();
            document.conversationId = event.conversationId();
            document.sessionId = event.sessionId();
            return document;
        }

        void append(MemoryTurnEvent event) {
            turns.add(TurnDocument.from(event));
            totalTokens += event.estimatedTokens();
            long completedAtMillis = event.completedAt() == null
                    ? Instant.now().toEpochMilli()
                    : event.completedAt().toEpochMilli();
            if (firstActivityAtMillis == null) {
                firstActivityAtMillis = completedAtMillis;
            }
            lastActivityAtMillis = completedAtMillis;
        }

        Instant lastActivityAt() {
            return lastActivityAtMillis == null ? null : Instant.ofEpochMilli(lastActivityAtMillis);
        }

        Instant firstActivityAt() {
            return firstActivityAtMillis == null ? null : Instant.ofEpochMilli(firstActivityAtMillis);
        }

        MemoryBufferState toState(MemoryAggregationPolicy policy) {
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
                    lastActivityAt(),
                    forceTokens || forceTurns,
                    trigger);
        }

        MemoryBufferSnapshot toSnapshot(MemoryFlushTrigger trigger) {
            List<MemoryTurnEvent> events = turns.stream()
                    .map(TurnDocument::toEvent)
                    .toList();
            return new MemoryBufferSnapshot(
                    "memory-snapshot-" + UUID.randomUUID(),
                    tenantId,
                    userId,
                    conversationId,
                    sessionId,
                    trigger,
                    events,
                    totalTokens,
                    firstActivityAt(),
                    lastActivityAt());
        }
    }

    static final class TurnDocument {

        public String tenantId;
        public String userId;
        public String conversationId;
        public String sessionId;
        public String userMessageId;
        public String assistantMessageId;
        public String userText;
        public String assistantText;
        public long completedAtMillis;
        public int estimatedTokens;

        static TurnDocument from(MemoryTurnEvent event) {
            TurnDocument document = new TurnDocument();
            document.tenantId = event.tenantId();
            document.userId = event.userId();
            document.conversationId = event.conversationId();
            document.sessionId = event.sessionId();
            document.userMessageId = event.userMessageId();
            document.assistantMessageId = event.assistantMessageId();
            document.userText = event.userText();
            document.assistantText = event.assistantText();
            document.completedAtMillis = event.completedAt() == null
                    ? Instant.now().toEpochMilli()
                    : event.completedAt().toEpochMilli();
            document.estimatedTokens = event.estimatedTokens();
            return document;
        }

        MemoryTurnEvent toEvent() {
            return new MemoryTurnEvent(
                    tenantId,
                    userId,
                    conversationId,
                    sessionId,
                    userMessageId,
                    assistantMessageId,
                    userText,
                    assistantText,
                    Instant.ofEpochMilli(completedAtMillis),
                    estimatedTokens);
        }
    }
}
