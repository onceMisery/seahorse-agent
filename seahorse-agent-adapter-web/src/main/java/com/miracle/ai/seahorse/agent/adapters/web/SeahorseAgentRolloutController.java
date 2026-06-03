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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutActionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutRollbackCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseAgentRolloutController {

    private final ObjectProvider<AgentRolloutInboundPort> rolloutPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseAgentRolloutController(ObjectProvider<AgentRolloutInboundPort> rolloutPortProvider) {
        this(rolloutPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseAgentRolloutController(ObjectProvider<AgentRolloutInboundPort> rolloutPortProvider,
                                          ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(rolloutPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseAgentRolloutController(ObjectProvider<AgentRolloutInboundPort> rolloutPortProvider,
                                          AdvancedFeatureGate advancedFeatureGate) {
        this.rolloutPortProvider = rolloutPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/agents/{agentId}/versions/{versionId}/rollouts/canary")
    public ApiResponse<Object> createCanary(@PathVariable String agentId,
                                            @PathVariable String versionId,
                                            @RequestBody AgentRolloutCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_ROLLOUT_MANAGEMENT);
        AgentRolloutCreateRequest safeRequest =
                request == null ? new AgentRolloutCreateRequest(null, null, null) : request;
        return ApiResponses.requireService(rolloutPortProvider,
                port -> port.createCanary(new AgentRolloutCreateCommand(
                        safeRequest.tenantId(),
                        agentId,
                        versionId,
                        safeRequest.canaryPercent(),
                        safeRequest.operator())));
    }

    @GetMapping("/api/agents/{agentId}/versions/{versionId}/rollouts/latest")
    public ApiResponse<Object> latest(@PathVariable String agentId,
                                      @PathVariable String versionId,
                                      @RequestParam String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_ROLLOUT_MANAGEMENT);
        return ApiResponses.requireService(rolloutPortProvider,
                port -> port.latest(tenantId, agentId, versionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Agent rollout not found")));
    }

    @PostMapping("/api/agents/{agentId}/rollouts/{rolloutId}/pause")
    public ApiResponse<Object> pause(@PathVariable String agentId,
                                     @PathVariable String rolloutId,
                                     @RequestBody AgentRolloutActionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_ROLLOUT_MANAGEMENT);
        AgentRolloutActionRequest safeRequest =
                request == null ? new AgentRolloutActionRequest(null, null, null) : request;
        return ApiResponses.requireService(rolloutPortProvider,
                port -> port.pause(new AgentRolloutActionCommand(
                        safeRequest.tenantId(),
                        agentId,
                        rolloutId,
                        safeRequest.operator(),
                        safeRequest.comment())));
    }

    @PostMapping("/api/agents/{agentId}/rollouts/{rolloutId}/promote")
    public ApiResponse<Object> promote(@PathVariable String agentId,
                                       @PathVariable String rolloutId,
                                       @RequestBody AgentRolloutActionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_ROLLOUT_MANAGEMENT);
        AgentRolloutActionRequest safeRequest =
                request == null ? new AgentRolloutActionRequest(null, null, null) : request;
        return ApiResponses.requireService(rolloutPortProvider,
                port -> port.promote(new AgentRolloutActionCommand(
                        safeRequest.tenantId(),
                        agentId,
                        rolloutId,
                        safeRequest.operator(),
                        safeRequest.comment())));
    }

    @PostMapping("/api/agents/{agentId}/rollouts/{rolloutId}/rollback")
    public ApiResponse<Object> rollback(@PathVariable String agentId,
                                        @PathVariable String rolloutId,
                                        @RequestBody AgentRolloutRollbackRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_ROLLOUT_MANAGEMENT);
        AgentRolloutRollbackRequest safeRequest =
                request == null ? new AgentRolloutRollbackRequest(null, null, null, null) : request;
        return ApiResponses.requireService(rolloutPortProvider,
                port -> port.rollback(new AgentRolloutRollbackCommand(
                        safeRequest.tenantId(),
                        agentId,
                        rolloutId,
                        safeRequest.targetVersionId(),
                        safeRequest.operator(),
                        safeRequest.comment())));
    }

    public record AgentRolloutCreateRequest(String tenantId,
                                            Integer canaryPercent,
                                            String operator) {
    }

    public record AgentRolloutActionRequest(String tenantId,
                                            String operator,
                                            String comment) {
    }

    public record AgentRolloutRollbackRequest(String tenantId,
                                              String targetVersionId,
                                              String operator,
                                              String comment) {
    }
}
