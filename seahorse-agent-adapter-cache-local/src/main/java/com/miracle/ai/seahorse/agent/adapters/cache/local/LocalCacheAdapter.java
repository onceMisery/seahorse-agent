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

package com.miracle.ai.seahorse.agent.adapters.cache.local;

import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.PubSubMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.PubSubMessageHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.PubSubPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.id.IdGeneratorPort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地内存缓存与协调 adapter。
 *
 * <p>该实现只保证单 JVM 内可见性，适用于本地开发和单节点部署。多节点部署应切换到 Redis/Redisson adapter。
 */
public class LocalCacheAdapter implements KeyValueCachePort, RateLimiterPort, PubSubPort,
        DistributedLockPort, IdGeneratorPort {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<String> locks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, List<PubSubMessageHandler>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimits = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expired()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        cache.put(requireText(key, "key"), new CacheEntry(Objects.requireNonNullElse(value, ""), expireAt(ttl)));
    }

    @Override
    public boolean delete(String key) {
        return cache.remove(key) != null;
    }

    @Override
    public RateLimitDecision tryAcquire(String resource, String subject, int permits, Duration ttl) {
        requireText(resource, "resource");
        requireText(subject, "subject");
        if (permits <= 0) {
            return RateLimitDecision.rejected(Duration.ZERO, "permits must be positive");
        }
        String key = resource + ":" + subject;
        Instant now = Instant.now();
        RateLimitEntry entry = rateLimits.compute(key, (ignored, current) -> {
            if (current == null || current.expired(now)) {
                return new RateLimitEntry(new AtomicLong(1L), expireAt(ttl));
            }
            current.counter().incrementAndGet();
            return current;
        });
        long used = entry.counter().get();
        long remaining = Math.max(0L, permits - used);
        if (used > permits) {
            return RateLimitDecision.rejected(Duration.between(now, entry.expireAt()), "rate limit exceeded");
        }
        return RateLimitDecision.allowed(remaining);
    }

    @Override
    public void publish(PubSubMessage message) {
        PubSubMessage safeMessage = Objects.requireNonNull(message, "message must not be null");
        List<PubSubMessageHandler> handlers = subscribers.getOrDefault(safeMessage.topic(), List.of());
        for (PubSubMessageHandler handler : handlers) {
            handler.handle(safeMessage);
        }
    }

    @Override
    public AutoCloseable subscribe(String topic, PubSubMessageHandler handler) {
        String safeTopic = requireText(topic, "topic");
        PubSubMessageHandler safeHandler = Objects.requireNonNull(handler, "handler must not be null");
        subscribers.computeIfAbsent(safeTopic, ignored -> new CopyOnWriteArrayList<>()).add(safeHandler);
        return () -> unsubscribe(safeTopic, safeHandler);
    }

    @Override
    public boolean tryLock(String lockName, Duration waitTime, Duration leaseTime) {
        return locks.add(requireText(lockName, "lockName"));
    }

    @Override
    public void unlock(String lockName) {
        locks.remove(lockName);
    }

    @Override
    public long nextId(String namespace) {
        return sequences.computeIfAbsent(requireText(namespace, "namespace"), ignored -> new AtomicLong()).incrementAndGet();
    }

    private void unsubscribe(String topic, PubSubMessageHandler handler) {
        List<PubSubMessageHandler> handlers = subscribers.get(topic);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    private Instant expireAt(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Instant.MAX;
        }
        return Instant.now().plus(ttl);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record CacheEntry(String value, Instant expireAt) {

        private boolean expired() {
            return Instant.now().isAfter(expireAt);
        }
    }

    private record RateLimitEntry(AtomicLong counter, Instant expireAt) {

        private boolean expired(Instant now) {
            return now.isAfter(expireAt);
        }
    }
}
