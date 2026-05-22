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
 * 更新 Agent 草稿的入站命令；null 字段表示沿用当前值。
 *
 * @param name        新名称
 * @param description 新描述
 * @param ownerTeam   新负责人团队
 * @param agentType   新 Agent 类型
 * @param riskLevel   新风险等级
 */
public record AgentDefinitionUpdateDraftCommand(String name,
                                                String description,
                                                String ownerTeam,
                                                AgentType agentType,
                                                AgentRiskLevel riskLevel) {
}
