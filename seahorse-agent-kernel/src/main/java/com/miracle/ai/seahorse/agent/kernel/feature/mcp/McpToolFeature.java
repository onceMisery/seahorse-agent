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

package com.miracle.ai.seahorse.agent.kernel.feature.mcp;

import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;

/**
 * MCP 工具 Feature。
 * <p>
 * MCP 工具既是业务扩展点，也是外部工具调用边界。该接口只暴露工具执行端口，
 * 远程协议、HTTP 客户端和鉴权细节应放在 L3 Adapter 中。
 */
public interface McpToolFeature extends AgentFeature, McpToolExecutorPort {

    @Override
    default FeatureType type() {
        return FeatureType.MCP_TOOL;
    }

    /**
     * 返回工具的稳定元数据。
     *
     * <p>内核编排器通过该元数据完成参数抽取和工具匹配，避免感知工具来自本地 Bean 还是远程 MCP Server。
     *
     * @return MCP 工具元数据
     */
    McpToolDescriptor descriptor();

    @Override
    default String name() {
        return descriptor().toolId();
    }

    /**
     * 判断当前请求是否由该工具处理。
     *
     * @param request 工具执行请求
     * @return true 表示支持
     */
    default boolean supports(McpToolExecutionRequest request) {
        return request != null && descriptor().toolId().equals(request.toolId());
    }
}
