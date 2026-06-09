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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ToolSearchToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "tool_search";
    public static final String ALLOWED_TOOL_IDS_ARGUMENT = "_seahorseAllowedToolIds";

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Tool Search",
            "Search runtime-allowed tool metadata without exposing denied tools.",
            "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                    + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}},\"required\":[\"query\"]}");

    private final ToolRegistryPort toolRegistry;
    private final AgentToolJsonSupport jsonSupport;

    public ToolSearchToolPortAdapter(ToolRegistryPort toolRegistry, AgentToolJsonSupport jsonSupport) {
        this.toolRegistry = Objects.requireNonNullElseGet(toolRegistry, ToolRegistryPort::empty);
        this.jsonSupport = Objects.requireNonNullElseGet(jsonSupport, () -> new AgentToolJsonSupport(null));
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        if (!TOOL_ID.equals(toolId)) {
            return ToolInvocationResult.failed("Tool id mismatch");
        }
        List<String> allowedToolIds = allowedToolIds(arguments);
        if (allowedToolIds == null) {
            return ToolInvocationResult.failed("allowed tool snapshot is required");
        }
        Set<String> allowed = Set.copyOf(allowedToolIds);
        String query = jsonSupport.string(arguments, "query").toLowerCase(Locale.ROOT);
        int limit = jsonSupport.boundedInt(arguments, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        List<Map<String, Object>> tools = toolRegistry.listTools().stream()
                .filter(tool -> allowed.contains(tool.toolId()))
                .filter(tool -> matches(tool, query))
                .limit(limit)
                .map(this::metadata)
                .toList();
        return ToolInvocationResult.ok(jsonSupport.write(Map.of("tools", tools)));
    }

    private List<String> allowedToolIds(Map<String, Object> arguments) {
        Object value = arguments == null ? null : arguments.get(ALLOWED_TOOL_IDS_ARGUMENT);
        if (!(value instanceof List<?> values)) {
            return null;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private boolean matches(ToolDescriptor tool, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return contains(tool.toolId(), query)
                || contains(tool.name(), query)
                || contains(tool.description(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private Map<String, Object> metadata(ToolDescriptor tool) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolId", tool.toolId());
        payload.put("name", tool.name());
        payload.put("description", tool.description());
        return payload;
    }
}
