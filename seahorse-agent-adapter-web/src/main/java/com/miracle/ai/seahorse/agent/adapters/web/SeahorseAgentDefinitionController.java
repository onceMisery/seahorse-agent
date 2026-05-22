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

import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseAgentDefinitionController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String KEY_MESSAGE = "message";
    private static final String SUCCESS_CODE = "0";
    private static final String ERROR_CODE = "1";
    private static final String SERVICE_NOT_AVAILABLE = "Service not available";

    private final ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider;

    public SeahorseAgentDefinitionController(ObjectProvider<AgentDefinitionInboundPort> agentDefinitionPortProvider) {
        this.agentDefinitionPortProvider = agentDefinitionPortProvider;
    }

    @PostMapping("/agents")
    public Map<String, Object> create(@RequestBody AgentDefinitionCreateRequest request) {
        AgentDefinitionCreateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        AgentDefinitionInboundPort port = requirePort();
        return ok(port.createDraft(new AgentDefinitionCreateCommand(
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
    public Map<String, Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false, defaultValue = "1") long current,
                                    @RequestParam(required = false, defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword) {
        AgentDefinitionInboundPort port = requirePort();
        return ok(port.page(tenantId, current, size, keyword));
    }

    @GetMapping("/agents/{agentId}")
    public Map<String, Object> findById(@PathVariable String agentId) {
        AgentDefinitionInboundPort port = requirePort();
        return ok(port.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found")));
    }

    @PutMapping("/agents/{agentId}/draft")
    public Map<String, Object> updateDraft(@PathVariable String agentId,
                                           @RequestBody AgentDefinitionUpdateDraftRequest request) {
        AgentDefinitionUpdateDraftRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        AgentDefinitionInboundPort port = requirePort();
        return ok(port.updateDraft(agentId, new AgentDefinitionUpdateDraftCommand(
                safeRequest.name(),
                safeRequest.description(),
                safeRequest.ownerTeam(),
                safeRequest.agentType(),
                safeRequest.riskLevel())));
    }

    @PostMapping("/agents/{agentId}/publish")
    public Map<String, Object> publish(@PathVariable String agentId,
                                       @RequestBody AgentVersionPublishRequest request) {
        AgentVersionPublishRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        AgentDefinitionInboundPort port = requirePort();
        return ok(port.publish(agentId, new AgentVersionPublishCommand(
                safeRequest.instructions(),
                safeRequest.toolSetJson(),
                safeRequest.modelConfigJson(),
                safeRequest.memoryConfigJson(),
                safeRequest.guardrailConfigJson(),
                safeRequest.changeSummary())));
    }

    @PostMapping("/agents/{agentId}/disable")
    public Map<String, Object> disable(@PathVariable String agentId) {
        AgentDefinitionInboundPort port = requirePort();
        return ok(port.disable(agentId));
    }

    private AgentDefinitionInboundPort requirePort() {
        AgentDefinitionInboundPort port = agentDefinitionPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE);
        }
        return port;
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> serviceUnavailable() {
        return Map.of(KEY_CODE, ERROR_CODE, KEY_MESSAGE, SERVICE_NOT_AVAILABLE);
    }
}
