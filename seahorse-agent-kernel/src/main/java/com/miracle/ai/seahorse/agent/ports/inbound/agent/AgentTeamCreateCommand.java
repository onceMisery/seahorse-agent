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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdge;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMember;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMode;

import java.util.List;

/**
 * 创建团队定义命令。
 *
 * @param tenantId            租户（空归一为默认租户）
 * @param name                团队名称
 * @param mode                编排模式
 * @param ownerTeam           所属组织/团队描述
 * @param supervisorMemberId  SUPERVISOR 模式的规划成员（WORKFLOW_DAG 可为空）
 * @param members             成员列表
 * @param edges               WORKFLOW_DAG 模式的执行边（SUPERVISOR 模式为空）
 */
public record AgentTeamCreateCommand(String tenantId,
                                     String name,
                                     AgentTeamMode mode,
                                     String ownerTeam,
                                     String supervisorMemberId,
                                     List<AgentTeamMember> members,
                                     List<AgentTeamEdge> edges) {
}
