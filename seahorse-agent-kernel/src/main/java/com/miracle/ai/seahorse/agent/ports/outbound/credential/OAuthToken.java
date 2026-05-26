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
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * OAuth access token material.
 */
public record OAuthToken(
        SecretValue accessToken,
        OAuthTokenType tokenType,
        Instant expiresAt,
        List<String> scopes,
        String refreshTokenRef
) {

    public OAuthToken {
        accessToken = Objects.requireNonNullElseGet(accessToken, SecretValue::empty);
        tokenType = Objects.requireNonNullElse(tokenType, OAuthTokenType.BEARER);
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        scopes = OAuthScopes.normalize(scopes);
        refreshTokenRef = Objects.requireNonNullElse(refreshTokenRef, "");
        if (!accessToken.hasText()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }

    public static OAuthToken bearer(SecretValue accessToken,
                                    Instant expiresAt,
                                    List<String> scopes,
                                    String refreshTokenRef) {
        return new OAuthToken(accessToken, OAuthTokenType.BEARER, expiresAt, scopes, refreshTokenRef);
    }

    public boolean isExpired(Clock clock) {
        return !expiresAt.isAfter(Objects.requireNonNull(clock, "clock must not be null").instant());
    }
}
