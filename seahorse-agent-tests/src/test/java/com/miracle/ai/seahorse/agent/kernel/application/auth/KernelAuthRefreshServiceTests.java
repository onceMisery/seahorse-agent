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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelAuthRefreshServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-06T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldRotateRefreshTokenAndIssueNewAccessToken() {
        InMemoryRefreshTokenRepository refreshTokenRepository = new InMemoryRefreshTokenRepository(
                new RefreshTokenRecord(1L, "alice", "admin", null, "default", "old-refresh",
                        NOW.plusSeconds(60)));
        FakeTokenService tokenService = new FakeTokenService();
        KernelAuthRefreshService service = new KernelAuthRefreshService(refreshTokenRepository,
                tokenService, FIXED_CLOCK);

        RefreshTokenResult result = service.refresh(new RefreshTokenCommand("old-refresh"));

        assertThat(result.userId()).isEqualTo("1");
        assertThat(result.role()).isEqualTo("admin");
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("old-refresh");
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 60 * 60));
        assertThat(refreshTokenRepository.revokedToken).isEqualTo("old-refresh");
        assertThat(refreshTokenRepository.savedToken).isEqualTo(result.refreshToken());
        assertThat(tokenService.loginTenantId).isEqualTo("default");
    }

    @Test
    void shouldRejectInvalidRefreshToken() {
        KernelAuthRefreshService service = new KernelAuthRefreshService(
                new InMemoryRefreshTokenRepository(null), new FakeTokenService(), FIXED_CLOCK);

        assertThatThrownBy(() -> service.refresh(new RefreshTokenCommand("missing-refresh")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("刷新令牌无效或已过期");
    }

    private static class FakeTokenService implements TokenServicePort {
        private String loginTenantId;

        @Override
        public String login(String userId, String tenantId) {
            loginTenantId = tenantId;
            return "token-" + userId;
        }

        @Override
        public void logout() {
        }
    }

    private static class InMemoryRefreshTokenRepository implements RefreshTokenRepositoryPort {
        private final RefreshTokenRecord record;
        private String revokedToken;
        private String savedToken;

        private InMemoryRefreshTokenRepository(RefreshTokenRecord record) {
            this.record = record;
        }

        @Override
        public void save(Long userId, String refreshToken, Instant expiresAt) {
            savedToken = refreshToken;
        }

        @Override
        public Optional<RefreshTokenRecord> findValid(String refreshToken, Instant now) {
            if (record == null || !record.refreshToken().equals(refreshToken)
                    || !record.refreshTokenExpiresAt().isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        @Override
        public void revoke(String refreshToken) {
            revokedToken = refreshToken;
        }
    }
}
