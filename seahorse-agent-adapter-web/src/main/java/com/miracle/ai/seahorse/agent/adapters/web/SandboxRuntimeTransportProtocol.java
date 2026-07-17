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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SandboxRuntimeTransportProtocol {

    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private SandboxRuntimeTransportProtocol() {
    }

    public record ExecutionResponse(SandboxExecution execution,
                                    List<ArtifactDescriptor> artifacts,
                                    SandboxPolicyReasonCode reasonCode) {

        public ExecutionResponse {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }

        public static ExecutionResponse from(SandboxExecutionResult result) {
            return new ExecutionResponse(
                    result.execution(),
                    result.artifacts().stream().map(ArtifactDescriptor::from).toList(),
                    result.reasonCode());
        }
    }

    public record ArtifactDescriptor(String artifactId,
                                     String sessionId,
                                     String executionId,
                                     String mediaType,
                                     SandboxArtifactScanStatus scanStatus,
                                     ContextSensitivity sensitivity,
                                     String scanSummary,
                                     String redactionSummaryJson,
                                     Instant createdAt) {

        public static ArtifactDescriptor from(SandboxArtifact artifact) {
            return new ArtifactDescriptor(
                    artifact.artifactId(),
                    artifact.sessionId(),
                    artifact.executionId(),
                    artifact.mediaType(),
                    artifact.scanStatus(),
                    artifact.sensitivity(),
                    artifact.scanSummary(),
                    artifact.redactionSummaryJson(),
                    artifact.createdAt());
        }

        public SandboxArtifact materialize(String objectUri) {
            return new SandboxArtifact(
                    artifactId,
                    sessionId,
                    executionId,
                    objectUri,
                    mediaType,
                    scanStatus,
                    sensitivity,
                    scanSummary,
                    redactionSummaryJson,
                    createdAt);
        }
    }

    public record ArtifactRequest(String sessionId, String executionId, String artifactId) {
    }

    public record SessionOwnershipRequest(String sessionId) {

        public SessionOwnershipRequest {
            sessionId = requireSessionId(sessionId);
        }
    }

    public record SessionOwnershipResponse(String sessionId, SandboxRuntimeSessionOwnership ownership) {

        public SessionOwnershipResponse {
            sessionId = requireSessionId(sessionId);
            ownership = Objects.requireNonNull(ownership, "ownership must not be null");
        }
    }

    private static String requireSessionId(String value) {
        if (value == null || !SESSION_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("sandbox transport session id is invalid");
        }
        return value;
    }
}
