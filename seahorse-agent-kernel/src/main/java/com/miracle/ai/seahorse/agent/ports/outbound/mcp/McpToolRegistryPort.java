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

package com.miracle.ai.seahorse.agent.ports.outbound.mcp;

import java.util.Optional;

/**
 * MCP 工具注册端口。
 * <p>
 * L1 内核通过该端口查找工具执行器，不关心工具来自本地 Bean、远程服务还是租户级动态配置。
 */
public interface McpToolRegistryPort {

    /**
     * 查找工具执行器。
     *
     * @param toolId 工具 ID
     * @return 工具执行器，未找到时为空
     */
    Optional<McpToolExecutorPort> findExecutor(String toolId);

    /**
     * 查找工具元数据。
     *
     * @param toolId 工具 ID
     * @return 工具元数据，未找到时为空
     */
    default Optional<McpToolDescriptor> findTool(String toolId) {
        return Optional.empty();
    }

    /**
     * 创建空注册表。
     *
     * @return 空 MCP 工具注册表
     */
    static McpToolRegistryPort empty() {
        return toolId -> Optional.empty();
    }
}
