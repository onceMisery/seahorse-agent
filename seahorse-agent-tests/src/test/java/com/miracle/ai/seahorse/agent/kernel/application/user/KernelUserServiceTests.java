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

package com.miracle.ai.seahorse.agent.kernel.application.user;

import com.miracle.ai.seahorse.agent.ports.inbound.user.ChangePasswordCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelUserServiceTests {

    @Test
    void shouldRequireAdminWhenCreatingUser() {
        MemoryUserRepository repository = new MemoryUserRepository();
        KernelUserService service = new KernelUserService(repository, PasswordHasherPort.plainText(),
                () -> Optional.of(new CurrentUser("admin-1", "root", "admin", null)));

        String id = service.create(new UserCreateCommand("bob", "pw", null, null));

        assertThat(id).isEqualTo("new-1");
        assertThat(repository.created.role()).isEqualTo("user");
    }

    @Test
    void shouldRejectNonAdminForUserManagement() {
        KernelUserService service = new KernelUserService(new MemoryUserRepository(), PasswordHasherPort.plainText(),
                () -> Optional.of(new CurrentUser("user-1", "u", "user", null)));

        assertThatThrownBy(() -> service.page(1, 10, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("权限不足");
    }

    @Test
    void shouldChangeCurrentUserPassword() {
        MemoryUserRepository repository = new MemoryUserRepository();
        CurrentUserPort currentUserPort = () -> Optional.of(new CurrentUser("user-1", "alice", "user", null));
        KernelUserService service = new KernelUserService(repository, PasswordHasherPort.plainText(), currentUserPort);

        service.changePassword(new ChangePasswordCommand("old", "new"));

        assertThat(repository.updated.password()).isEqualTo("new");
    }

    private static class MemoryUserRepository implements UserRepositoryPort {
        private UserCreateValues created;
        private UserUpdateValues updated;
        private final UserRecord current = new UserRecord("user-1", "alice", "old", "user", null, null, null);

        @Override
        public Optional<UserRecord> findById(String id) {
            return Optional.of(current);
        }

        @Override
        public Optional<UserRecord> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public boolean usernameExists(String username, String excludedId) {
            return false;
        }

        @Override
        public UserPage page(long current, long size, String keyword) {
            return new UserPage(java.util.List.of(), 0, size, current, 0);
        }

        @Override
        public String create(UserCreateValues values) {
            created = values;
            return "new-1";
        }

        @Override
        public boolean update(String id, UserUpdateValues values) {
            updated = values;
            return true;
        }

        @Override
        public boolean delete(String id) {
            return true;
        }
    }
}
