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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class SeahorseAgentDefinitionController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseAgentDefinitionController(ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider) {
        this(agentDefinitionPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseAgentDefinitionController(ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider,
                                             ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(agentDefinitionPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseAgentDefinitionController(ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider,
                                             AdvancedFeatureGate advancedFeatureGate) {
        this.agentDefinitionPortProvider = agentDefinitionPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping({"/agents", "/api/agents"})
    public ApiResponse<Object> create(@RequestBody AgentDefinitionCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        AgentDefinitionCreateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");

        // Auto-generate agentId if not provided
        String agentId = safeRequest.agentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = SnowflakeIds.nextIdString();
        }

        // Auto-fill ownerUserId from Sa-Token session if not provided
        String ownerUserId = safeRequest.ownerUserId();
        if (ownerUserId == null || ownerUserId.isBlank()) {
            ownerUserId = WebUserIdResolver.resolve(null, null);
        }

        // Auto-fill tenantId from TenantContext if not provided
        String tenantId = safeRequest.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            String ctxTenant = TenantContext.get();
            tenantId = (ctxTenant != null && !ctxTenant.isBlank()) ? ctxTenant : "default";
        }

        // Validate required fields at web layer for friendly 400 errors
        String name = safeRequest.name();
        if (name == null || name.isBlank()) {
            return ApiResponse.error("name is required and must not be blank");
        }

        String finalAgentId = agentId;
        String finalOwnerUserId = ownerUserId;
        String finalTenantId = tenantId;
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.createDraft(new AgentDefinitionCreateCommand(
                finalAgentId,
                finalTenantId,
                safeRequest.name(),
                safeRequest.description(),
                finalOwnerUserId,
                safeRequest.ownerTeam(),
                safeRequest.agentType(),
                safeRequest.baseAgentId(),
                safeRequest.riskLevel())));
    }

    @GetMapping({"/agents", "/api/agents"})
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size,
                                    @RequestParam(required = false) String keyword) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        return ApiResponses.requireService(agentDefinitionPortProvider,
                port -> port.page(tenantId, current, size, keyword));
    }

    @GetMapping({"/agents/{agentId}", "/api/agents/{agentId}"})
    public ApiResponse<Object> findById(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found")));
    }

    @GetMapping({"/agents/{agentId}/versions/{versionId}", "/api/agents/{agentId}/versions/{versionId}"})
    public ApiResponse<Object> findVersion(@PathVariable String agentId,
                                           @PathVariable String versionId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.findVersion(agentId, versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent version not found")));
    }

    @PutMapping({"/agents/{agentId}/draft", "/api/agents/{agentId}/draft"})
    public ApiResponse<Object> updateDraft(@PathVariable String agentId,
                                           @RequestBody AgentDefinitionUpdateDraftRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        AgentDefinitionUpdateDraftRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.updateDraft(agentId,
                new AgentDefinitionUpdateDraftCommand(
                        safeRequest.name(),
                        safeRequest.description(),
                        safeRequest.ownerTeam(),
                        safeRequest.agentType(),
                        safeRequest.riskLevel())));
    }

    @PostMapping({"/agents/{agentId}/publish", "/api/agents/{agentId}/publish"})
    public ApiResponse<Object> publish(@PathVariable String agentId,
                                       @RequestBody AgentVersionPublishRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        AgentVersionPublishRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.publish(agentId,
                new AgentVersionPublishCommand(
                        safeRequest.instructions(),
                        safeRequest.toolSetJson(),
                        safeRequest.modelConfigJson(),
                        safeRequest.memoryConfigJson(),
                        safeRequest.guardrailConfigJson(),
                        safeRequest.skillSetJson(),
                        safeRequest.changeSummary())));
    }

    @PostMapping({"/agents/{agentId}/disable", "/api/agents/{agentId}/disable"})
    public ApiResponse<Object> disable(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.disable(agentId));
    }

    @PostMapping({"/agents/{agentId}/enable", "/api/agents/{agentId}/enable"})
    public ApiResponse<Object> enable(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.enable(agentId));
    }

    @DeleteMapping({"/agents/{agentId}", "/api/agents/{agentId}"})
    public ApiResponse<Object> delete(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> {
            port.delete(agentId);
            return null;
        });
    }
}
