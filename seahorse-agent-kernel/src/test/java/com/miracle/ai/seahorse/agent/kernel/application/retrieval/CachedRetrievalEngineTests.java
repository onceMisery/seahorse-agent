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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedRetrievalEngineTests {

    @Test
    void shouldIsolateCacheEntriesPerTenant() {
        AtomicInteger delegateCalls = new AtomicInteger();
        CachedRetrievalEngine<String> engine = new CachedRetrievalEngine<>((query, options) -> {
            delegateCalls.incrementAndGet();
            return List.of("result-for-" + options.get("tenantId"));
        });

        List<String> tenantA = engine.retrieve("refund policy", Map.of("tenantId", "tenant-a"));
        List<String> tenantB = engine.retrieve("refund policy", Map.of("tenantId", "tenant-b"));

        assertEquals(2, delegateCalls.get());
        assertEquals(List.of("result-for-tenant-a"), tenantA);
        assertEquals(List.of("result-for-tenant-b"), tenantB);
        assertNotEquals(tenantA, tenantB);
        assertEquals(2, engine.cacheSize());
    }

    @Test
    void shouldEvictEldestEntryWhenMaxEntriesExceeded() {
        CachedRetrievalEngine<String> engine = new CachedRetrievalEngine<>(
                (query, options) -> List.of("result-" + query),
                Duration.ofMinutes(10),
                2);

        engine.retrieve("query-1", Map.of());
        engine.retrieve("query-2", Map.of());
        engine.retrieve("query-3", Map.of());

        assertEquals(2, engine.cacheSize());
        // query-1 was the FIFO head and must have been evicted; re-retrieving it
        // must hit the delegate again (observed via a fresh, distinct result marker)
        List<String> reFetched = engine.retrieve("query-1", Map.of());
        assertEquals(List.of("result-query-1"), reFetched);
        assertEquals(2, engine.cacheSize());
    }

    @Test
    void shouldKeepRecentEntryAndNotGrowBeyondCapacity() {
        AtomicInteger delegateCalls = new AtomicInteger();
        CachedRetrievalEngine<String> engine = new CachedRetrievalEngine<>(
                (query, options) -> {
                    delegateCalls.incrementAndGet();
                    return List.of("result-" + query);
                },
                Duration.ofMinutes(10),
                2);

        engine.retrieve("query-1", Map.of());
        engine.retrieve("query-2", Map.of());
        engine.retrieve("query-3", Map.of());
        // three distinct queries produced three delegate calls and one FIFO eviction
        assertEquals(3, delegateCalls.get());
        assertEquals(2, engine.cacheSize());
        // query-2 survived the eviction: retrieving it must not hit the delegate
        engine.retrieve("query-2", Map.of());
        assertEquals(3, delegateCalls.get());
        // query-1 was the FIFO head: re-retrieving it hits the delegate again
        engine.retrieve("query-1", Map.of());
        assertEquals(4, delegateCalls.get());
        assertEquals(2, engine.cacheSize());
    }

    @Test
    void shouldNotServeExpiredEntry() {
        AtomicInteger delegateCalls = new AtomicInteger();
        // a negative TTL back-dates every entry, so the cache is deterministically expired
        CachedRetrievalEngine<String> engine = new CachedRetrievalEngine<>((query, options) -> {
            delegateCalls.incrementAndGet();
            return List.of("result-" + query);
        }, Duration.ofSeconds(-1));

        engine.retrieve("query-1", Map.of());
        engine.retrieve("query-1", Map.of());

        assertEquals(2, delegateCalls.get());
        assertEquals(1, engine.cacheSize());
    }

    @Test
    void shouldClearKeyOrderOnInvalidateAll() {
        CachedRetrievalEngine<String> engine = new CachedRetrievalEngine<>(
                (query, options) -> List.of("result-" + query),
                Duration.ofMinutes(10),
                2);

        engine.retrieve("query-1", Map.of());
        engine.retrieve("query-2", Map.of());
        engine.invalidateAll();
        assertEquals(0, engine.cacheSize());

        engine.retrieve("query-3", Map.of());
        engine.retrieve("query-4", Map.of());
        engine.retrieve("query-5", Map.of());
        assertEquals(2, engine.cacheSize());
    }

    @Test
    void shouldRejectNonPositiveMaxEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> new CachedRetrievalEngine<String>((query, options) -> List.of(), null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CachedRetrievalEngine<String>((query, options) -> List.of(), null, -1));
    }

    @Test
    void shouldServeIdenticalQueryFromCacheWithinSameTenant() {
        AtomicInteger delegateCalls = new AtomicInteger();
        CachedRetrievalEngine<String> engine = new CachedRetrievalEngine<>((query, options) -> {
            delegateCalls.incrementAndGet();
            return List.of("result-" + query);
        });

        Map<String, Object> options = Map.of("tenantId", "tenant-a", "topK", 5);
        List<String> first = engine.retrieve("same question", options);
        List<String> second = engine.retrieve("same question", options);

        assertEquals(1, delegateCalls.get());
        assertTrue(first == second, "second call must be served from cache");
    }
}
