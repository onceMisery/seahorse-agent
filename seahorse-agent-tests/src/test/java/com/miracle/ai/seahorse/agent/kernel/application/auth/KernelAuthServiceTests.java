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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelAuthServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-06T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldLoginAndPersistTenantScopedRefreshToken() {
        FakeTokenService tokenService = new FakeTokenService();
        InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
        KernelAuthService service = service(tokenService, refreshTokens);

        LoginResult result = service.login(new LoginCommand("alice", "secret"));

        assertThat(result.userId()).isEqualTo("1");
        assertThat(result.role()).isEqualTo("admin");
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.tenantId()).isEqualTo("tenant-a");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 60 * 60));
        assertThat(refreshTokens.userId).isEqualTo(1L);
        assertThat(refreshTokens.tenantId).isEqualTo("tenant-a");
        assertThat(refreshTokens.refreshToken).isEqualTo(result.refreshToken());
        assertThat(tokenService.loginTenantId).isEqualTo("tenant-a");
    }

    @Test
    void shouldRejectInvalidPassword() {
        KernelAuthService service = service(new FakeTokenService(), new InMemoryRefreshTokenRepository());

        assertThatThrownBy(() -> service.login(new LoginCommand("alice", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void shouldRevokeRefreshTokenWhenLoggingOut() {
        FakeTokenService tokenService = new FakeTokenService();
        InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
        KernelAuthService service = service(tokenService, refreshTokens);

        service.logout(" refresh-current ");

        assertThat(tokenService.loggedOut).isTrue();
        assertThat(refreshTokens.revokedToken).isEqualTo("refresh-current");
    }

    private KernelAuthService service(FakeTokenService tokenService,
                                      InMemoryRefreshTokenRepository refreshTokens) {
        return new KernelAuthService(
                new SingleUserRepository(),
                PasswordHasherPort.plainText(),
                tokenService,
                null,
                refreshTokens,
                FIXED_CLOCK);
    }

    private static final class FakeTokenService implements TokenServicePort {
        private String loginTenantId;
        private boolean loggedOut;

        @Override
        public String login(String userId, String tenantId) {
            loginTenantId = tenantId;
            return "token-" + userId;
        }

        @Override
        public void logout() {
            loggedOut = true;
        }
    }

    private static final class SingleUserRepository implements UserRepositoryPort {
        private final UserRecord user = new UserRecord(
                1L, "alice", "secret", "admin", null, "tenant-a", null, null);

        @Override
        public Optional<UserRecord> findById(Long id) {
            return Optional.of(user);
        }

        @Override
        public Optional<UserRecord> findByUsername(String username) {
            return "alice".equals(username) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public boolean usernameExists(String username, Long excludedId) {
            return false;
        }

        @Override
        public UserPage page(long current, long size, String keyword) {
            return new UserPage(java.util.List.of(user), 1, size, current, 1);
        }

        @Override
        public Long create(UserCreateValues values) {
            return 1L;
        }

        @Override
        public boolean update(Long id, UserUpdateValues values) {
            return true;
        }

        @Override
        public boolean delete(Long id) {
            return true;
        }
    }

    private static final class InMemoryRefreshTokenRepository implements RefreshTokenRepositoryPort {
        private Long userId;
        private String tenantId;
        private String refreshToken;
        private Instant expiresAt;
        private String revokedToken;

        @Override
        public void save(Long userId, String tenantId, String refreshToken, Instant expiresAt) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }

        @Override
        public Optional<RefreshTokenRecord> rotate(String refreshToken, String nextRefreshToken,
                                                   Instant nextExpiresAt, Instant now) {
            return Optional.empty();
        }

        @Override
        public void revoke(String refreshToken) {
            revokedToken = refreshToken;
        }
    }
}
