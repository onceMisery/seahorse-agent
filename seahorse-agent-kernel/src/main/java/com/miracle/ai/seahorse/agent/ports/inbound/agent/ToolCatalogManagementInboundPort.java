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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;

import java.util.Optional;

public interface ToolCatalogManagementInboundPort {

    /**
     * 分页查询工具目录，用于管理端查看工具风险、动作类型和启用状态。
     */
    ToolCatalogPage page(String resourceType, String keyword, long current, long size, Boolean enabled);

    /**
     * 按工具 ID 查询工具目录详情。
     */
    Optional<ToolCatalogEntry> findById(String toolId);

    /**
     * 启用工具，使后续策略决策可以继续评估该工具。
     */
    ToolCatalogEntry enable(String toolId);

    /**
     * 禁用工具，禁用后策略层必须拒绝该工具执行。
     */
    ToolCatalogEntry disable(String toolId);
}
