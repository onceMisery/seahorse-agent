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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AgentScopeToolFactory {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ToolRegistryPort toolRegistry;
    private final ToolGatewayPort toolGateway;
    private final ObjectMapper objectMapper;

    public AgentScopeToolFactory(ToolRegistryPort toolRegistry, ToolGatewayPort toolGateway) {
        this(toolRegistry, toolGateway, new ObjectMapper());
    }

    public AgentScopeToolFactory(ToolRegistryPort toolRegistry, ToolGatewayPort toolGateway, ObjectMapper objectMapper) {
        this.toolRegistry = Objects.requireNonNullElseGet(toolRegistry, ToolRegistryPort::empty);
        this.toolGateway = Objects.requireNonNull(toolGateway, "toolGateway must not be null");
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    public Toolkit toolkitFor(AgentLoopRequest request) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Toolkit toolkit = new Toolkit();
        for (ToolDescriptor descriptor : exposedTools(safeRequest)) {
            toolkit.registerAgentTool(new SeahorseAgentScopeTool(descriptor, safeRequest));
        }
        return toolkit;
    }

    private List<ToolDescriptor> exposedTools(AgentLoopRequest request) {
        Set<String> allowed = request.allowedToolIds().stream().collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return List.of();
        }
        Map<String, ToolDescriptor> descriptorsById = toolRegistry.listTools().stream()
                .filter(descriptor -> allowed.contains(descriptor.toolId()))
                .collect(Collectors.toMap(
                        ToolDescriptor::toolId,
                        descriptor -> descriptor,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return request.allowedToolIds().stream()
                .map(descriptorsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private final class SeahorseAgentScopeTool implements AgentTool {

        private final ToolDescriptor descriptor;
        private final AgentLoopRequest request;

        private SeahorseAgentScopeTool(ToolDescriptor descriptor, AgentLoopRequest request) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
            this.request = Objects.requireNonNull(request, "request must not be null");
        }

        @Override
        public String getName() {
            return descriptor.toolId();
        }

        @Override
        public String getDescription() {
            return descriptor.description();
        }

        @Override
        public Map<String, Object> getParameters() {
            return parseSchema(descriptor.jsonSchema());
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(() -> invoke(param));
        }

        private ToolResultBlock invoke(ToolCallParam param) {
            ToolUseBlock toolUseBlock = param == null ? null : param.getToolUseBlock();
            String toolCallId = textOrDefault(toolUseBlock == null ? null : toolUseBlock.getId(),
                    "agentscope-tool-" + UUID.randomUUID());
            Map<String, Object> input = param == null || param.getInput() == null ? Map.of() : param.getInput();
            ToolInvocationResult result = toolGateway.invoke(new ToolInvocationRequest(
                    request.runId(),
                    "agentscope-step",
                    toolCallId,
                    request.agentId(),
                    request.versionId(),
                    request.rolloutId(),
                    request.tenantId(),
                    request.userId(),
                    request.agentIdentityId(),
                    descriptor.toolId(),
                    input,
                    Map.of(),
                    request.runId() == null ? toolCallId : request.runId() + ":" + toolCallId,
                    request.allowedToolIds()));
            String output = result.success()
                    ? Objects.requireNonNullElse(result.content(), "")
                    : Objects.requireNonNullElse(result.error(), "Tool invocation failed");
            return ToolResultBlock.builder()
                    .id(toolCallId)
                    .name(descriptor.toolId())
                    .output(TextBlock.builder().text(output).build())
                    .state(result.success() ? ToolResultState.SUCCESS : ToolResultState.ERROR)
                    .build();
        }
    }

    private Map<String, Object> parseSchema(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(jsonSchema, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }
}
