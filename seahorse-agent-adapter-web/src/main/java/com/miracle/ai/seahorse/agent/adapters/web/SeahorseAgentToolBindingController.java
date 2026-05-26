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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingItemCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingReplaceCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Agent 发布版本工具绑定管理 API。
 */
@RestController
public class SeahorseAgentToolBindingController {

    private final ObjectProvider<AgentToolBindingManagementInboundPort> bindingPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseAgentToolBindingController(
            ObjectProvider<AgentToolBindingManagementInboundPort> bindingPortProvider) {
        this(bindingPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseAgentToolBindingController(
            ObjectProvider<AgentToolBindingManagementInboundPort> bindingPortProvider,
            ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(bindingPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseAgentToolBindingController(
            ObjectProvider<AgentToolBindingManagementInboundPort> bindingPortProvider,
            AdvancedFeatureGate advancedFeatureGate) {
        this.bindingPortProvider = bindingPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PutMapping("/api/agents/{agentId}/versions/{versionId}/tools")
    public ApiResponse<Object> replaceBindings(@PathVariable String agentId,
                                               @PathVariable String versionId,
                                               @RequestBody AgentToolBindingReplaceRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_TOOL_BINDING_MANAGEMENT);
        AgentToolBindingReplaceRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(bindingPortProvider,
                port -> port.replaceBindings(agentId, versionId, toCommand(safeRequest)));
    }

    private AgentToolBindingReplaceCommand toCommand(AgentToolBindingReplaceRequest request) {
        List<AgentToolBindingItemCommand> tools = request.tools().stream()
                .map(this::toCommand)
                .toList();
        return new AgentToolBindingReplaceCommand(tools);
    }

    private AgentToolBindingItemCommand toCommand(AgentToolBindingItemRequest request) {
        AgentToolBindingItemRequest safeRequest = Objects.requireNonNull(request, "tool item must not be null");
        return new AgentToolBindingItemCommand(
                safeRequest.toolId(),
                safeRequest.maxCallsPerRun(),
                safeRequest.argumentPolicyJson());
    }
}
