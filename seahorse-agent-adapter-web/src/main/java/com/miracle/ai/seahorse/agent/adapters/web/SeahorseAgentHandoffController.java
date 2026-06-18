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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentHandoffInboundPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class SeahorseAgentHandoffController {

    private final ObjectProvider<AgentHandoffInboundPort> handoffPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseAgentHandoffController(ObjectProvider<AgentHandoffInboundPort> handoffPortProvider,
                                          ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(handoffPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseAgentHandoffController(ObjectProvider<AgentHandoffInboundPort> handoffPortProvider,
                                          AdvancedFeatureGate advancedFeatureGate) {
        this.handoffPortProvider = handoffPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @GetMapping("/api/agent-runs/{runId}/handoffs")
    public ApiResponse<Object> listByParentRunId(@PathVariable String runId,
                                                 @RequestParam String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(handoffPortProvider, port -> port.listByParentRunId(tenantId, runId).stream()
                .map(AgentHandoffResponse::from)
                .toList());
    }

    @GetMapping("/api/agent-handoffs/{handoffId}")
    public ApiResponse<Object> findById(@PathVariable String handoffId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(handoffPortProvider, port -> AgentHandoffResponse.from(port.findById(handoffId)));
    }

    @PostMapping("/api/agent-handoffs/{handoffId}/cancel")
    public ApiResponse<Object> cancel(@PathVariable String handoffId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(handoffPortProvider, port -> AgentHandoffResponse.from(port.cancel(handoffId)));
    }

    public record AgentHandoffResponse(String handoffId,
                                       String tenantId,
                                       String parentRunId,
                                       String childRunId,
                                       String sourceAgentId,
                                       String targetAgentId,
                                       AgentHandoffStatus status,
                                       AgentHandoffFailureCode failureCode,
                                       String handoffReason,
                                       Instant createdAt,
                                       Instant updatedAt,
                                       Instant finishedAt) {

        private static AgentHandoffResponse from(AgentHandoff handoff) {
            return new AgentHandoffResponse(
                    handoff.handoffId(),
                    handoff.tenantId(),
                    handoff.parentRunId(),
                    handoff.childRunId(),
                    handoff.sourceAgentId(),
                    handoff.targetAgentId(),
                    handoff.status(),
                    handoff.failureCode(),
                    handoff.handoffReason(),
                    handoff.createdAt(),
                    handoff.updatedAt(),
                    handoff.finishedAt());
        }
    }
}
