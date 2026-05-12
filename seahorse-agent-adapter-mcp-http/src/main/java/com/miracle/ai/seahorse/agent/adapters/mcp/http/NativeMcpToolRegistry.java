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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolFeature;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Seahorse 原生 MCP 工具注册表。
 *
 * <p>注册表聚合本地和远程 {@link McpToolFeature}，对内核暴露稳定端口。重复 toolId 按后注册覆盖，
 * 与旧注册表语义一致，便于配置级替换远程工具。
 */
public class NativeMcpToolRegistry implements McpToolRegistryPort {

    private final Map<String, McpToolFeature> executorMap;
    private final Map<String, McpToolDescriptor> descriptorMap;

    public NativeMcpToolRegistry(Collection<McpToolFeature> features) {
        this.executorMap = new LinkedHashMap<>();
        this.descriptorMap = new LinkedHashMap<>();
        for (McpToolFeature feature : Objects.requireNonNullElse(features, java.util.List.<McpToolFeature>of())) {
            register(feature);
        }
    }

    @Override
    public Optional<McpToolExecutorPort> findExecutor(String toolId) {
        return Optional.ofNullable(executorMap.get(toolId));
    }

    @Override
    public Optional<McpToolDescriptor> findTool(String toolId) {
        return Optional.ofNullable(descriptorMap.get(toolId));
    }

    /**
     * 注册 MCP 工具 Feature。
     *
     * @param feature 工具 Feature
     */
    public final void register(McpToolFeature feature) {
        if (feature == null) {
            return;
        }
        McpToolDescriptor descriptor = feature.descriptor();
        if (descriptor.toolId().isBlank()) {
            return;
        }
        executorMap.put(descriptor.toolId(), feature);
        descriptorMap.put(descriptor.toolId(), descriptor);
    }
}
