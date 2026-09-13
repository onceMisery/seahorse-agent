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
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.AgentHandoffCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
public class SeahorseAgentHandoffController {

    private static final CurrentUserPort DENY_ALL_CURRENT_USER = () -> Optional.empty();

    private final ObjectProvider<AgentHandoffInboundPort> handoffPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;
    private final CurrentUserPort currentUserPort;

    @Autowired
    public SeahorseAgentHandoffController(ObjectProvider<AgentHandoffInboundPort> handoffPortProvider,
                                          ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider,
                                          ObjectProvider<CurrentUserPort> currentUserPortProvider) {
        this(handoffPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults),
                currentUserPortProvider);
    }

    public SeahorseAgentHandoffController(ObjectProvider<AgentHandoffInboundPort> handoffPortProvider,
                                          AdvancedFeatureGate advancedFeatureGate,
                                          ObjectProvider<CurrentUserPort> currentUserPortProvider) {
        this.handoffPortProvider = handoffPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
        // 受控创建要求 ADMIN；缺失认证上下文时一律拒绝（设计 §5.3：默认仅 admin/workflow engine 可用）
        this.currentUserPort = currentUserPortProvider == null
                ? DENY_ALL_CURRENT_USER
                : currentUserPortProvider.getIfAvailable(() -> DENY_ALL_CURRENT_USER);
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

    @PostMapping("/api/agent-handoffs")
    public ApiResponse<Object> create(@RequestBody AgentHandoffCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        currentUserPort.requireRole("ADMIN");
        return ApiResponses.requireService(handoffPortProvider,
                port -> AgentHandoffResponse.from(port.createLocalHandoff(request.toCommand())));
    }

    @PostMapping("/api/agent-handoffs/{handoffId}/cancel")
    public ApiResponse<Object> cancel(@PathVariable String handoffId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(handoffPortProvider, port -> AgentHandoffResponse.from(port.cancel(handoffId)));
    }

    /**
     * 受控创建请求（设计 §5.3：默认仅 admin/workflow engine 可用）。
     */
    public record AgentHandoffCreateRequest(String tenantId,
                                            String parentRunId,
                                            String sourceAgentId,
                                            String targetAgentId,
                                            String targetVersionId,
                                            String handoffReason,
                                            String contextPackId,
                                            String inputSummary,
                                            String contextSummaryJson,
                                            Integer depth,
                                            List<String> ancestorAgentIds,
                                            String traceId) {

        private AgentHandoffCreateCommand toCommand() {
            return new AgentHandoffCreateCommand(tenantId, parentRunId, sourceAgentId, targetAgentId,
                    targetVersionId, handoffReason, contextPackId, inputSummary, contextSummaryJson,
                    depth == null ? 1 : depth, ancestorAgentIds == null ? List.of() : ancestorAgentIds,
                    traceId);
        }
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
                                       String contextPackId,
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
                    handoff.contextPackId(),
                    handoff.createdAt(),
                    handoff.updatedAt(),
                    handoff.finishedAt());
        }
    }
}
