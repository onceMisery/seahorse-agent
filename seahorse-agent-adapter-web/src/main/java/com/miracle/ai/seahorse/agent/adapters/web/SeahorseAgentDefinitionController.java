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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import org.springframework.beans.factory.ObjectProvider;
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

    public SeahorseAgentDefinitionController(ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider) {
        this.agentDefinitionPortProvider = agentDefinitionPortProvider;
    }

    @PostMapping("/agents")
    public ApiResponse<Object> create(@RequestBody AgentDefinitionCreateRequest request) {
        AgentDefinitionCreateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.createDraft(new AgentDefinitionCreateCommand(
                safeRequest.agentId(),
                safeRequest.tenantId(),
                safeRequest.name(),
                safeRequest.description(),
                safeRequest.ownerUserId(),
                safeRequest.ownerTeam(),
                safeRequest.agentType(),
                safeRequest.baseAgentId(),
                safeRequest.riskLevel())));
    }

    @GetMapping("/agents")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size,
                                    @RequestParam(required = false) String keyword) {
        return ApiResponses.requireService(agentDefinitionPortProvider,
                port -> port.page(tenantId, current, size, keyword));
    }

    @GetMapping("/agents/{agentId}")
    public ApiResponse<Object> findById(@PathVariable String agentId) {
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found")));
    }

    @PutMapping("/agents/{agentId}/draft")
    public ApiResponse<Object> updateDraft(@PathVariable String agentId,
                                           @RequestBody AgentDefinitionUpdateDraftRequest request) {
        AgentDefinitionUpdateDraftRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.updateDraft(agentId,
                new AgentDefinitionUpdateDraftCommand(
                        safeRequest.name(),
                        safeRequest.description(),
                        safeRequest.ownerTeam(),
                        safeRequest.agentType(),
                        safeRequest.riskLevel())));
    }

    @PostMapping("/agents/{agentId}/publish")
    public ApiResponse<Object> publish(@PathVariable String agentId,
                                       @RequestBody AgentVersionPublishRequest request) {
        AgentVersionPublishRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.publish(agentId,
                new AgentVersionPublishCommand(
                        safeRequest.instructions(),
                        safeRequest.toolSetJson(),
                        safeRequest.modelConfigJson(),
                        safeRequest.memoryConfigJson(),
                        safeRequest.guardrailConfigJson(),
                        safeRequest.changeSummary())));
    }

    @PostMapping("/agents/{agentId}/disable")
    public ApiResponse<Object> disable(@PathVariable String agentId) {
        return ApiResponses.requireService(agentDefinitionPortProvider, port -> port.disable(agentId));
    }
}
