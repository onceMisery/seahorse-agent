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
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserUpdateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;

import java.util.Objects;

public class KernelUserService implements UserInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String USER_ROLE = "user";
    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final CurrentUserPort currentUserPort;

    public KernelUserService(UserRepositoryPort userRepositoryPort,
                             PasswordHasherPort passwordHasherPort,
                             CurrentUserPort currentUserPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort must not be null");
        this.passwordHasherPort = Objects.requireNonNull(passwordHasherPort, "passwordHasherPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public CurrentUser currentUser() {
        return currentUserPort.requireCurrentUser();
    }

    @Override
    public UserPage page(long current, long size, String keyword) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return userRepositoryPort.page(current, size, keyword);
    }

    @Override
    public String create(UserCreateCommand command) {
        currentUserPort.requireRole(ADMIN_ROLE);
        UserCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String username = requireText(safeCommand.username(), "用户名不能为空");
        String password = requireText(safeCommand.password(), "密码不能为空");
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("默认管理员用户名不可用");
        }
        String role = normalizeRole(safeCommand.role());
        ensureUsernameAvailable(username, null);
        return userRepositoryPort.create(new UserCreateValues(
                username, passwordHasherPort.encode(password), role, trimToNull(safeCommand.avatar())));
    }

    @Override
    public void update(String id, UserUpdateCommand command) {
        currentUserPort.requireRole(ADMIN_ROLE);
        UserUpdateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        UserRecord record = loadById(id);
        ensureNotDefaultAdmin(record);
        String username = normalizeUsernameForUpdate(record, safeCommand.username());
        String password = safeCommand.password() == null ? null
                : passwordHasherPort.encode(requireText(safeCommand.password(), "新密码不能为空"));
        String role = safeCommand.role() == null ? null : normalizeRole(safeCommand.role());
        userRepositoryPort.update(id, new UserUpdateValues(username, password, role, trimToNull(safeCommand.avatar())));
    }

    @Override
    public void delete(String id) {
        currentUserPort.requireRole(ADMIN_ROLE);
        UserRecord record = loadById(id);
        ensureNotDefaultAdmin(record);
        if (!userRepositoryPort.delete(id)) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        ChangePasswordCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String current = requireText(safeCommand.currentPassword(), "当前密码不能为空");
        String next = requireText(safeCommand.newPassword(), "新密码不能为空");
        CurrentUser user = currentUserPort.requireCurrentUser();
        UserRecord record = userRepositoryPort.findById(user.userId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordHasherPort.matches(current, record.password())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        userRepositoryPort.update(record.id(), new UserUpdateValues(null, passwordHasherPort.encode(next), null, null));
    }

    private UserRecord loadById(String id) {
        return userRepositoryPort.findById(requireText(id, "用户 ID 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private void ensureNotDefaultAdmin(UserRecord record) {
        if (record != null && DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(record.username())) {
            throw new IllegalArgumentException("默认管理员不允许修改或删除");
        }
    }

    private String normalizeUsernameForUpdate(UserRecord record, String requestedUsername) {
        if (requestedUsername == null) {
            return null;
        }
        String username = requireText(requestedUsername, "用户名不能为空");
        if (!username.equals(record.username())) {
            if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
                throw new IllegalArgumentException("默认管理员用户名不可用");
            }
            ensureUsernameAvailable(username, record.id());
        }
        return username;
    }

    private void ensureUsernameAvailable(String username, String excludedId) {
        if (userRepositoryPort.usernameExists(username, excludedId)) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    private String normalizeRole(String role) {
        String value = trimToNull(role);
        if (value == null) {
            return USER_ROLE;
        }
        if (ADMIN_ROLE.equalsIgnoreCase(value)) {
            return ADMIN_ROLE;
        }
        if (USER_ROLE.equalsIgnoreCase(value)) {
            return USER_ROLE;
        }
        throw new IllegalArgumentException("角色类型不合法");
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
