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
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelAuthServiceTests {

    @Test
    void shouldLoginWithCompatiblePayload() {
        FakeTokenService tokenService = new FakeTokenService();
        KernelAuthService service = new KernelAuthService(new SingleUserRepository(),
                PasswordHasherPort.plainText(), tokenService);

        LoginResult result = service.login(new LoginCommand("alice", "secret"));

        assertThat(result.userId()).isEqualTo("1");
        assertThat(result.role()).isEqualTo("admin");
        assertThat(result.token()).isEqualTo("token-1");
        assertThat(result.avatar()).isNotBlank();
        assertThat(tokenService.loginId).isEqualTo("1");
    }

    @Test
    void shouldRejectInvalidPassword() {
        KernelAuthService service = new KernelAuthService(new SingleUserRepository(),
                PasswordHasherPort.plainText(), new FakeTokenService());

        assertThatThrownBy(() -> service.login(new LoginCommand("alice", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名或密码错误");
    }

    private static class FakeTokenService implements TokenServicePort {
        private String loginId;

        @Override
        public String login(String userId, String tenantId) {
            loginId = userId;
            return "token-" + userId;
        }

        @Override
        public void logout() {
        }
    }

    private static class SingleUserRepository implements UserRepositoryPort {
        private final UserRecord user = new UserRecord(1L, "alice", "secret", "admin", null, null, null);

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
}
