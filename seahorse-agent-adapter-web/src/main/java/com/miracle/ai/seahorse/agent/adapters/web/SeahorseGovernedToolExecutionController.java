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

import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolExecutionPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SeahorseGovernedToolExecutionController {

    private final ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;
    private final CurrentUserPort currentUserPort;

    public SeahorseGovernedToolExecutionController(
            ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPortProvider) {
        this(governedToolExecutionPortProvider, AdvancedFeatureGate.allEnabledForTests(), null);
    }

    public SeahorseGovernedToolExecutionController(
            ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPortProvider,
            ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(governedToolExecutionPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults),
                null);
    }

    public SeahorseGovernedToolExecutionController(
            ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPortProvider,
            AdvancedFeatureGate advancedFeatureGate) {
        this(governedToolExecutionPortProvider, advancedFeatureGate, null);
    }

    @Autowired
    public SeahorseGovernedToolExecutionController(
            ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPortProvider,
            ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider,
            CurrentUserPort currentUserPort) {
        this(governedToolExecutionPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults),
                currentUserPort);
    }

    public SeahorseGovernedToolExecutionController(
            ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPortProvider,
            AdvancedFeatureGate advancedFeatureGate,
            CurrentUserPort currentUserPort) {
        this.governedToolExecutionPortProvider = governedToolExecutionPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
        this.currentUserPort = currentUserPort;
    }

    @PostMapping({"/tools/{toolId}/preflight", "/api/tools/{toolId}/preflight"})
    public ApiResponse<Object> preflight(@PathVariable String toolId,
                                         @RequestBody(required = false) ToolPreflightRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        advancedFeatureGate.requireEnabled(AdvancedFeature.TOOL_CATALOG_MANAGEMENT);
        ToolPreflightRequest safeRequest = request == null ? ToolPreflightRequest.empty() : request;
        return ApiResponses.requireService(governedToolExecutionPortProvider,
                port -> port.preflight(bindCurrentUser(safeRequest).toInvocationRequest(toolId)));
    }

    @PostMapping({"/tools/{toolId}/invoke", "/api/tools/{toolId}/invoke"})
    public ApiResponse<Object> invoke(@PathVariable String toolId,
                                      @RequestBody(required = false) ToolPreflightRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        advancedFeatureGate.requireEnabled(AdvancedFeature.TOOL_CATALOG_MANAGEMENT);
        ToolPreflightRequest safeRequest = request == null ? ToolPreflightRequest.empty() : request;
        return ApiResponses.requireService(governedToolExecutionPortProvider,
                port -> port.invoke(bindCurrentUser(safeRequest).toInvocationRequest(toolId)));
    }

    private ToolPreflightRequest bindCurrentUser(ToolPreflightRequest request) {
        if (currentUserPort == null) {
            return request;
        }
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        return request.withIdentity(currentUser.effectiveTenantId(), currentUser.operator());
    }

    public record ToolPreflightRequest(
            String runId,
            String stepId,
            String toolCallId,
            String agentId,
            String versionId,
            String rolloutId,
            String tenantId,
            String userId,
            String agentIdentityId,
            Map<String, Object> arguments,
            Map<String, String> resourceRefs,
            String idempotencyKey,
            List<String> allowedToolIds) {

        static ToolPreflightRequest empty() {
            return new ToolPreflightRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    Map.of(),
                    null,
                    List.of());
        }

        ToolPreflightRequest withIdentity(String effectiveTenantId, String effectiveUserId) {
            return new ToolPreflightRequest(
                    runId,
                    stepId,
                    toolCallId,
                    agentId,
                    versionId,
                    rolloutId,
                    effectiveTenantId,
                    effectiveUserId,
                    agentIdentityId,
                    arguments,
                    resourceRefs,
                    idempotencyKey,
                    allowedToolIds);
        }

        ToolInvocationRequest toInvocationRequest(String toolId) {
            return new ToolInvocationRequest(
                    runId,
                    stepId,
                    toolCallId,
                    agentId,
                    versionId,
                    rolloutId,
                    tenantId,
                    userId,
                    agentIdentityId,
                    toolId,
                    arguments,
                    resourceRefs,
                    idempotencyKey,
                    allowedToolIds);
        }
    }
}
