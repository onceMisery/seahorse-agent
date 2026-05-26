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

@RestController
public class SeahorseSandboxController {

    private final ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                     ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(sandboxRuntimePortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseSandboxController(ObjectProvider<SandboxRuntimeInboundPort> sandboxRuntimePortProvider,
                                     AdvancedFeatureGate advancedFeatureGate) {
        this.sandboxRuntimePortProvider = sandboxRuntimePortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
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
                port -> port.execute(new SandboxExecutionCommand(
                        sessionId,
                        safeRequest.input(),
                        safeRequest.networkRequested(),
                        safeRequest.requestedHosts())));
    }

    @PostMapping("/api/sandbox/sessions/{sessionId}/close")
    public ApiResponse<Object> close(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.close(sessionId));
    }

    @GetMapping("/api/sandbox/sessions/{sessionId}/artifacts")
    public ApiResponse<Object> listArtifacts(@PathVariable String sessionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SANDBOX);
        return ApiResponses.requireService(sandboxRuntimePortProvider, port -> port.listArtifacts(sessionId));
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
}
