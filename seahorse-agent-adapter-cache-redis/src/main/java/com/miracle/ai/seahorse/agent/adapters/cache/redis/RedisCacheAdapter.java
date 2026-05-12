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
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.PubSubMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.PubSubMessageHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.PubSubPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimitDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.id.IdGeneratorPort;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存与协同 adapter。
 *
 * <p>该实现仅依赖 Redisson core，不引入 Spring Boot starter，适合作为独立微内核 adapter
 * 由外部 DI 容器注入 {@link RedissonClient} 后注册到端口注册表。
 */
public class RedisCacheAdapter implements KeyValueCachePort, RateLimiterPort, PubSubPort,
        DistributedLockPort, IdGeneratorPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String KEY_PREFIX = "seahorse:agent:";
    private static final String KEY_CACHE_PREFIX = KEY_PREFIX + "cache:";
    private static final String KEY_LOCK_PREFIX = KEY_PREFIX + "lock:";
    private static final String KEY_PUBSUB_PREFIX = KEY_PREFIX + "topic:";
    private static final String KEY_RATE_LIMIT_PREFIX = KEY_PREFIX + "ratelimit:";
    private static final String KEY_ID_PREFIX = KEY_PREFIX + "id:";

    private final RedissonClient redissonClient;

    public RedisCacheAdapter(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
    }

    @Override
    public Optional<String> get(String key) {
        RBucket<String> bucket = redissonClient.getBucket(cacheKey(key), StringCodec.INSTANCE);
        return Optional.ofNullable(bucket.get());
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(cacheKey(key), StringCodec.INSTANCE);
        String safeValue = Objects.requireNonNullElse(value, "");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            bucket.set(safeValue);
            return;
        }
        bucket.set(safeValue, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean delete(String key) {
        RBucket<String> bucket = redissonClient.getBucket(cacheKey(key), StringCodec.INSTANCE);
        return bucket.delete();
    }

    @Override
    public RateLimitDecision tryAcquire(String resource, String subject, int permits, Duration ttl) {
        if (permits <= 0) {
            return RateLimitDecision.rejected(Duration.ZERO, "permits must be positive");
        }
        RAtomicLong counter = redissonClient.getAtomicLong(rateLimitKey(resource, subject));
        long usedPermits = counter.incrementAndGet();
        if (usedPermits == 1L && ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            counter.expire(ttl);
        }
        long remaining = Math.max(0L, permits - usedPermits);
        if (usedPermits > permits) {
            return RateLimitDecision.rejected(ttl == null ? Duration.ZERO : ttl, "rate limit exceeded");
        }
        return RateLimitDecision.allowed(remaining);
    }

    @Override
    public void publish(PubSubMessage message) {
        PubSubMessage safeMessage = Objects.requireNonNull(message, "message must not be null");
        RTopic topic = redissonClient.getTopic(pubSubKey(safeMessage.topic()), StringCodec.INSTANCE);
        topic.publish(serialize(safeMessage));
    }

    @Override
    public AutoCloseable subscribe(String topic, PubSubMessageHandler handler) {
        String safeTopic = requireText(topic, "topic");
        PubSubMessageHandler safeHandler = Objects.requireNonNull(handler, "handler must not be null");
        RTopic rTopic = redissonClient.getTopic(pubSubKey(safeTopic), StringCodec.INSTANCE);
        int listenerId = rTopic.addListener(String.class, (channel, payload) -> safeHandler.handle(deserialize(payload)));
        return () -> rTopic.removeListener(listenerId);
    }

    @Override
    public boolean tryLock(String lockName, Duration waitTime, Duration leaseTime) {
        RLock lock = redissonClient.getLock(lockKey(lockName));
        try {
            return lock.tryLock(toMillis(waitTime), toMillis(leaseTime), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock(String lockName) {
        RLock lock = redissonClient.getLock(lockKey(lockName));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public long nextId(String namespace) {
        return redissonClient.getAtomicLong(idKey(namespace)).incrementAndGet();
    }

    private PubSubMessage deserialize(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, PubSubMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("deserialize pubsub message failed", ex);
        }
    }

    private String serialize(PubSubMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize pubsub message failed", ex);
        }
    }

    private long toMillis(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0L;
        }
        return duration.toMillis();
    }

    private String cacheKey(String key) {
        return KEY_CACHE_PREFIX + requireText(key, "key");
    }

    private String lockKey(String lockName) {
        return KEY_LOCK_PREFIX + requireText(lockName, "lockName");
    }

    private String pubSubKey(String topic) {
        return KEY_PUBSUB_PREFIX + requireText(topic, "topic");
    }

    private String rateLimitKey(String resource, String subject) {
        return KEY_RATE_LIMIT_PREFIX + requireText(resource, "resource") + ":" + requireText(subject, "subject");
    }

    private String idKey(String namespace) {
        return KEY_ID_PREFIX + requireText(namespace, "namespace");
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
