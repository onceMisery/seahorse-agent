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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;

@RestController
public class SeahorseSandboxController {

    private final ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                     ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(sandboxRuntimePortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                     AdvancedFeatureGate advancedFeatureGate) {
        this.sandboxRuntimePortProvider = sandboxRuntimePortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/sandbox/sessions")
    public ApiResponse<Object> createSession(@RequestBody SandboxSessionCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxSessionCreateRequest safeRequest = request == null
                ? new SandboxSessionCreateRequest(null, null, null, false, List.of())
                : request;
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> port.createSession(new SandboxSessionCreateCommand(
                        safeRequest.tenantId(),
                        safeRequest.runId(),
                        safeRequest.runtimeType(),
                        safeRequest.networkRequested(),
                        safeRequest.requestedHosts())));
    }

    @PostMapping("/api/sandbox/sessions/{sessionId}/execute")
    public ApiResponse<Object> execute(@PathVariable String sessionId,
                                       @RequestBody SandboxExecutionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        SandboxExecutionRequest safeRequest = request == null
                ? new SandboxExecutionRequest(null, false, List.of())
                : request;
        return ApiResponses.requireService(sandboxRuntimePortProvider,
                port -> toResponse(port.execute(new SandboxExecutionCommand(
                        sessionId,
                        safeRequest.input(),
                        safeRequest.networkRequested(),
                        safeRequest.requestedHosts()))));
    }

    @PostMapping("/api/sandbox/sessions/{sessionId}/close")
    public ApiResponse<Object> close(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.close(sessionId));
    }

    @GetMapping("/api/sandbox/sessions/{sessionId}/executions")
    public ApiResponse<Object> listExecutions(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.listExecutions(sessionId));
    }

    @GetMapping("/api/sandbox/sessions/{sessionId}/artifacts")
    public ApiResponse<Object> listArtifacts(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.listArtifacts(sessionId).stream()
                .map(SeahorseSandboxController::toResponse)
                .toList());
    }

    private static SandboxExecutionResultResponse toResponse(SandboxExecutionResult result) {
        return new SandboxExecutionResultResponse(
                result.execution(),
                result.artifacts().stream()
                        .map(SeahorseSandboxController::toResponse)
                        .toList(),
                result.reasonCode());
    }

    private static SandboxArtifactResponse toResponse(SandboxArtifact artifact) {
        return new SandboxArtifactResponse(
                artifact.artifactId(),
                artifact.sessionId(),
                artifact.executionId(),
                artifact.mediaType(),
                artifact.scanStatus(),
                artifact.sensitivity(),
                artifact.createdAt());
    }

    public record SandboxSessionCreateRequest(String tenantId,
                                              String runId,
                                              SandboxRuntimeType runtimeType,
                                              boolean networkRequested,
                                              List<String> requestedHosts) {

        public SandboxSessionCreateRequest {
            requestedHosts = requestedHosts == null ? List.of() : List.copyOf(requestedHosts);
        }
    }

    public record SandboxExecutionRequest(String input,
                                          boolean networkRequested,
                                          List<String> requestedHosts) {

        public SandboxExecutionRequest {
            input = input == null ? "" : input;
            requestedHosts = requestedHosts == null ? List.of() : List.copyOf(requestedHosts);
        }
    }

    public record SandboxExecutionResultResponse(SandboxExecution execution,
                                                 List<SandboxArtifactResponse> artifacts,
                                                 SandboxPolicyReasonCode reasonCode) {

        public SandboxExecutionResultResponse {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }

    public record SandboxArtifactResponse(String artifactId,
                                          String sessionId,
                                          String executionId,
                                          String mediaType,
                                          SandboxArtifactScanStatus scanStatus,
                                          ContextSensitivity sensitivity,
                                          Instant createdAt) {
    }
}
