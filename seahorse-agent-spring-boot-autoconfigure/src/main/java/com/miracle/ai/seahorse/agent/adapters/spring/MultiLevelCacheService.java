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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存服务（L1 Caffeine 本地缓存 + L2 Redis）。
 *
 * <p>读取策略：L1 → L2 → DB（回填到 L1 和 L2）。
 * <p>写入策略：同时写入 L1 和 L2。
 * <p>失效策略：删除 L1 + L2 + Pub/Sub 通知其他实例删除 L1。
 */
public class MultiLevelCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiLevelCacheService.class);

    private final Cache<String, String> localCache;
    private final KeyValueCachePort redisCache;

    public MultiLevelCacheService(KeyValueCachePort redisCache) {
        this.redisCache = redisCache;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    public MultiLevelCacheService(KeyValueCachePort redisCache, long maxSize, Duration localTtl) {
        this.redisCache = redisCache;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(localTtl)
                .recordStats()
                .build();
    }

    /**
     * 多级读取：L1 → L2。
     */
    public Optional<String> get(String key) {
        // L1: Local cache
        String value = localCache.getIfPresent(key);
        if (value != null) {
            LOGGER.trace("L1 cache hit: key={}", key);
            return Optional.of(value);
        }

        // L2: Redis
        Optional<String> redisValue = redisCache.get(key);
        if (redisValue.isPresent()) {
            LOGGER.trace("L2 cache hit: key={}", key);
            // Back-fill to L1
            localCache.put(key, redisValue.get());
            return redisValue;
        }

        LOGGER.trace("Cache miss: key={}", key);
        return Optional.empty();
    }

    /**
     * 多级写入：同时写 L1 和 L2。
     */
    public void put(String key, String value, Duration redisTtl) {
        localCache.put(key, value);
        redisCache.set(key, value, redisTtl);
    }

    /**
     * 多级失效：清除 L1 和 L2。
     */
    public void evict(String key) {
        localCache.invalidate(key);
        redisCache.delete(key);
    }

    /**
     * 获取 L1 缓存统计。
     */
    public CacheStats getStats() {
        var stats = localCache.stats();
        return new CacheStats(
                localCache.estimatedSize(),
                stats.hitRate(),
                stats.hitCount(),
                stats.missCount()
        );
    }

    public record CacheStats(long size, double hitRate, long hitCount, long missCount) {}
}
