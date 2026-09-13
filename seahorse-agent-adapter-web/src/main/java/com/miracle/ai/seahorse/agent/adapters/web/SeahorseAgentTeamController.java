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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdgeCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRunStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentTeamRunStartCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Agent 团队编排 API（Multi-Agent A2A 设计 §10 P1）。
 * 团队运行与 handoff 同属治理面，沿用 AGENT_HANDOFF feature gate。
 */
@RestController
public class SeahorseAgentTeamController {

    private final ObjectProvider<AgentTeamInboundPort> teamPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    @Autowired
    public SeahorseAgentTeamController(ObjectProvider<AgentTeamInboundPort> teamPortProvider,
                                       ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(teamPortProvider, advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults));
    }

    public SeahorseAgentTeamController(ObjectProvider<AgentTeamInboundPort> teamPortProvider,
                                       AdvancedFeatureGate advancedFeatureGate) {
        this.teamPortProvider = teamPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.demoDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/agent-teams")
    public ApiResponse<Object> createTeam(@RequestBody AgentTeamCreateCommand command) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(teamPortProvider,
                port -> AgentTeamDefinitionResponse.from(port.createTeam(command)));
    }

    @GetMapping("/api/agent-teams")
    public ApiResponse<Object> listTeams(@RequestParam(name = "tenantId", required = false) String tenantId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(teamPortProvider,
                port -> port.listTeams(tenantId).stream().map(AgentTeamDefinitionResponse::from).toList());
    }

    @GetMapping("/api/agent-teams/{teamId}")
    public ApiResponse<Object> getTeam(@PathVariable("teamId") String teamId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(teamPortProvider,
                port -> AgentTeamDefinitionResponse.from(port.getTeam(teamId)));
    }

    @PostMapping("/api/agent-teams/{teamId}/runs")
    public ApiResponse<Object> startTeamRun(@PathVariable("teamId") String teamId,
                                            @RequestBody AgentTeamRunStartCommand command) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(teamPortProvider,
                port -> AgentTeamRunResponse.from(port.startTeamRun(teamId, command)));
    }

    @GetMapping("/api/agent-team-runs/{teamRunId}")
    public ApiResponse<Object> getTeamRun(@PathVariable("teamRunId") String teamRunId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_HANDOFF);
        return ApiResponses.requireService(teamPortProvider,
                port -> AgentTeamRunResponse.from(port.getTeamRun(teamRunId)));
    }

    public record AgentTeamDefinitionResponse(String teamId,
                                              String tenantId,
                                              String name,
                                              AgentTeamMode mode,
                                              String ownerTeam,
                                              String supervisorMemberId,
                                              List<AgentTeamMemberView> members,
                                              List<AgentTeamEdgeView> edges,
                                              boolean active,
                                              Instant createdAt,
                                              Instant updatedAt) {

        private static AgentTeamDefinitionResponse from(AgentTeamDefinition definition) {
            return new AgentTeamDefinitionResponse(
                    definition.teamId(),
                    definition.tenantId(),
                    definition.name(),
                    definition.mode(),
                    definition.ownerTeam(),
                    definition.supervisorMemberId(),
                    definition.members().stream()
                            .map(member -> new AgentTeamMemberView(member.memberId(), member.agentId(),
                                    member.role(), member.instruction()))
                            .toList(),
                    definition.edges().stream()
                            .map(edge -> new AgentTeamEdgeView(edge.sourceMemberId(), edge.targetMemberId(),
                                    edge.condition()))
                            .toList(),
                    definition.isActive(),
                    definition.createdAt(),
                    definition.updatedAt());
        }
    }

    public record AgentTeamMemberView(String memberId,
                                      String agentId,
                                      String role,
                                      String instruction) {
    }

    public record AgentTeamEdgeView(String sourceMemberId,
                                    String targetMemberId,
                                    AgentTeamEdgeCondition condition) {
    }

    public record AgentTeamRunResponse(String teamRunId,
                                       String teamId,
                                       String tenantId,
                                       String userId,
                                       AgentTeamMode mode,
                                       String objective,
                                       AgentTeamRunStatus status,
                                       String parentRunId,
                                       String summary,
                                       String errorCode,
                                       String errorMessage,
                                       List<AgentTeamNodeRunView> nodeRuns,
                                       Instant startedAt,
                                       Instant finishedAt) {

        private static AgentTeamRunResponse from(AgentTeamRun teamRun) {
            return new AgentTeamRunResponse(
                    teamRun.teamRunId(),
                    teamRun.teamId(),
                    teamRun.tenantId(),
                    teamRun.userId(),
                    teamRun.mode(),
                    teamRun.objective(),
                    teamRun.status(),
                    teamRun.parentRunId(),
                    teamRun.summary(),
                    teamRun.errorCode(),
                    teamRun.errorMessage(),
                    teamRun.nodeRuns().stream()
                            .map(node -> new AgentTeamNodeRunView(node.nodeRunId(), node.memberId(),
                                    node.agentId(), node.instruction(), node.status(), node.handoffId(),
                                    node.childRunId(), node.outputSummary(), node.errorCode(),
                                    node.errorMessage(), node.startedAt(), node.finishedAt()))
                            .toList(),
                    teamRun.startedAt(),
                    teamRun.finishedAt());
        }
    }

    public record AgentTeamNodeRunView(String nodeRunId,
                                       String memberId,
                                       String agentId,
                                       String instruction,
                                       AgentTeamNodeStatus status,
                                       String handoffId,
                                       String childRunId,
                                       String outputSummary,
                                       String errorCode,
                                       String errorMessage,
                                       Instant startedAt,
                                       Instant finishedAt) {
    }
}
