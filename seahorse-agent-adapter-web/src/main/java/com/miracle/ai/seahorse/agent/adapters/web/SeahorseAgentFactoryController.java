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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateId;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentPublishValidationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionRollbackCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeahorseAgentFactoryController {

    private final ObjectProvider<AgentFactoryInboundPort> agentFactoryPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseAgentFactoryController(ObjectProvider<AgentFactoryInboundPort> agentFactoryPortProvider) {
        this(agentFactoryPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseAgentFactoryController(ObjectProvider<AgentFactoryInboundPort> agentFactoryPortProvider,
                                          ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(agentFactoryPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseAgentFactoryController(ObjectProvider<AgentFactoryInboundPort> agentFactoryPortProvider,
                                          AdvancedFeatureGate advancedFeatureGate) {
        this.agentFactoryPortProvider = agentFactoryPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @GetMapping("/api/agent-templates")
    public ApiResponse<Object> listTemplates(
            @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_FACTORY_MANAGEMENT);
        return ApiResponses.requireService(agentFactoryPortProvider, port -> port.listTemplates(includeDisabled));
    }

    @PostMapping("/api/agents/from-template")
    public ApiResponse<Object> createFromTemplate(@RequestBody AgentFactoryCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_FACTORY_MANAGEMENT);
        AgentFactoryCreateRequest safeRequest = request == null
                ? new AgentFactoryCreateRequest(null, null, null, null, null, null, null, List.of(), null, null)
                : request;
        return ApiResponses.requireService(agentFactoryPortProvider,
                port -> port.createFromTemplate(new AgentFactoryCreateCommand(
                        safeRequest.templateId(),
                        safeRequest.tenantId(),
                        safeRequest.agentId(),
                        safeRequest.name(),
                        safeRequest.description(),
                        safeRequest.ownerUserId(),
                        safeRequest.ownerTeam(),
                        safeRequest.requestedToolIds(),
                        safeRequest.riskLevel(),
                        safeRequest.instructionsOverlay())));
    }

    @PostMapping("/api/agents/{agentId}/validate")
    public ApiResponse<Object> validatePublish(@PathVariable String agentId,
                                               @RequestBody AgentPublishValidationRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_FACTORY_MANAGEMENT);
        AgentPublishValidationRequest safeRequest = request == null
                ? new AgentPublishValidationRequest(null, null, List.of(), null, null, null)
                : request;
        return ApiResponses.requireService(agentFactoryPortProvider,
                port -> port.validatePublish(new AgentPublishValidationCommand(
                        agentId,
                        safeRequest.versionId(),
                        safeRequest.instructions(),
                        safeRequest.toolIds(),
                        safeRequest.ownerUserId(),
                        safeRequest.ownerTeam(),
                        safeRequest.changeSummary())));
    }

    @GetMapping("/api/agents/{agentId}/publish-checks/latest")
    public ApiResponse<Object> latestPublishCheck(@PathVariable String agentId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_FACTORY_MANAGEMENT);
        return ApiResponses.requireService(agentFactoryPortProvider,
                port -> port.latestPublishCheck(agentId).orElse(null));
    }

    @PostMapping("/api/agents/{agentId}/versions/{versionId}/rollback")
    public ApiResponse<Object> rollback(@PathVariable String agentId,
                                        @PathVariable String versionId,
                                        @RequestBody AgentVersionRollbackRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_FACTORY_MANAGEMENT);
        AgentVersionRollbackRequest safeRequest = request == null
                ? new AgentVersionRollbackRequest(null, null, null, null)
                : request;
        return ApiResponses.requireService(agentFactoryPortProvider,
                port -> port.rollback(new AgentVersionRollbackCommand(
                        safeRequest.tenantId(),
                        agentId,
                        versionId,
                        safeRequest.operator(),
                        safeRequest.reasonCode(),
                        safeRequest.comment())));
    }

    @GetMapping("/api/agent-catalog")
    public ApiResponse<Object> catalog(@RequestParam(required = false) String tenantId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false, defaultValue = "1") long current,
                                       @RequestParam(required = false, defaultValue = "20") long size) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_FACTORY_MANAGEMENT);
        return ApiResponses.requireService(agentFactoryPortProvider,
                port -> port.catalog(new AgentCatalogQuery(tenantId, keyword, current, size)));
    }

    public record AgentFactoryCreateRequest(AgentTemplateId templateId,
                                            String tenantId,
                                            String agentId,
                                            String name,
                                            String description,
                                            String ownerUserId,
                                            String ownerTeam,
                                            List<String> requestedToolIds,
                                            AgentRiskLevel riskLevel,
                                            String instructionsOverlay) {
    }

    public record AgentPublishValidationRequest(String versionId,
                                                String instructions,
                                                List<String> toolIds,
                                                String ownerUserId,
                                                String ownerTeam,
                                                String changeSummary) {
    }

    public record AgentVersionRollbackRequest(String tenantId,
                                              String operator,
                                              AgentRollbackReasonCode reasonCode,
                                              String comment) {
    }
}
