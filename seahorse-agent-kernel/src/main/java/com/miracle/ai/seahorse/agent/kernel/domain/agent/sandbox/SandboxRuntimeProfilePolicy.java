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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record SandboxRuntimeProfilePolicy(String policyId,
                                          String tenantId,
                                          SandboxRuntimeType runtimeType,
                                          String profileId,
                                          SandboxRuntimeProfilePolicyStatus status,
                                          long sessionTtlSeconds,
                                          boolean networkAllowed,
                                          Instant createdAt,
                                          Instant updatedAt) {

    public static final long DEFAULT_SESSION_TTL_SECONDS = SandboxSession.DEFAULT_SESSION_TTL.toSeconds();
    public static final long MIN_SESSION_TTL_SECONDS = 60;
    public static final long MAX_SESSION_TTL_SECONDS = 7200;

    public SandboxRuntimeProfilePolicy {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        policyId = hasText(policyId) ? policyId.trim() : defaultPolicyId(tenantId, runtimeType);
        profileId = SandboxSession.profileIdOrDefault(profileId, runtimeType);
        status = Objects.requireNonNullElse(status, SandboxRuntimeProfilePolicyStatus.ACTIVE);
        if (sessionTtlSeconds < MIN_SESSION_TTL_SECONDS || sessionTtlSeconds > MAX_SESSION_TTL_SECONDS) {
            throw new IllegalArgumentException("sessionTtlSeconds must be between 60 and 7200");
        }
        if (networkAllowed) {
            throw new IllegalArgumentException("networkAllowed must remain false until sandbox egress policy is available");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNullElse(updatedAt, createdAt);
    }

    public static SandboxRuntimeProfilePolicy defaultPolicy(String tenantId,
                                                            SandboxRuntimeType runtimeType,
                                                            Instant now) {
        return new SandboxRuntimeProfilePolicy(
                defaultPolicyId(tenantId, runtimeType),
                tenantId,
                runtimeType,
                SandboxSession.profileIdOrDefault(null, runtimeType),
                SandboxRuntimeProfilePolicyStatus.ACTIVE,
                DEFAULT_SESSION_TTL_SECONDS,
                false,
                now,
                now);
    }

    public static String defaultPolicyId(String tenantId, SandboxRuntimeType runtimeType) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return "sandbox-runtime-profile-" + safeTenantId + "-"
                + Objects.requireNonNull(runtimeType, "runtimeType must not be null")
                .name()
                .toLowerCase(Locale.ROOT);
    }

    public boolean allowsExecution() {
        return status.allowsExecution();
    }

    public Instant effectiveExpiresAt(Instant requestedExpiresAt, Instant now) {
        Instant safeNow = Objects.requireNonNull(now, "now must not be null");
        Instant policyExpiresAt = safeNow.plusSeconds(sessionTtlSeconds);
        if (requestedExpiresAt == null || !requestedExpiresAt.isAfter(safeNow)
                || requestedExpiresAt.isAfter(policyExpiresAt)) {
            return policyExpiresAt;
        }
        return requestedExpiresAt;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
