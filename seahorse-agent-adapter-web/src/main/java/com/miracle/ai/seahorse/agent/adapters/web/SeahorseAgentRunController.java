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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseAgentRunController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String SERVICE_NOT_AVAILABLE = "Service not available";

    private final ObjectProvider<AgentRunInboundPort> agentRunPortProvider;

    public SeahorseAgentRunController(ObjectProvider<AgentRunInboundPort> agentRunPortProvider) {
        this.agentRunPortProvider = agentRunPortProvider;
    }

    @PostMapping("/agents/{agentId}/runs")
    public Map<String, Object> startRun(@PathVariable String agentId,
                                        @RequestBody AgentRunStartRequest request) {
        AgentRunStartRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        AgentRunInboundPort port = requirePort();
        return ok(port.startRun(new AgentRunStartCommand(
                agentId,
                safeRequest.versionId(),
                safeRequest.tenantId(),
                safeRequest.conversationId(),
                safeRequest.triggerType(),
                safeRequest.inputSummary(),
                safeRequest.traceId())));
    }

    @GetMapping("/agent-runs/{runId}")
    public Map<String, Object> findRunById(@PathVariable String runId) {
        AgentRunInboundPort port = requirePort();
        return ok(port.findRunById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found")));
    }

    @GetMapping("/agent-runs/{runId}/steps")
    public Map<String, Object> listSteps(@PathVariable String runId) {
        AgentRunInboundPort port = requirePort();
        return ok(port.listSteps(runId));
    }

    @PostMapping("/agent-runs/{runId}/cancel")
    public Map<String, Object> cancel(@PathVariable String runId) {
        AgentRunInboundPort port = requirePort();
        return ok(port.cancel(runId));
    }

    private AgentRunInboundPort requirePort() {
        AgentRunInboundPort port = agentRunPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE);
        }
        return port;
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }
}
