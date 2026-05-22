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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;

import java.util.Optional;

public interface ToolCatalogRepositoryPort {

    /**
     * 保存或更新工具目录条目。
     */
    void save(ToolCatalogEntry entry);

    /**
     * 按工具 ID 查询目录条目。
     */
    Optional<ToolCatalogEntry> findById(String toolId);

    /**
     * 启用或禁用工具；禁用工具必须被策略拒绝执行。
     */
    void setEnabled(String toolId, boolean enabled);

    /**
     * 空目录实现，用于没有配置 catalog 仓储时保持依赖可空安全。
     */
    static ToolCatalogRepositoryPort empty() {
        return new ToolCatalogRepositoryPort() {
            @Override
            public void save(ToolCatalogEntry entry) {
            }

            @Override
            public Optional<ToolCatalogEntry> findById(String toolId) {
                return Optional.empty();
            }

            @Override
            public void setEnabled(String toolId, boolean enabled) {
            }
        };
    }
}
