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
import java.util.Objects;

public record SandboxSession(String sessionId,
                             String tenantId,
                             String runId,
                             SandboxRuntimeType runtimeType,
                             SandboxExecutionStatus status,
                             SandboxPolicyReasonCode reasonCode,
                             Instant createdAt,
                             Instant updatedAt) {

    public SandboxSession {
        sessionId = requireText(sessionId, "sessionId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        runId = requireText(runId, "runId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        status = Objects.requireNonNullElse(status, SandboxExecutionStatus.CREATED);
        reasonCode = Objects.requireNonNullElse(reasonCode, SandboxPolicyReasonCode.VALID_REQUEST);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
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

    public static SandboxSession failed(String sessionId,
                                        String tenantId,
                                        String runId,
                                        SandboxRuntimeType runtimeType,
                                        SandboxPolicyReasonCode reasonCode,
                                        Instant createdAt) {
        return new SandboxSession(sessionId, tenantId, runId, runtimeType,
                SandboxExecutionStatus.FAILED, reasonCode, createdAt, createdAt);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
