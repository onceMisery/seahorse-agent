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
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolExecutionPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolPermission;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
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

public class AgentScopeToolBridge {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String AGENTSCOPE_STEP_ID = "agentscope-step";

    private final ToolRegistryPort toolRegistry;
    private final GovernedToolExecutionPort toolExecution;
    private final ObjectMapper objectMapper;

    public AgentScopeToolBridge(ToolRegistryPort toolRegistry, GovernedToolExecutionPort toolExecution) {
        this(toolRegistry, toolExecution, new ObjectMapper());
    }

    public AgentScopeToolBridge(
            ToolRegistryPort toolRegistry,
            GovernedToolExecutionPort toolExecution,
            ObjectMapper objectMapper) {
        this.toolRegistry = Objects.requireNonNullElseGet(toolRegistry, ToolRegistryPort::empty);
        this.toolExecution = Objects.requireNonNull(toolExecution, "toolExecution must not be null");
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    public Toolkit toolkitFor(AgentLoopRequest request) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Toolkit toolkit = new Toolkit();
        List<ToolDescriptor> descriptors = exposedTools(safeRequest);
        List<String> effectiveAllowedToolIds = descriptors.stream()
                .map(ToolDescriptor::toolId)
                .toList();
        for (ToolDescriptor descriptor : descriptors) {
            toolkit.registerAgentTool(new SeahorseAgentScopeTool(
                    descriptor,
                    safeRequest,
                    effectiveAllowedToolIds,
                    parseSchema(descriptor.jsonSchema())));
        }
        return toolkit;
    }

    private List<ToolDescriptor> exposedTools(AgentLoopRequest request) {
        List<ToolDescriptor> allTools = toolRegistry.listTools();
        List<String> requestedToolIds = request.allowedToolIds();
        if (requestedToolIds.isEmpty()) {
            if (request.explicitToolAllowlist()) {
                return List.of();
            }
            return List.copyOf(allTools);
        }
        Set<String> allowed = requestedToolIds.stream().collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return List.of();
        }
        Map<String, ToolDescriptor> descriptorsById = allTools.stream()
                .filter(descriptor -> allowed.contains(descriptor.toolId()))
                .collect(Collectors.toMap(
                        ToolDescriptor::toolId,
                        descriptor -> descriptor,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return requestedToolIds.stream()
                .map(descriptorsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private final class SeahorseAgentScopeTool extends ToolBase {

        private final ToolDescriptor descriptor;
        private final AgentLoopRequest request;
        private final List<String> effectiveAllowedToolIds;

        private SeahorseAgentScopeTool(
                ToolDescriptor descriptor,
                AgentLoopRequest request,
                List<String> effectiveAllowedToolIds,
                Map<String, Object> inputSchema) {
            super(ToolBase.builder()
                    .name(Objects.requireNonNull(descriptor, "descriptor must not be null").toolId())
                    .description(Objects.requireNonNullElse(descriptor.description(), ""))
                    .inputSchema(Objects.requireNonNullElse(inputSchema, Map.of())));
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
            this.request = Objects.requireNonNull(request, "request must not be null");
            this.effectiveAllowedToolIds = effectiveAllowedToolIds == null
                    ? List.of()
                    : List.copyOf(effectiveAllowedToolIds);
        }

        @Override
        public Mono<PermissionDecision> checkPermissions(
                Map<String, Object> toolInput,
                PermissionContextState context) {
            return Mono.fromCallable(() -> preflight(toolInput));
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
            ToolInvocationResult result = invokeWithTenant(invocationRequest(toolCallId, input), request.tenantId());
            if (isApprovalRequired(result)) {
                throw new AgentScopeToolApprovalRequiredException(
                        toolCallId,
                        descriptor.toolId(),
                        result.approvalId(),
                        result.error());
            }
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

        private ToolInvocationResult invokeWithTenant(ToolInvocationRequest invocationRequest, String tenantId) {
            String previousTenant = TenantContext.capture();
            TenantContext.restore(tenantId);
            try {
                return toolExecution.invoke(invocationRequest);
            } finally {
                TenantContext.restore(previousTenant);
            }
        }

        private boolean isApprovalRequired(ToolInvocationResult result) {
            return result != null
                    && !result.success()
                    && result.approvalId() != null
                    && !result.approvalId().isBlank()
                    && ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED.equals(result.error());
        }

        private PermissionDecision preflight(Map<String, Object> input) {
            ToolInvocationRequest invocationRequest = invocationRequest(
                    "agentscope-permission-" + UUID.randomUUID(),
                    input == null ? Map.of() : input);
            GovernedToolPermission permission = preflightWithTenant(invocationRequest, request.tenantId());
            return switch (permission.effect()) {
                case ALLOW -> PermissionDecision.allow(permission.reasonMessage());
                case APPROVAL_REQUIRED -> PermissionDecision.ask(permission.reasonMessage());
                case DENY -> PermissionDecision.deny(permission.reasonMessage());
            };
        }

        private GovernedToolPermission preflightWithTenant(ToolInvocationRequest invocationRequest, String tenantId) {
            String previousTenant = TenantContext.capture();
            TenantContext.restore(tenantId);
            try {
                return toolExecution.preflight(invocationRequest);
            } finally {
                TenantContext.restore(previousTenant);
            }
        }

        private ToolInvocationRequest invocationRequest(String toolCallId, Map<String, Object> input) {
            return new ToolInvocationRequest(
                    request.runId(),
                    AGENTSCOPE_STEP_ID,
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
                    effectiveAllowedToolIds);
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
