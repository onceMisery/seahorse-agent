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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record SandboxSession(String sessionId,
                             String tenantId,
                             String runId,
                             SandboxRuntimeType runtimeType,
                             SandboxExecutionStatus status,
                             SandboxPolicyReasonCode reasonCode,
                             String profileId,
                             Instant expiresAt,
                             Instant createdAt,
                             Instant updatedAt) {

    public static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(1);

    public SandboxSession(String sessionId,
                          String tenantId,
                          String runId,
                          SandboxRuntimeType runtimeType,
                          SandboxExecutionStatus status,
                          SandboxPolicyReasonCode reasonCode,
                          Instant createdAt,
                          Instant updatedAt) {
        this(sessionId, tenantId, runId, runtimeType, status, reasonCode, null, null, createdAt, updatedAt);
    }

    public SandboxSession {
        sessionId = requireText(sessionId, "sessionId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        runId = requireText(runId, "runId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        status = Objects.requireNonNullElse(status, SandboxExecutionStatus.CREATED);
        reasonCode = Objects.requireNonNullElse(reasonCode, SandboxPolicyReasonCode.VALID_REQUEST);
        profileId = profileIdOrDefault(profileId, runtimeType);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        expiresAt = normalizeExpiresAt(expiresAt, createdAt);
        updatedAt = Objects.requireNonNullElse(updatedAt, createdAt);
    }

    public static SandboxSession created(String sessionId,
                                         String tenantId,
                                         String runId,
                                         SandboxRuntimeType runtimeType,
                                         Instant createdAt) {
        return new SandboxSession(sessionId, tenantId, runId, runtimeType,
                SandboxExecutionStatus.CREATED, SandboxPolicyReasonCode.VALID_REQUEST, createdAt, createdAt);
    }

    public static SandboxSession created(String sessionId,
                                         String tenantId,
                                         String runId,
                                         SandboxRuntimeType runtimeType,
                                         String profileId,
                                         Instant expiresAt,
                                         Instant createdAt) {
        return new SandboxSession(sessionId, tenantId, runId, runtimeType,
                SandboxExecutionStatus.CREATED, SandboxPolicyReasonCode.VALID_REQUEST,
                profileId, expiresAt, createdAt, createdAt);
    }

    public static SandboxSession failed(String sessionId,
                                        String tenantId,
                                        String runId,
                                        SandboxRuntimeType runtimeType,
                                        SandboxPolicyReasonCode reasonCode,
                                        Instant createdAt) {
        return new SandboxSession(sessionId, tenantId, runId, runtimeType,
                SandboxExecutionStatus.FAILED, reasonCode, createdAt, createdAt);
    }

    public static SandboxSession failed(String sessionId,
                                        String tenantId,
                                        String runId,
                                        SandboxRuntimeType runtimeType,
                                        SandboxPolicyReasonCode reasonCode,
                                        String profileId,
                                        Instant expiresAt,
                                        Instant createdAt) {
        return new SandboxSession(sessionId, tenantId, runId, runtimeType,
                SandboxExecutionStatus.FAILED, reasonCode, profileId, expiresAt, createdAt, createdAt);
    }

    public SandboxSession closed(Instant closedAt) {
        return new SandboxSession(
                sessionId,
                tenantId,
                runId,
                runtimeType,
                SandboxExecutionStatus.CANCELLED,
                reasonCode,
                profileId,
                expiresAt,
                createdAt,
                Objects.requireNonNullElse(closedAt, updatedAt));
    }

    public SandboxSession withRuntimeGovernance(String profileId, Instant expiresAt) {
        return new SandboxSession(
                sessionId,
                tenantId,
                runId,
                runtimeType,
                status,
                reasonCode,
                profileId,
                expiresAt,
                createdAt,
                updatedAt);
    }

    public static String profileIdOrDefault(String profileId, SandboxRuntimeType runtimeType) {
        if (profileId != null && !profileId.trim().isEmpty()) {
            return profileId.trim();
        }
        return switch (Objects.requireNonNull(runtimeType, "runtimeType must not be null")) {
            case CODE_INTERPRETER -> "python-small";
            case BROWSER_AUTOMATION -> "browser-readonly";
            case FILE_CONVERSION -> "file-conversion";
            case SHELL -> "shell-restricted";
        };
    }

    public static Instant defaultExpiresAt(Instant createdAt) {
        return Objects.requireNonNull(createdAt, "createdAt must not be null").plus(DEFAULT_SESSION_TTL);
    }

    private static Instant normalizeExpiresAt(Instant expiresAt, Instant createdAt) {
        if (expiresAt == null || expiresAt.isBefore(createdAt)) {
            return defaultExpiresAt(createdAt);
        }
        return expiresAt;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
