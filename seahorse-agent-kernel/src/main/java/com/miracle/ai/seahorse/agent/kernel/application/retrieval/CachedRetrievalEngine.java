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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Cached retrieval engine that wraps another retrieval function with TTL-based caching.
 *
 * <p>Uses a {@link ConcurrentHashMap} with time-based expiry to avoid redundant
 * retrieval calls for identical queries. The cache key is a SHA-256 hash of the
 * query text combined with retrieval options.
 *
 * <p>This is a decorator: supply the actual retrieval logic as a {@link BiFunction}
 * and wrap it with this engine to gain transparent caching.
 */
public class CachedRetrievalEngine<T> {

    private static final Logger log = LoggerFactory.getLogger(CachedRetrievalEngine.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final BiFunction<String, Map<String, Object>, List<T>> delegate;
    private final ConcurrentHashMap<String, CacheEntry<List<T>>> cache;
    private final Duration ttl;

    /**
     * Create a cached retrieval engine wrapping the given delegate with default TTL (10 minutes).
     *
     * @param delegate the underlying retrieval function (query, options) → results
     */
    public CachedRetrievalEngine(BiFunction<String, Map<String, Object>, List<T>> delegate) {
        this(delegate, DEFAULT_TTL);
    }

    /**
     * Create a cached retrieval engine wrapping the given delegate with a custom TTL.
     *
     * @param delegate the underlying retrieval function (query, options) → results
     * @param ttl      the cache entry time-to-live
     */
    public CachedRetrievalEngine(BiFunction<String, Map<String, Object>, List<T>> delegate,
                                 Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.ttl = Objects.requireNonNullElse(ttl, DEFAULT_TTL);
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Retrieve results for the given query, serving from cache when possible.
     *
     * @param query   the retrieval query
     * @param options additional retrieval options (may be empty)
     * @return the list of retrieval results
     */
    public List<T> retrieve(String query, Map<String, Object> options) {
        String cacheKey = computeCacheKey(query, options);
        CacheEntry<List<T>> entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for query hash [{}]", cacheKey.substring(0, 8));
            return entry.value();
        }

        log.debug("Cache miss for query hash [{}], delegating", cacheKey.substring(0, 8));
        List<T> results = delegate.apply(query, options != null ? options : Map.of());
        cache.put(cacheKey, new CacheEntry<>(results, Instant.now().plus(ttl)));
        evictStaleEntries();
        return results;
    }

    /**
     * Invalidate all cached entries.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Return the current number of cached entries.
     */
    public int cacheSize() {
        return cache.size();
    }

    private String computeCacheKey(String query, Map<String, Object> options) {
        String raw = Objects.requireNonNullElse(query, "") + "|"
                + (options != null ? options.toString() : "");
        return sha256Hex(raw);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed to be available in every JDK
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void evictStaleEntries() {
        if (cache.size() < 256) {
            return;
        }
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
