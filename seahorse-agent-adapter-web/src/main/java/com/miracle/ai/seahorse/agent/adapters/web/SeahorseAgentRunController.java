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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunCostSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCheckpointQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class SeahorseAgentRunController {

    private final ObjectProvider<AgentRunInboundPort> agentRunPortProvider;
    private final ObjectProvider<AgentRunResumeInboundPort> agentRunResumePortProvider;
    private final ObjectProvider<AgentCheckpointQueryInboundPort> checkpointQueryPortProvider;
    private final ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider;
    private final ObjectProvider<AgentRunCostSummaryInboundPort> costSummaryPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseAgentRunController(ObjectProvider<AgentRunInboundPort> agentRunPortProvider) {
        this(agentRunPortProvider, null, null, null, null, AdvancedFeatureGate.consumerWebDefaults());
    }

    @Autowired
    public SeahorseAgentRunController(ObjectProvider<AgentRunInboundPort> agentRunPortProvider,
                                       ObjectProvider<AgentRunResumeInboundPort> agentRunResumePortProvider,
                                       ObjectProvider<AgentCheckpointQueryInboundPort> checkpointQueryPortProvider,
                                       ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider,
                                       ObjectProvider<AgentRunCostSummaryInboundPort> costSummaryPortProvider,
                                       ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(agentRunPortProvider,
                agentRunResumePortProvider,
                checkpointQueryPortProvider,
                snapshotPortProvider,
                costSummaryPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseAgentRunController(ObjectProvider<AgentRunInboundPort> agentRunPortProvider,
                                      ObjectProvider<AgentRunResumeInboundPort> agentRunResumePortProvider,
                                      ObjectProvider<AgentCheckpointQueryInboundPort> checkpointQueryPortProvider) {
        this(agentRunPortProvider, agentRunResumePortProvider, checkpointQueryPortProvider, null, null,
                AdvancedFeatureGate.consumerWebDefaults());
    }

    public SeahorseAgentRunController(ObjectProvider<AgentRunInboundPort> agentRunPortProvider,
                                      ObjectProvider<AgentRunResumeInboundPort> agentRunResumePortProvider,
                                      ObjectProvider<AgentCheckpointQueryInboundPort> checkpointQueryPortProvider,
                                      ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider,
                                      AdvancedFeatureGate advancedFeatureGate) {
        this(agentRunPortProvider,
                agentRunResumePortProvider,
                checkpointQueryPortProvider,
                snapshotPortProvider,
                null,
                advancedFeatureGate);
    }

    public SeahorseAgentRunController(ObjectProvider<AgentRunInboundPort> agentRunPortProvider,
                                      ObjectProvider<AgentRunResumeInboundPort> agentRunResumePortProvider,
                                      ObjectProvider<AgentCheckpointQueryInboundPort> checkpointQueryPortProvider,
                                      ObjectProvider<AgentRunSnapshotInboundPort> snapshotPortProvider,
                                      ObjectProvider<AgentRunCostSummaryInboundPort> costSummaryPortProvider,
                                      AdvancedFeatureGate advancedFeatureGate) {
        this.agentRunPortProvider = agentRunPortProvider;
        this.agentRunResumePortProvider = agentRunResumePortProvider;
        this.checkpointQueryPortProvider = checkpointQueryPortProvider;
        this.snapshotPortProvider = snapshotPortProvider;
        this.costSummaryPortProvider = costSummaryPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/agents/{agentId}/runs")
    public ApiResponse<Object> startRun(@PathVariable String agentId,
                                        @RequestBody AgentRunStartRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        AgentRunStartRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        requireTriggerAllowed(safeRequest.triggerType());
        return ApiResponses.requireService(agentRunPortProvider, port -> port.startRun(new AgentRunStartCommand(
                agentId,
                safeRequest.versionId(),
                safeRequest.tenantId(),
                safeRequest.conversationId(),
                safeRequest.triggerType(),
                safeRequest.inputSummary(),
                safeRequest.traceId())));
    }

    private void requireTriggerAllowed(AgentRunTriggerType triggerType) {
        if (triggerType == AgentRunTriggerType.A2A) {
            advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        }
    }

    @GetMapping("/agent-runs/{runId}")
    public ApiResponse<Object> findRunById(@PathVariable String runId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(agentRunPortProvider, port -> port.findRunById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found")));
    }

    @GetMapping("/agent-runs/{runId}/steps")
    public ApiResponse<Object> listSteps(@PathVariable String runId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(agentRunPortProvider, port -> port.listSteps(runId));
    }

    @PostMapping("/agent-runs/{runId}/cancel")
    public ApiResponse<Object> cancel(@PathVariable String runId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(agentRunPortProvider, port -> port.cancel(runId));
    }

    @PostMapping({"/agent-runs/{runId}/retry", "/api/agent-runs/{runId}/retry"})
    public ApiResponse<Object> retry(@PathVariable String runId, HttpServletRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(agentRunPortProvider, port -> port.retry(runId));
    }

    @PostMapping({"/agent-runs/{runId}/resume", "/api/agent-runs/{runId}/resume"})
    public ApiResponse<Object> resume(@PathVariable String runId, HttpServletRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(agentRunResumePortProvider, port -> port.resume(runId));
    }

    @GetMapping({"/agent-runs/{runId}/checkpoints", "/api/agent-runs/{runId}/checkpoints"})
    public ApiResponse<Object> listCheckpoints(@PathVariable String runId, HttpServletRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(checkpointQueryPortProvider, port -> port.listByRunId(runId));
    }

    @GetMapping({"/agent-runs/{runId}/snapshot", "/api/agent-runs/{runId}/snapshot"})
    public ApiResponse<Object> snapshot(@PathVariable String runId, HttpServletRequest request) {
        requireApiOrRunManagement(request);
        return ApiResponses.requireService(snapshotPortProvider, port -> port.getSnapshot(runId));
    }

    @GetMapping({"/agent-runs/{runId}/cost-summary", "/api/agent-runs/{runId}/cost-summary"})
    public ApiResponse<Object> costSummary(@PathVariable String runId, HttpServletRequest request) {
        requireApiOrRunManagement(request);
        return ApiResponses.requireService(costSummaryPortProvider, port -> port.getCostSummary(runId));
    }

    private void requireApiOrRunManagement(HttpServletRequest request) {
        String uri = request == null ? null : request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        }
    }
}
