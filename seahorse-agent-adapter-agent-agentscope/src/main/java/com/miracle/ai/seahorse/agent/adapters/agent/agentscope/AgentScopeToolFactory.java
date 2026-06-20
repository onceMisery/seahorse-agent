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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.Toolkit;
import reactor.core.publisher.Mono;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AgentScopeToolFactory {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String AGENTSCOPE_STEP_ID = "agentscope-step";
    private static final String APPROVAL_ID_PREFIX = "approval:";
    private static final String LEGACY_RUN_ID_PREFIX = "legacy-run:";
    private static final String LEGACY_USER_ID = "legacy-user";

    private final ToolRegistryPort toolRegistry;
    private final ToolGatewayPort toolGateway;
    private final ToolPolicyPort toolPolicy;
    private final ToolApprovalRequestRepositoryPort approvalRequestRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentScopeToolFactory(ToolRegistryPort toolRegistry, ToolGatewayPort toolGateway) {
        this(toolRegistry, toolGateway, new ObjectMapper());
    }

    public AgentScopeToolFactory(ToolRegistryPort toolRegistry, ToolGatewayPort toolGateway, ObjectMapper objectMapper) {
        this(
                toolRegistry,
                toolGateway,
                ToolPolicyPort.defaults(),
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                objectMapper,
                Clock.systemUTC());
    }

    public AgentScopeToolFactory(
            ToolRegistryPort toolRegistry,
            ToolGatewayPort toolGateway,
            ToolPolicyPort toolPolicy,
            ToolApprovalRequestRepositoryPort approvalRequestRepository) {
        this(
                toolRegistry,
                toolGateway,
                toolPolicy,
                approvalRequestRepository,
                ApprovalRequestQueryPort.empty(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    public AgentScopeToolFactory(
            ToolRegistryPort toolRegistry,
            ToolGatewayPort toolGateway,
            ToolPolicyPort toolPolicy,
            ToolApprovalRequestRepositoryPort approvalRequestRepository,
            ApprovalRequestQueryPort approvalQueryPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.toolRegistry = Objects.requireNonNullElseGet(toolRegistry, ToolRegistryPort::empty);
        this.toolGateway = Objects.requireNonNull(toolGateway, "toolGateway must not be null");
        this.toolPolicy = Objects.requireNonNullElseGet(toolPolicy, ToolPolicyPort::defaults);
        this.approvalRequestRepository = Objects.requireNonNullElseGet(
                approvalRequestRepository,
                ToolApprovalRequestRepositoryPort::noop);
        this.approvalQueryPort = Objects.requireNonNullElseGet(approvalQueryPort, ApprovalRequestQueryPort::empty);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
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
            ToolInvocationResult result = invokeWithTenant(new ToolInvocationRequest(
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
                            effectiveAllowedToolIds),
                    request.tenantId());
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
                return toolGateway.invoke(invocationRequest);
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
            Optional<ApprovalRequest> latestApproval = approvalQueryPort.findLatestByRunIdAndStepId(
                    approvalRunId(invocationRequest.runId(), invocationRequest.toolCallId()),
                    AGENTSCOPE_STEP_ID);
            if (latestApproval.filter(approval -> approvalMatches(approval, invocationRequest)).isPresent()) {
                ApprovalRequest approval = latestApproval.orElseThrow();
                if (approval.status() == ApprovalRequestStatus.APPROVED
                        || approval.status() == ApprovalRequestStatus.MODIFIED) {
                    return PermissionDecision.allow("Seahorse approval satisfied: " + approval.approvalId());
                }
                if (approval.status() == ApprovalRequestStatus.PENDING) {
                    return PermissionDecision.ask("Seahorse approval required: " + approval.approvalId());
                }
                return PermissionDecision.deny("Seahorse approval rejected: " + approval.approvalId());
            }

            PolicyDecision decision = Objects.requireNonNullElseGet(
                    toolPolicy.decide(ToolPolicyRequest.from(
                            invocationRequest,
                            toolRegistry.find(descriptor.toolId()).isPresent())),
                    () -> PolicyDecision.deny(
                            "agentscope-tool-policy-null",
                            ToolPolicyReasonCodes.POLICY_DECISION_MISSING,
                            "Tool policy did not return a decision"));
            if (decision.effect() == PolicyDecision.Effect.ALLOW) {
                return PermissionDecision.allow(decision.reasonMessage());
            }
            if (decision.effect() != PolicyDecision.Effect.APPROVAL_REQUIRED) {
                return PermissionDecision.deny(decision.reasonMessage());
            }
            ApprovalRequest approval = createApprovalRequest(invocationRequest, decision, clock.instant());
            approvalRequestRepository.save(approval);
            return PermissionDecision.ask("Seahorse approval required: " + approval.approvalId());
        }

        private boolean approvalMatches(ApprovalRequest approval, ToolInvocationRequest invocationRequest) {
            return approval != null
                    && invocationRequest != null
                    && descriptor.toolId().equals(approval.toolId())
                    && Objects.equals(approval.argumentsPreviewJson(), argumentsPreviewJson(invocationRequest));
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

        private ApprovalRequest createApprovalRequest(
                ToolInvocationRequest invocationRequest,
                PolicyDecision decision,
                Instant requestedAt) {
            String approvalId = APPROVAL_ID_PREFIX + UUID.randomUUID();
            return new ApprovalRequest(
                    approvalId,
                    approvalRunId(invocationRequest.runId(), invocationRequest.toolCallId()),
                    invocationRequest.stepId(),
                    invocationRequest.toolCallId(),
                    invocationRequest.tenantId(),
                    textOrDefault(invocationRequest.userId(), LEGACY_USER_ID),
                    invocationRequest.agentId(),
                    invocationRequest.rolloutId(),
                    invocationRequest.toolId(),
                    ApprovalType.TOOL_EXECUTION,
                    ToolRiskLevel.HIGH,
                    truncate("Tool " + invocationRequest.toolId() + " requires approval: " + decision.reasonCode()),
                    argumentsPreviewJson(invocationRequest),
                    ApprovalRequestStatus.PENDING,
                    requestedAt,
                    null,
                    null,
                    null,
                    null);
        }

        private String argumentsPreviewJson(ToolInvocationRequest invocationRequest) {
            try {
                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("argumentKeys", sortedKeys(invocationRequest.arguments()));
                preview.put("argumentCount", invocationRequest.arguments().size());
                preview.put("argumentHash", sha256(canonicalJson(invocationRequest.arguments())));
                preview.put("resourceRefs", invocationRequest.resourceRefs());
                return truncate(objectMapper.writeValueAsString(preview));
            } catch (JsonProcessingException ex) {
                return truncate("keys=" + invocationRequest.arguments().keySet()
                        + ", size=" + invocationRequest.arguments().size());
            }
        }

        private List<String> sortedKeys(Map<String, Object> arguments) {
            return arguments.keySet().stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
        }

        private String canonicalJson(Object value) throws JsonProcessingException {
            return objectMapper.writeValueAsString(canonicalValue(value));
        }

        private Object canonicalValue(Object value) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.entrySet().stream()
                        .filter(entry -> entry.getKey() != null)
                        .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                        .forEach(entry -> result.put(String.valueOf(entry.getKey()), canonicalValue(entry.getValue())));
                return result;
            }
            if (value instanceof Iterable<?> iterable) {
                List<Object> result = new ArrayList<>();
                iterable.forEach(item -> result.add(canonicalValue(item)));
                return result;
            }
            if (value != null && value.getClass().isArray()) {
                List<Object> result = new ArrayList<>();
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    result.add(canonicalValue(Array.get(value, i)));
                }
                return result;
            }
            return value;
        }

        private String sha256(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder(bytes.length * 2);
                for (byte item : bytes) {
                    result.append(String.format("%02x", item));
                }
                return result.toString();
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is not available", ex);
            }
        }

        private String approvalRunId(String runId, String invocationId) {
            return textOrDefault(runId, LEGACY_RUN_ID_PREFIX + invocationId);
        }

        private String truncate(String value) {
            if (value == null || value.length() <= 1000) {
                return value;
            }
            return value.substring(0, 1000);
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
