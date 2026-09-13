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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRun;

import java.util.List;

/**
 * Agent 团队编排入站端口：团队定义管理与团队运行（Supervisor / Workflow DAG）。
 */
public interface AgentTeamInboundPort {

    AgentTeamDefinition createTeam(AgentTeamCreateCommand command);

    List<AgentTeamDefinition> listTeams(String tenantId);

    AgentTeamDefinition getTeam(String teamId);

    AgentTeamRun startTeamRun(String teamId, AgentTeamRunStartCommand command);

    AgentTeamRun getTeamRun(String teamRunId);
}
