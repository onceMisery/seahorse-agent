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

import java.time.Instant;
import java.util.List;

public final class SandboxRuntimeTransportProtocol {

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
}
