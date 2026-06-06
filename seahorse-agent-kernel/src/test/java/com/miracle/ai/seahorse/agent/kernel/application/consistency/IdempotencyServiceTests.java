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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyServiceTests {

    @Test
    void concurrentRequestsForSameKeyExecuteOnlyOnce() throws Exception {
        RecordingCachePort cachePort = new RecordingCachePort();
        IdempotencyService service = new IdempotencyService(cachePort);
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch inFlight = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<IdempotencyService.IdempotencyResult<String>> first;
        java.util.concurrent.Future<IdempotencyService.IdempotencyResult<String>> second;

        try {
            first = executor.submit(() -> service.executeIdempotent("same-key", "billing", () -> {
                executions.incrementAndGet();
                start.countDown();
                await(inFlight);
                return "ok";
            }));
            await(start);
            second = executor.submit(() -> service.executeIdempotent("same-key", "billing", () -> {
                executions.incrementAndGet();
                return "duplicate";
            }));

            inFlight.countDown();
            IdempotencyService.IdempotencyResult<String> firstResult = first.get(5, TimeUnit.SECONDS);
            IdempotencyService.IdempotencyResult<String> secondResult = second.get(5, TimeUnit.SECONDS);

            assertEquals(1, executions.get());
            assertEquals("ok", firstResult.result());
            assertEquals("ok", secondResult.result());
            assertTrue(secondResult.isDuplicate());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedExecutionReleasesLockAndAllowsRetry() {
        RecordingCachePort cachePort = new RecordingCachePort();
        IdempotencyService service = new IdempotencyService(cachePort);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                service.executeIdempotent("retry-key", "billing", () -> {
                    throw new IllegalStateException("boom");
                }));
        assertEquals("boom", thrown.getMessage());

        IdempotencyService.IdempotencyResult<String> result = service.executeIdempotent("retry-key", "billing", () -> "success");

        assertEquals("success", result.result());
        assertFalse(result.isDuplicate());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static final class RecordingCachePort implements KeyValueCachePort {

        private final java.util.concurrent.ConcurrentHashMap<String, String> values = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public boolean delete(String key) {
            return values.remove(key) != null;
        }
    }
}
