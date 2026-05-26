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

public record SandboxExecution(String executionId,
                               String sessionId,
                               SandboxRuntimeType runtimeType,
                               SandboxExecutionStatus status,
                               String resultSummary,
                               SandboxPolicyReasonCode reasonCode,
                               Instant createdAt,
                               Instant updatedAt) {

    public SandboxExecution {
        executionId = requireText(executionId, "executionId must not be blank");
        sessionId = requireText(sessionId, "sessionId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        status = Objects.requireNonNullElse(status, SandboxExecutionStatus.CREATED);
        resultSummary = trimToNull(resultSummary);
        reasonCode = Objects.requireNonNullElse(reasonCode, SandboxPolicyReasonCode.VALID_REQUEST);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNullElse(updatedAt, createdAt);
    }

    public static SandboxExecution created(String executionId,
                                           String sessionId,
                                           SandboxRuntimeType runtimeType,
                                           Instant createdAt) {
        return new SandboxExecution(executionId, sessionId, runtimeType,
                SandboxExecutionStatus.CREATED, null, SandboxPolicyReasonCode.VALID_REQUEST, createdAt, createdAt);
    }

    public static SandboxExecution failed(String executionId,
                                          String sessionId,
                                          SandboxRuntimeType runtimeType,
                                          Instant createdAt,
                                          SandboxPolicyReasonCode reasonCode) {
        return new SandboxExecution(executionId, sessionId, runtimeType,
                SandboxExecutionStatus.FAILED, null, reasonCode, createdAt, createdAt);
    }

    public SandboxExecution markRunning(Instant updatedAt) {
        if (status != SandboxExecutionStatus.CREATED) {
            throw new IllegalStateException("Sandbox execution must be CREATED before running");
        }
        return new SandboxExecution(executionId, sessionId, runtimeType, SandboxExecutionStatus.RUNNING,
                resultSummary, reasonCode, createdAt, updatedAt);
    }

    public SandboxExecution markSucceeded(Instant updatedAt, String resultSummary) {
        requireRunningBeforeCompletion();
        return new SandboxExecution(executionId, sessionId, runtimeType, SandboxExecutionStatus.SUCCEEDED,
                resultSummary, SandboxPolicyReasonCode.VALID_REQUEST, createdAt, updatedAt);
    }

    public SandboxExecution markFailed(Instant updatedAt, SandboxPolicyReasonCode reasonCode) {
        if (status != SandboxExecutionStatus.CREATED && status != SandboxExecutionStatus.RUNNING) {
            throw new IllegalStateException("Sandbox execution is already terminal");
        }
        return new SandboxExecution(executionId, sessionId, runtimeType, SandboxExecutionStatus.FAILED,
                resultSummary, reasonCode, createdAt, updatedAt);
    }

    private void requireRunningBeforeCompletion() {
        if (status != SandboxExecutionStatus.RUNNING) {
            throw new IllegalStateException("Sandbox execution must be RUNNING before completion");
        }
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
