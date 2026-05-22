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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;

import java.util.List;
import java.util.Optional;

public interface AgentToolBindingRepositoryPort {

    /**
     * 保存某个 Agent 版本的工具绑定快照。
     */
    void saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings);

    /**
     * 查询某个 Agent 版本的全部工具绑定。
     */
    List<AgentToolBinding> listBindings(String agentId, String versionId);

    /**
     * 查询某个 Agent 版本与工具的单条绑定。
     */
    default Optional<AgentToolBinding> findBinding(String agentId, String versionId, String toolId) {
        return listBindings(agentId, versionId).stream()
                .filter(binding -> binding.toolId().equals(toolId))
                .findFirst();
    }

    /**
     * 判断工具是否绑定到当前 Agent 版本。
     */
    default boolean isBound(String agentId, String versionId, String toolId) {
        return findBinding(agentId, versionId, toolId).isPresent();
    }

    /**
     * 空绑定实现，用于没有配置绑定仓储时保持依赖可空安全。
     */
    static AgentToolBindingRepositoryPort empty() {
        return new AgentToolBindingRepositoryPort() {
            @Override
            public void saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings) {
            }

            @Override
            public List<AgentToolBinding> listBindings(String agentId, String versionId) {
                return List.of();
            }
        };
    }
}
