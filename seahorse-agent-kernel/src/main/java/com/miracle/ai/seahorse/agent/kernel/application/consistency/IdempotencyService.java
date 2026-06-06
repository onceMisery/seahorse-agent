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

package com.miracle.ai.seahorse.agent.kernel.application.consistency;

import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 幂等执行服务。
 *
 * <p>保持 Port 不变，使用缓存结果 + 本地 in-flight 去重，避免同一 JVM 内并发重复执行。
 */
public class IdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final KeyValueCachePort cachePort;
    private final Map<String, Object> inFlightLocks = new ConcurrentHashMap<>();

    public IdempotencyService(KeyValueCachePort cachePort) {
        this.cachePort = cachePort;
    }

    public IdempotencyResult<String> executeIdempotent(String idempotencyKey,
                                                       String operationType,
                                                       Supplier<String> operation) {
        String cacheKey = KEY_PREFIX + operationType + ":" + idempotencyKey;

        Optional<String> cached = cachePort.get(cacheKey);
        if (cached.isPresent()) {
            LOGGER.debug("Idempotency hit: key={}, type={}", idempotencyKey, operationType);
            return IdempotencyResult.duplicate(cached.get());
        }

        Object localLock = inFlightLocks.computeIfAbsent(cacheKey, key -> new Object());
        synchronized (localLock) {
            try {
                Optional<String> cachedAfterLock = cachePort.get(cacheKey);
                if (cachedAfterLock.isPresent()) {
                    LOGGER.debug("Idempotency hit after lock: key={}, type={}", idempotencyKey, operationType);
                    return IdempotencyResult.duplicate(cachedAfterLock.get());
                }

                String lockKey = cacheKey + ":lock";
                cachePort.set(lockKey, "PROCESSING", LOCK_TTL);
                try {
                    String result = operation.get();
                    cachePort.set(cacheKey, result, DEFAULT_TTL);
                    LOGGER.debug("Idempotency first execution: key={}, type={}", idempotencyKey, operationType);
                    return IdempotencyResult.firstExecution(result);
                } catch (RuntimeException | Error ex) {
                    cachePort.delete(lockKey);
                    throw ex;
                } finally {
                    cachePort.delete(lockKey);
                }
            } finally {
                inFlightLocks.remove(cacheKey, localLock);
            }
        }
    }

    public record IdempotencyResult<T>(T result, boolean isDuplicate) {
        public static <T> IdempotencyResult<T> firstExecution(T result) {
            return new IdempotencyResult<>(result, false);
        }

        public static <T> IdempotencyResult<T> duplicate(T result) {
            return new IdempotencyResult<>(result, true);
        }
    }
}
