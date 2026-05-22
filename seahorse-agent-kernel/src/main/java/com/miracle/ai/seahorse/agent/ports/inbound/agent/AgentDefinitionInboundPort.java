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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;

import java.util.Optional;

public interface AgentDefinitionInboundPort {

    /**
     * 创建一个可编辑的 Agent 草稿。
     *
     * @param command 创建命令
     * @return 新 Agent ID
     */
    String createDraft(AgentDefinitionCreateCommand command);

    /**
     * 更新 Agent 草稿；已发布或禁用状态不能通过该方法原地修改。
     */
    AgentDefinition updateDraft(String agentId, AgentDefinitionUpdateDraftCommand command);

    /**
     * 将当前草稿内容固化为不可变版本，并把 Agent 标记为已发布。
     */
    AgentVersion publish(String agentId, AgentVersionPublishCommand command);

    /**
     * 禁用 Agent，后续运行入口必须拒绝启动新的 run。
     */
    AgentDefinition disable(String agentId);

    /**
     * 查询单个 Agent 定义。
     */
    Optional<AgentDefinition> findById(String agentId);

    /**
     * 分页查询 Agent 定义列表。
     */
    AgentDefinitionPage page(String tenantId, long current, long size, String keyword);
}
