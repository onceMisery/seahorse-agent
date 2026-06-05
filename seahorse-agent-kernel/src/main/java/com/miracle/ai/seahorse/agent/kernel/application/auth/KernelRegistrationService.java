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

import com.miracle.ai.seahorse.agent.kernel.domain.user.UserTrial;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.email.EmailSenderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.tenant.TenantProvisioningPort;
import com.miracle.ai.seahorse.agent.ports.outbound.user.TrialRepositoryPort;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kernel-level registration service implementing the full sign-up flow:
 * <ol>
 *   <li>Email verification code generation and delivery</li>
 *   <li>Tenant provisioning</li>
 *   <li>User creation with encoded password</li>
 *   <li>Free-trial activation (14 days)</li>
 *   <li>Automatic login via token</li>
 * </ol>
 */
public class KernelRegistrationService implements RegistrationInboundPort {

    private static final int CODE_LENGTH = 6;
    private static final long CODE_TTL_MINUTES = 5;
    private static final int BOUND = 1_000_000;
    private static final String USER_ROLE = "user";

    /** Default trial configuration. */
    private static final int TRIAL_DAYS = 14;
    private static final long TRIAL_TOKEN_LIMIT = 1_000_000L;
    private static final long TRIAL_STORAGE_LIMIT_BYTES = 1_073_741_824L;
    private static final int TRIAL_CONCURRENCY_LIMIT = 5;
    private static final String TRIAL_PLAN_CODE = "FREE_TRIAL";

    private final UserRepositoryPort userRepositoryPort;
    private final TokenServicePort tokenServicePort;
    private final EmailSenderPort emailSenderPort;
    private final TenantProvisioningPort tenantProvisioningPort;
    private final TrialRepositoryPort trialRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;

    private final ConcurrentHashMap<String, CodeEntry> codeStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public KernelRegistrationService(UserRepositoryPort userRepositoryPort,
                                     TokenServicePort tokenServicePort,
                                     EmailSenderPort emailSenderPort,
                                     TenantProvisioningPort tenantProvisioningPort,
                                     TrialRepositoryPort trialRepositoryPort,
                                     PasswordHasherPort passwordHasherPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort must not be null");
        this.tokenServicePort = Objects.requireNonNull(tokenServicePort, "tokenServicePort must not be null");
        this.emailSenderPort = Objects.requireNonNull(emailSenderPort, "emailSenderPort must not be null");
        this.tenantProvisioningPort = Objects.requireNonNull(tenantProvisioningPort, "tenantProvisioningPort must not be null");
        this.trialRepositoryPort = Objects.requireNonNull(trialRepositoryPort, "trialRepositoryPort must not be null");
        this.passwordHasherPort = Objects.requireNonNull(passwordHasherPort, "passwordHasherPort must not be null");
    }

    @Override
    public void sendVerificationCode(String email) {
        String safeEmail = requireEmail(email);
        if (userRepositoryPort.usernameExists(safeEmail, null)) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
        String code = generateCode();
        Instant expiresAt = Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES);
        codeStore.put(safeEmail, new CodeEntry(code, expiresAt));
        emailSenderPort.sendVerificationCode(safeEmail, code);
    }

    @Override
    public RegistrationResult register(String email, String code, String password) {
        String safeEmail = requireEmail(email);
        String safeCode = requireText(code, "验证码不能为空");
        String safePassword = requireText(password, "密码不能为空");

        verifyCode(safeEmail, safeCode);

        if (userRepositoryPort.usernameExists(safeEmail, null)) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }

        // 1. Create user (email serves as username)
        String encodedPassword = passwordHasherPort.encode(safePassword);
        Long userId = userRepositoryPort.create(
                new UserCreateValues(safeEmail, encodedPassword, USER_ROLE, null));
        if (userId == null) {
            throw new IllegalStateException("用户创建失败");
        }

        // 2. Provision tenant for the new user
        String tenantId = tenantProvisioningPort.provisionTenant(String.valueOf(userId), safeEmail);

        // 3. Activate free trial
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TRIAL_DAYS, ChronoUnit.DAYS);
        UserTrial trial = new UserTrial(
                null, tenantId, userId, TRIAL_PLAN_CODE, UserTrial.STATUS_ACTIVE,
                TRIAL_TOKEN_LIMIT, TRIAL_STORAGE_LIMIT_BYTES, TRIAL_CONCURRENCY_LIMIT,
                now, expiresAt);
        trialRepositoryPort.save(trial);

        // 4. Login and generate token
        String token = tokenServicePort.login(String.valueOf(userId), tenantId);

        // 5. Clean up used code
        codeStore.remove(safeEmail);

        return new RegistrationResult(String.valueOf(userId), token, tenantId, expiresAt);
    }

    @Override
    public boolean isEmailAvailable(String email) {
        String safeEmail = requireEmail(email);
        return !userRepositoryPort.usernameExists(safeEmail, null);
    }

    // ─── private helpers ──────────────────────────────────────────────────

    private void verifyCode(String email, String code) {
        CodeEntry entry = codeStore.get(email);
        if (entry == null) {
            throw new IllegalArgumentException("请先获取验证码");
        }
        if (Instant.now().isAfter(entry.expiresAt)) {
            codeStore.remove(email);
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!entry.code.equals(code)) {
            throw new IllegalArgumentException("验证码不正确");
        }
    }

    private String generateCode() {
        int code = secureRandom.nextInt(BOUND);
        return String.format("%0" + CODE_LENGTH + "d", code);
    }

    private String requireEmail(String email) {
        String trimmed = requireText(email, "邮箱不能为空");
        if (!trimmed.contains("@")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return trimmed.toLowerCase();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /** Internal holder for a verification code and its expiry timestamp. */
    private record CodeEntry(String code, Instant expiresAt) {
    }
}
