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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;

import java.util.Optional;

public interface AgentDefinitionRepositoryPort {

    /**
     * 持久化新的 Agent 定义。
     */
    void create(AgentDefinition definition);

    /**
     * 更新 Agent 定义元数据。
     */
    void update(AgentDefinition definition);

    /**
     * 按 Agent ID 查询定义。
     */
    Optional<AgentDefinition> findById(String agentId);

    /**
     * 分页查询 Agent 定义。
     */
    AgentDefinitionPage page(String tenantId, long current, long size, String keyword);

    /**
     * 获取下一个版本号，仓储实现需要保证同一 Agent 下递增。
     */
    long nextVersionNo(String agentId);

    /**
     * 保存不可变的 Agent 版本快照。
     */
    void saveVersion(AgentVersion version);

    /**
     * 查询 Agent 当前最新发布版本。
     */
    Optional<AgentVersion> latestVersion(String agentId);

    /**
     * 按不可变版本 ID 查询已发布版本快照。
     */
    Optional<AgentVersion> findVersion(String agentId, String versionId);
}
