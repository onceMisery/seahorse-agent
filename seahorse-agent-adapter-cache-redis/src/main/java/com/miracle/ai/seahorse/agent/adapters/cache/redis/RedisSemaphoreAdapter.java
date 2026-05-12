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

import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedSemaphorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.SemaphorePermit;
import org.redisson.api.RBucket;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式信号量 adapter。
 *
 * <p>使用 Redisson 可过期信号量提供跨节点并发控制，permit id 会短暂写入 Redis 以便 release
 * 阶段准确释放 Redisson 真实许可。
 */
public class RedisSemaphoreAdapter implements DistributedSemaphorePort {

    private static final String KEY_PREFIX = "seahorse:agent:";
    private static final String KEY_SEMAPHORE_PREFIX = KEY_PREFIX + "semaphore:";
    private static final String KEY_SEMAPHORE_PERMIT_PREFIX = KEY_PREFIX + "semaphore-permit:";

    private final RedissonClient redissonClient;

    public RedisSemaphoreAdapter(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
    }

    @Override
    public Optional<SemaphorePermit> tryAcquire(String resource, String owner, int permits, Duration ttl) {
        if (permits <= 0) {
            return Optional.empty();
        }
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreKey(resource));
        semaphore.trySetPermits(Integer.MAX_VALUE);
        return acquirePermit(semaphore, resource, owner, permits, ttl);
    }

    @Override
    public void release(SemaphorePermit permit) {
        SemaphorePermit safePermit = Objects.requireNonNull(permit, "permit must not be null");
        RBucket<String> permitIdsBucket = redissonClient.getBucket(semaphorePermitKey(safePermit), StringCodec.INSTANCE);
        String permitIds = permitIdsBucket.get();
        if (permitIds == null || permitIds.isBlank()) {
            return;
        }
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreKey(safePermit.resource()));
        releasePermitIds(semaphore, permitIds);
        permitIdsBucket.delete();
    }

    private Optional<SemaphorePermit> acquirePermit(
            RPermitExpirableSemaphore semaphore, String resource, String owner, int permits, Duration ttl) {
        List<String> permitIds = new ArrayList<>();
        try {
            return acquirePermitIds(semaphore, resource, owner, permits, ttl, permitIds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            releasePermitIds(semaphore, permitIds);
            return Optional.empty();
        }
    }

    private Optional<SemaphorePermit> acquirePermitIds(
            RPermitExpirableSemaphore semaphore, String resource, String owner, int permits, Duration ttl,
            List<String> permitIds) throws InterruptedException {
        for (int index = 0; index < permits; index++) {
            String permitId = semaphore.tryAcquire(0, toMillis(ttl), TimeUnit.MILLISECONDS);
            if (permitId == null) {
                releasePermitIds(semaphore, permitIds);
                return Optional.empty();
            }
            permitIds.add(permitId);
        }
        SemaphorePermit permit = new SemaphorePermit(
                requireText(resource, "resource"),
                requireText(owner, "owner"),
                permits,
                expireAt(ttl));
        rememberPermitIds(permit, permitIds, ttl);
        return Optional.of(permit);
    }

    private void rememberPermitIds(SemaphorePermit permit, List<String> permitIds, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(semaphorePermitKey(permit), StringCodec.INSTANCE);
        String value = String.join(",", permitIds);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            bucket.set(value);
            return;
        }
        bucket.set(value, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void releasePermitIds(RPermitExpirableSemaphore semaphore, String permitIds) {
        for (String permitId : permitIds.split(",")) {
            if (!permitId.isBlank()) {
                semaphore.tryRelease(permitId);
            }
        }
    }

    private void releasePermitIds(RPermitExpirableSemaphore semaphore, List<String> permitIds) {
        for (String permitId : permitIds) {
            semaphore.tryRelease(permitId);
        }
    }

    private Instant expireAt(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Instant.MAX;
        }
        return Instant.now().plus(ttl);
    }

    private long toMillis(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0L;
        }
        return duration.toMillis();
    }

    private String semaphoreKey(String resource) {
        return KEY_SEMAPHORE_PREFIX + requireText(resource, "resource");
    }

    private String semaphorePermitKey(SemaphorePermit permit) {
        return KEY_SEMAPHORE_PERMIT_PREFIX + permit.resource() + ":" + permit.owner() + ":" + permit.expireAt().toEpochMilli();
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
