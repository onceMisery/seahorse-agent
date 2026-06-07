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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.LoginHistoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

public class KernelAuthService implements AuthInboundPort {

    private static final Logger log = LoggerFactory.getLogger(KernelAuthService.class);
    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/37446017?v=4&size=64";
    private static final String LOGIN_TYPE_PASSWORD = "PASSWORD";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenServicePort tokenServicePort;
    private final LoginHistoryPort loginHistoryPort;
    private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public KernelAuthService(UserRepositoryPort userRepositoryPort,
                             PasswordHasherPort passwordHasherPort,
                             TokenServicePort tokenServicePort) {
        this(userRepositoryPort, passwordHasherPort, tokenServicePort, null);
    }

    public KernelAuthService(UserRepositoryPort userRepositoryPort,
                             PasswordHasherPort passwordHasherPort,
                             TokenServicePort tokenServicePort,
                             LoginHistoryPort loginHistoryPort) {
        this(userRepositoryPort, passwordHasherPort, tokenServicePort, loginHistoryPort, null, Clock.systemUTC());
    }

    public KernelAuthService(UserRepositoryPort userRepositoryPort,
                             PasswordHasherPort passwordHasherPort,
                             TokenServicePort tokenServicePort,
                             LoginHistoryPort loginHistoryPort,
                             RefreshTokenRepositoryPort refreshTokenRepositoryPort,
                             Clock clock) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort must not be null");
        this.passwordHasherPort = Objects.requireNonNull(passwordHasherPort, "passwordHasherPort must not be null");
        this.tokenServicePort = Objects.requireNonNull(tokenServicePort, "tokenServicePort must not be null");
        this.loginHistoryPort = loginHistoryPort;
        this.refreshTokenRepositoryPort = refreshTokenRepositoryPort;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public LoginResult login(LoginCommand command) {
        LoginCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String username = trimToNull(safeCommand.username());
        String password = trimToNull(safeCommand.password());
        if (username == null || password == null) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        UserRecord user = userRepositoryPort.findByUsername(username)
                .orElseThrow(() -> {
                    safeRecordLogin(safeCommand, 0L, "default", LOGIN_TYPE_PASSWORD, STATUS_FAILED, "用户不存在");
                    return new IllegalArgumentException("用户名或密码错误");
                });
        if (!passwordHasherPort.matches(password, user.password())) {
            safeRecordLogin(safeCommand, user.id() != null ? user.id() : 0L, user.tenantId(),
                    LOGIN_TYPE_PASSWORD, STATUS_FAILED, "密码错误");
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.id() == null) {
            throw new IllegalStateException("用户信息异常");
        }
        String token = tokenServicePort.login(String.valueOf(user.id()), user.tenantId());
        safeRecordLogin(safeCommand, user.id(), user.tenantId(), LOGIN_TYPE_PASSWORD, STATUS_SUCCESS, null);
        if (refreshTokenRepositoryPort == null) {
            return new LoginResult(String.valueOf(user.id()), user.role(), token,
                    defaultAvatar(user.avatar()), user.tenantId());
        }
        Instant expiresAt = clock.instant().plus(REFRESH_TOKEN_TTL);
        String refreshToken = generateRefreshToken();
        refreshTokenRepositoryPort.save(user.id(), refreshToken, expiresAt);
        return new LoginResult(String.valueOf(user.id()), user.role(), token,
                defaultAvatar(user.avatar()), user.tenantId(), refreshToken, expiresAt);
    }

    @Override
    public void logout() {
        tokenServicePort.logout();
    }

    private void safeRecordLogin(LoginCommand command, long userId, String tenantId, String loginType,
                                 String status, String failureReason) {
        if (loginHistoryPort == null) {
            return;
        }
        try {
            String ipAddress = command != null ? command.ipAddress() : null;
            String userAgent = command != null ? command.userAgent() : null;
            String deviceInfo = command != null ? command.deviceInfo() : null;
            loginHistoryPort.recordLogin(userId, tenantId, loginType, ipAddress, userAgent, deviceInfo, status, failureReason);
        } catch (Exception e) {
            log.warn("Failed to record login history: {}", e.getMessage());
        }
    }

    private String defaultAvatar(String avatar) {
        return avatar == null || avatar.isBlank() ? DEFAULT_AVATAR_URL : avatar;
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
