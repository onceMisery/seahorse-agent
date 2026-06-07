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

package com.miracle.ai.seahorse.agent.kernel.application.auth;

import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

public class KernelAuthRefreshService implements AuthRefreshInboundPort {

    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/37446017?v=4&size=64";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;
    private final TokenServicePort tokenServicePort;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public KernelAuthRefreshService(RefreshTokenRepositoryPort refreshTokenRepositoryPort,
                                    TokenServicePort tokenServicePort,
                                    Clock clock) {
        this.refreshTokenRepositoryPort = Objects.requireNonNull(refreshTokenRepositoryPort,
                "refreshTokenRepositoryPort must not be null");
        this.tokenServicePort = Objects.requireNonNull(tokenServicePort, "tokenServicePort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public RefreshTokenResult refresh(RefreshTokenCommand command) {
        RefreshTokenCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String refreshToken = trimToNull(safeCommand.refreshToken());
        if (refreshToken == null) {
            throw new IllegalArgumentException("refreshToken 不能为空");
        }
        RefreshTokenRecord record = refreshTokenRepositoryPort.findValid(refreshToken, clock.instant())
                .orElseThrow(() -> new IllegalArgumentException("刷新令牌无效或已过期"));
        String token = tokenServicePort.login(String.valueOf(record.userId()), record.tenantId());
        String nextRefreshToken = generateRefreshToken();
        Instant nextExpiresAt = clock.instant().plus(REFRESH_TOKEN_TTL);
        refreshTokenRepositoryPort.revoke(refreshToken);
        refreshTokenRepositoryPort.save(record.userId(), nextRefreshToken, nextExpiresAt);
        return new RefreshTokenResult(
                String.valueOf(record.userId()),
                record.role(),
                token,
                nextRefreshToken,
                nextExpiresAt,
                defaultAvatar(record.avatar()),
                record.tenantId());
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String defaultAvatar(String avatar) {
        return avatar == null || avatar.isBlank() ? DEFAULT_AVATAR_URL : avatar;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
