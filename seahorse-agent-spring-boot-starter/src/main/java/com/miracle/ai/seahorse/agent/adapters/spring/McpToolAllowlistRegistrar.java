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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.McpToolPortAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将配置 allowlist 中的 MCP 工具注册为 Agent 工具。
 */
public class McpToolAllowlistRegistrar implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolAllowlistRegistrar.class);

    private final McpToolPortAdapter adapter;
    private final McpToolRegistryPort mcpRegistry;
    private final ToolRegistryPort toolRegistry;
    private final List<String> includeToolIds;
    private final ObjectMapper objectMapper;

    public McpToolAllowlistRegistrar(McpToolPortAdapter adapter,
                                     McpToolRegistryPort mcpRegistry,
                                     ToolRegistryPort toolRegistry,
                                     List<String> includeToolIds,
                                     ObjectMapper objectMapper) {
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.mcpRegistry = Objects.requireNonNull(mcpRegistry, "mcpRegistry must not be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.includeToolIds = List.copyOf(Objects.requireNonNullElse(includeToolIds, List.of()));
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String toolId : includeToolIds) {
            register(toolId);
        }
    }

    private void register(String toolId) {
        if (toolId == null || toolId.isBlank() || toolRegistry.find(toolId).isPresent()) {
            return;
        }
        mcpRegistry.findTool(toolId)
                .ifPresentOrElse(
                        descriptor -> registerTool(descriptor, toolId),
                        () -> LOG.warn("MCP tool allowlist item not found: {}", toolId));
    }

    private void registerTool(McpToolDescriptor descriptor, String toolId) {
        try {
            toolRegistry.register(toToolDescriptor(descriptor), adapter);
        } catch (UnsupportedOperationException ex) {
            LOG.warn("ToolRegistryPort does not support dynamic MCP tool registration: toolId={}", toolId, ex);
        }
    }

    private ToolDescriptor toToolDescriptor(McpToolDescriptor descriptor) {
        return new ToolDescriptor(
                descriptor.toolId(),
                descriptor.toolId(),
                descriptor.description(),
                toJsonSchema(descriptor));
    }

    private String toJsonSchema(McpToolDescriptor descriptor) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map.Entry<String, McpToolDescriptor.Parameter> entry : descriptor.parameters().entrySet()) {
            McpToolDescriptor.Parameter parameter = entry.getValue();
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", parameter.type());
            if (!parameter.description().isBlank()) {
                property.put("description", parameter.description());
            }
            if (parameter.defaultValue() != null) {
                property.put("default", parameter.defaultValue());
            }
            if (!parameter.enumValues().isEmpty()) {
                property.put("enum", parameter.enumValues());
            }
            properties.put(entry.getKey(), property);
            if (parameter.required()) {
                required.add(entry.getKey());
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException ex) {
            LOG.warn("MCP tool schema serialization failed: toolId={}", descriptor.toolId(), ex);
            return "{}";
        }
    }
}
