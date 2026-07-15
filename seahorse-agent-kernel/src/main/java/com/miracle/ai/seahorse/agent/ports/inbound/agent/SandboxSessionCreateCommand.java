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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record SandboxSessionCreateCommand(String tenantId,
                                          String runId,
                                          SandboxRuntimeType runtimeType,
                                          boolean networkRequested,
                                          List<String> requestedHosts,
                                          String profileId,
                                          Instant expiresAt,
                                          String requiredRuntimeNodeId) {

    private static final Pattern RUNTIME_NODE_ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public SandboxSessionCreateCommand(String tenantId,
                                       String runId,
                                       SandboxRuntimeType runtimeType,
                                       boolean networkRequested,
                                       List<String> requestedHosts) {
        this(tenantId, runId, runtimeType, networkRequested, requestedHosts, null, null, null);
    }

    public SandboxSessionCreateCommand(String tenantId,
                                       String runId,
                                       SandboxRuntimeType runtimeType,
                                       boolean networkRequested,
                                       List<String> requestedHosts,
                                       String profileId,
                                       Instant expiresAt) {
        this(tenantId, runId, runtimeType, networkRequested, requestedHosts, profileId, expiresAt, null);
    }

    public SandboxSessionCreateCommand {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        runId = requireText(runId, "runId must not be blank");
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        requestedHosts = requestedHosts == null ? List.of() : List.copyOf(requestedHosts);
        profileId = profileId == null || profileId.trim().isEmpty() ? null : profileId.trim();
        requiredRuntimeNodeId = normalizeRuntimeNodeId(requiredRuntimeNodeId);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeRuntimeNodeId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.equals(value) || !RUNTIME_NODE_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "requiredRuntimeNodeId must use 1-64 lowercase letters, numbers, dots, underscores, or hyphens");
        }
        return normalized;
    }
}
