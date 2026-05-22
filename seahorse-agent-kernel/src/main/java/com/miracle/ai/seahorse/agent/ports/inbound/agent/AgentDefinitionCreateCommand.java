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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;

/**
 * 创建 Agent 草稿的入站命令。
 *
 * @param agentId     Agent 稳定 ID
 * @param tenantId    租户 ID；为空时使用默认租户
 * @param name        Agent 名称
 * @param description Agent 描述
 * @param ownerUserId 负责人用户 ID
 * @param ownerTeam   负责人团队
 * @param agentType   Agent 类型
 * @param baseAgentId 派生来源 Agent ID
 * @param riskLevel   默认风险等级
 */
public record AgentDefinitionCreateCommand(String agentId,
                                           String tenantId,
                                           String name,
                                           String description,
                                           String ownerUserId,
                                           String ownerTeam,
                                           AgentType agentType,
                                           String baseAgentId,
                                           AgentRiskLevel riskLevel) {
}
