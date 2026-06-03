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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillBindingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillManagementInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@RestController
public class SeahorseSkillController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<AgentSkillManagementInboundPort> managementPortProvider;
    private final ObjectProvider<AgentSkillBindingInboundPort> bindingPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseSkillController(ObjectProvider<AgentSkillManagementInboundPort> managementPortProvider,
                                   ObjectProvider<AgentSkillBindingInboundPort> bindingPortProvider,
                                   ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this.managementPortProvider = managementPortProvider;
        this.bindingPortProvider = bindingPortProvider;
        this.advancedFeatureGate = advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults);
    }

    public SeahorseSkillController(ObjectProvider<AgentSkillManagementInboundPort> managementPortProvider,
                                   ObjectProvider<AgentSkillBindingInboundPort> bindingPortProvider,
                                   AdvancedFeatureGate advancedFeatureGate) {
        this.managementPortProvider = managementPortProvider;
        this.bindingPortProvider = bindingPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @GetMapping("/api/skills")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size,
                                    @RequestParam(required = false) String keyword) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        return ApiResponses.requireService(managementPortProvider, port -> port.page(tenantId, current, size, keyword));
    }

    @GetMapping("/api/skills/{name}")
    public ApiResponse<Object> detail(@PathVariable String name,
                                      @RequestParam(required = false) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        return ApiResponses.requireService(managementPortProvider, port -> port.find(tenantId, name)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found")));
    }

    @PostMapping("/api/skills/custom")
    public ApiResponse<Object> createCustom(@RequestBody SkillCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        SkillCreateRequest safe = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(managementPortProvider, port -> port.createCustom(safe.tenantId(), safe.content()));
    }

    @PutMapping("/api/skills/custom/{name}")
    public ApiResponse<Object> updateCustom(@PathVariable String name,
                                            @RequestBody SkillUpdateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        SkillUpdateRequest safe = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(managementPortProvider, port -> port.updateCustom(safe.tenantId(), name, safe.content()));
    }

    @PostMapping("/api/skills/{name}/enable")
    public ApiResponse<Object> enable(@PathVariable String name,
                                      @RequestBody(required = false) SkillEnableRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        String tenantId = request == null ? null : request.tenantId();
        return ApiResponses.requireService(managementPortProvider, port -> port.enable(tenantId, name));
    }

    @PostMapping("/api/skills/{name}/disable")
    public ApiResponse<Object> disable(@PathVariable String name,
                                       @RequestBody(required = false) SkillEnableRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        String tenantId = request == null ? null : request.tenantId();
        return ApiResponses.requireService(managementPortProvider, port -> port.disable(tenantId, name));
    }

    @DeleteMapping("/api/skills/custom/{name}")
    public ApiResponse<Object> deleteCustom(@PathVariable String name,
                                            @RequestParam(required = false) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        return ApiResponses.requireService(managementPortProvider, port -> port.deleteCustom(tenantId, name));
    }

    @GetMapping("/api/skills/custom/{name}/history")
    public ApiResponse<Object> history(@PathVariable String name,
                                       @RequestParam(required = false) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        return ApiResponses.requireService(managementPortProvider, port -> port.history(tenantId, name));
    }

    @PostMapping("/api/skills/custom/{name}/rollback")
    public ApiResponse<Object> rollback(@PathVariable String name,
                                        @RequestBody SkillRollbackRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        SkillRollbackRequest safe = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(managementPortProvider,
                port -> port.rollbackCustom(safe.tenantId(), name, safe.revisionId()));
    }

    @PostMapping("/api/skills/install")
    public ApiResponse<Object> install(@RequestBody SkillInstallRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        SkillInstallRequest safe = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(managementPortProvider, port -> port.install(safe.tenantId(), safe.content()));
    }

    @GetMapping("/api/agents/{agentId}/skills")
    public ApiResponse<Object> listBindings(@PathVariable String agentId,
                                            @RequestParam(required = false) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        return ApiResponses.requireService(bindingPortProvider, port -> port.listBindings(tenantId, agentId));
    }

    @PutMapping("/api/agents/{agentId}/skills")
    public ApiResponse<Object> replaceBindings(@PathVariable String agentId,
                                               @RequestBody AgentSkillBindingReplaceRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        AgentSkillBindingReplaceRequest safe = Objects.requireNonNull(request, "request must not be null");
        String tenantId = safe.tenantId() == null || safe.tenantId().isBlank()
                ? AgentDefinition.DEFAULT_TENANT_ID
                : safe.tenantId().trim();
        List<AgentSkillBinding> bindings = safe.bindings() == null ? List.of() : safe.bindings().stream()
                .map(binding -> new AgentSkillBinding(agentId, tenantId, binding.skillName(), binding.revisionId(),
                        injectMode(binding.injectMode()), null, Instant.EPOCH))
                .toList();
        return ApiResponses.requireService(bindingPortProvider, port -> port.replaceBindings(tenantId, agentId, bindings));
    }

    @GetMapping("/api/agents/{agentId}/skills/snapshot")
    public ApiResponse<Object> snapshot(@PathVariable String agentId,
                                        @RequestParam(required = false) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_DEFINITION_MANAGEMENT);
        advancedFeatureGate.requireEnabled(AdvancedFeature.SKILL_MANAGEMENT);
        return ApiResponses.requireService(bindingPortProvider, port -> port.snapshotJson(tenantId, agentId));
    }

    private SkillInjectMode injectMode(String value) {
        if (value == null || value.isBlank()) {
            return SkillInjectMode.METADATA_AND_BODY;
        }
        return SkillInjectMode.valueOf(value.trim());
    }
}
