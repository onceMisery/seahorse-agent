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

package com.miracle.ai.seahorse.agent.ports.outbound.credential;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory OAuth token cache for local/default composition.
 */
public class InMemoryOAuthTokenCachePort implements OAuthTokenCachePort {

    private final Clock clock;
    private final Map<OAuthTokenCacheKey, CachedToken> tokens = new ConcurrentHashMap<>();

    public InMemoryOAuthTokenCachePort() {
        this(Clock.systemUTC());
    }

    public InMemoryOAuthTokenCachePort(Clock clock) {
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public Optional<OAuthToken> get(OAuthTokenCacheKey key) {
        OAuthTokenCacheKey safeKey = Objects.requireNonNull(key, "key must not be null");
        CachedToken cachedToken = tokens.get(safeKey);
        if (cachedToken == null) {
            return Optional.empty();
        }
        if (!cachedToken.expiresAt().isAfter(clock.instant())) {
            tokens.remove(safeKey);
            return Optional.empty();
        }
        return Optional.of(cachedToken.token());
    }

    @Override
    public void put(OAuthTokenCacheKey key, OAuthToken token, Duration ttl) {
        OAuthTokenCacheKey safeKey = Objects.requireNonNull(key, "key must not be null");
        OAuthToken safeToken = Objects.requireNonNull(token, "token must not be null");
        Duration safeTtl = Objects.requireNonNullElse(ttl, Duration.ZERO);
        if (safeTtl.isZero() || safeTtl.isNegative()) {
            return;
        }
        tokens.put(safeKey, new CachedToken(safeToken, clock.instant().plus(safeTtl)));
    }

    @Override
    public void evict(OAuthTokenCacheKey key) {
        tokens.remove(Objects.requireNonNull(key, "key must not be null"));
    }

    private record CachedToken(OAuthToken token, Instant expiresAt) {
    }
}
