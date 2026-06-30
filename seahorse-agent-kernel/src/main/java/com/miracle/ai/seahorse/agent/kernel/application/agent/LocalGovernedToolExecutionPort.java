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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

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
import java.util.UUID;

public class LocalGovernedToolExecutionPort implements GovernedToolExecutionPort {

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

    public LocalGovernedToolExecutionPort(
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

    @Override
    public GovernedToolPermission preflight(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Optional<ApprovalRequest> latestApproval = approvalQueryPort.findLatestByRunIdAndStepId(
                approvalRunId(safeRequest.runId(), safeRequest.toolCallId()),
                safeRequest.stepId());
        if (latestApproval.filter(approval -> approvalMatches(approval, safeRequest)).isPresent()) {
            return permissionFrom(latestApproval.orElseThrow());
        }

        PolicyDecision decision = Objects.requireNonNullElseGet(
                toolPolicy.decide(ToolPolicyRequest.from(
                        safeRequest,
                        toolRegistry.find(safeRequest.toolId()).isPresent())),
                () -> PolicyDecision.deny(
                        "governed-tool-policy-null",
                        ToolPolicyReasonCodes.POLICY_DECISION_MISSING,
                        "Tool policy did not return a decision"));
        if (decision.effect() == PolicyDecision.Effect.ALLOW) {
            return GovernedToolPermission.allow(decision.reasonCode(), decision.reasonMessage());
        }
        if (decision.effect() != PolicyDecision.Effect.APPROVAL_REQUIRED) {
            return GovernedToolPermission.deny(decision.reasonCode(), decision.reasonMessage());
        }
        ApprovalRequest approval = createApprovalRequest(safeRequest, decision, clock.instant());
        approvalRequestRepository.save(approval);
        return GovernedToolPermission.approvalRequired(
                approval.approvalId(),
                decision.reasonCode(),
                decision.reasonMessage());
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        return toolGateway.invoke(Objects.requireNonNull(request, "request must not be null"));
    }

    @Override
    public Optional<GovernedToolApproval> findLatestApproval(String runId, String stepId) {
        String safeRunId = trimToNull(runId);
        String safeStepId = trimToNull(stepId);
        if (safeRunId == null || safeStepId == null) {
            return Optional.empty();
        }
        return approvalQueryPort.findLatestByRunIdAndStepId(safeRunId, safeStepId)
                .map(this::toGovernedApproval);
    }

    private GovernedToolApproval toGovernedApproval(ApprovalRequest approval) {
        return new GovernedToolApproval(
                approval.approvalId(),
                approval.toolInvocationId(),
                approval.toolId(),
                approval.riskLevel(),
                approval.summary(),
                Map.of(),
                approval.requestedAt());
    }

    private GovernedToolPermission permissionFrom(ApprovalRequest approval) {
        if (approval.status() == ApprovalRequestStatus.APPROVED
                || approval.status() == ApprovalRequestStatus.MODIFIED) {
            return GovernedToolPermission.allow("APPROVAL_SATISFIED",
                    "Seahorse approval satisfied: " + approval.approvalId());
        }
        if (approval.status() == ApprovalRequestStatus.PENDING) {
            return GovernedToolPermission.approvalRequired(
                    approval.approvalId(),
                    ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                    "Seahorse approval required: " + approval.approvalId());
        }
        return GovernedToolPermission.deny("APPROVAL_REJECTED",
                "Seahorse approval rejected: " + approval.approvalId());
    }

    private boolean approvalMatches(ApprovalRequest approval, ToolInvocationRequest invocationRequest) {
        return approval != null
                && invocationRequest != null
                && invocationRequest.toolId().equals(approval.toolId())
                && Objects.equals(approval.argumentsPreviewJson(), argumentsPreviewJson(invocationRequest));
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

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }
}
