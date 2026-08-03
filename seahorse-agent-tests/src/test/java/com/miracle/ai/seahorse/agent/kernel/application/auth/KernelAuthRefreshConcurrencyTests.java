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
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KernelAuthRefreshConcurrencyTests {

    private static final Instant NOW = Instant.parse("2026-06-06T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldRotateRefreshTokenAndIssueNewAccessToken() {
        AtomicRefreshTokenRepository repository = new AtomicRefreshTokenRepository(validRecord());
        CountingTokenService tokenService = new CountingTokenService();
        KernelAuthService service = service(repository, tokenService);

        RefreshTokenResult result = service.refresh(new RefreshTokenCommand("old-refresh"));

        assertThat(result.userId()).isEqualTo("1");
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("old-refresh");
        assertThat(repository.currentToken()).isEqualTo(result.refreshToken());
        assertThat(tokenService.loginCount()).isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyOneConcurrentRefreshForTheSameToken() throws Exception {
        AtomicRefreshTokenRepository repository = new AtomicRefreshTokenRepository(validRecord());
        CountingTokenService tokenService = new CountingTokenService();
        KernelAuthService service = service(repository, tokenService);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> refreshAfter(start, service));
            Future<Object> second = executor.submit(() -> refreshAfter(start, service));
            start.countDown();

            List<Object> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).filteredOn(RefreshTokenResult.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(IllegalArgumentException.class::isInstance).hasSize(1);
            assertThat(tokenService.loginCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectInvalidRefreshToken() {
        KernelAuthService service = service(new AtomicRefreshTokenRepository(null), new CountingTokenService());

        assertThatThrownBy(() -> service.refresh(new RefreshTokenCommand("missing-refresh")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    private Object refreshAfter(CountDownLatch start, KernelAuthService service) throws InterruptedException {
        start.await();
        try {
            return service.refresh(new RefreshTokenCommand("old-refresh"));
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    private KernelAuthService service(RefreshTokenRepositoryPort repository, TokenServicePort tokenService) {
        return new KernelAuthService(
                mock(UserRepositoryPort.class),
                PasswordHasherPort.plainText(),
                tokenService,
                null,
                repository,
                FIXED_CLOCK);
    }

    private RefreshTokenRecord validRecord() {
        return new RefreshTokenRecord(
                1L, "alice", "admin", null, "tenant-a", "old-refresh", NOW.plusSeconds(60));
    }

    private static final class CountingTokenService implements TokenServicePort {
        private final AtomicInteger loginCount = new AtomicInteger();

        @Override
        public String login(String userId, String tenantId) {
            loginCount.incrementAndGet();
            return "token-" + userId;
        }

        @Override
        public void logout() {
        }

        private int loginCount() {
            return loginCount.get();
        }
    }

    private static final class AtomicRefreshTokenRepository implements RefreshTokenRepositoryPort {
        private final AtomicReference<RefreshTokenRecord> record;

        private AtomicRefreshTokenRepository(RefreshTokenRecord record) {
            this.record = new AtomicReference<>(record);
        }

        @Override
        public void save(Long userId, String tenantId, String refreshToken, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RefreshTokenRecord> rotate(String refreshToken, String nextRefreshToken,
                                                   Instant nextExpiresAt, Instant now) {
            while (true) {
                RefreshTokenRecord current = record.get();
                if (current == null
                        || !current.refreshToken().equals(refreshToken)
                        || !current.refreshTokenExpiresAt().isAfter(now)) {
                    return Optional.empty();
                }
                RefreshTokenRecord next = new RefreshTokenRecord(
                        current.userId(), current.username(), current.role(), current.avatar(), current.tenantId(),
                        nextRefreshToken, nextExpiresAt);
                if (record.compareAndSet(current, next)) {
                    return Optional.of(current);
                }
            }
        }

        @Override
        public void revoke(String refreshToken) {
            record.updateAndGet(current -> current != null && current.refreshToken().equals(refreshToken)
                    ? null
                    : current);
        }

        private String currentToken() {
            return record.get().refreshToken();
        }
    }
}
