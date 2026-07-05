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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolArtifactPublicationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolOutputRedactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LocalToolGatewayPort implements ToolGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(LocalToolGatewayPort.class);
    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final String APPROVAL_ID_PREFIX = "approval:";
    private static final String LEGACY_RUN_ID_PREFIX = "legacy-run:";
    private static final String LEGACY_USER_ID = "legacy-user";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolRegistryPort toolRegistry;
    private final ToolPolicyPort toolPolicy;
    private final ToolInvocationAuditPort auditPort;
    private final ToolApprovalRequestRepositoryPort approvalRequestRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final ToolOutputRedactionPort outputRedactionPort;
    private final ToolArtifactPublicationPort artifactPublicationPort;
    private final Clock clock;

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry) {
        this(toolRegistry, ToolPolicyPort.defaults());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry, ToolPolicyPort toolPolicy) {
        this(toolRegistry, toolPolicy, ToolInvocationAuditPort.noop());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort) {
        this(toolRegistry, toolPolicy, auditPort, Clock.systemUTC());
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                Clock clock) {
        this(toolRegistry, toolPolicy, auditPort, ToolApprovalRequestRepositoryPort.noop(), clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolOutputRedactionPort outputRedactionPort,
                                Clock clock) {
        this(toolRegistry,
                toolPolicy,
                auditPort,
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                outputRedactionPort,
                ToolArtifactPublicationPort.noop(),
                clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                Clock clock) {
        this(toolRegistry, toolPolicy, auditPort, approvalRequestRepository, ApprovalRequestQueryPort.empty(), clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                ApprovalRequestQueryPort approvalQueryPort,
                                Clock clock) {
        this(toolRegistry,
                toolPolicy,
                auditPort,
                approvalRequestRepository,
                approvalQueryPort,
                ToolOutputRedactionPort.noop(),
                ToolArtifactPublicationPort.noop(),
                clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                ApprovalRequestQueryPort approvalQueryPort,
                                ToolOutputRedactionPort outputRedactionPort,
                                Clock clock) {
        this(toolRegistry,
                toolPolicy,
                auditPort,
                approvalRequestRepository,
                approvalQueryPort,
                outputRedactionPort,
                ToolArtifactPublicationPort.noop(),
                clock);
    }

    public LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                ToolPolicyPort toolPolicy,
                                ToolInvocationAuditPort auditPort,
                                ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                ApprovalRequestQueryPort approvalQueryPort,
                                ToolOutputRedactionPort outputRedactionPort,
                                ToolArtifactPublicationPort artifactPublicationPort,
                                Clock clock) {
        this.toolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolPolicy = Objects.requireNonNullElseGet(toolPolicy, ToolPolicyPort::defaults);
        this.auditPort = Objects.requireNonNullElseGet(auditPort, ToolInvocationAuditPort::noop);
        this.approvalRequestRepository = Objects.requireNonNullElseGet(
                approvalRequestRepository,
                ToolApprovalRequestRepositoryPort::noop);
        this.approvalQueryPort = Objects.requireNonNullElseGet(approvalQueryPort, ApprovalRequestQueryPort::empty);
        this.outputRedactionPort = Objects.requireNonNullElseGet(outputRedactionPort, ToolOutputRedactionPort::noop);
        this.artifactPublicationPort = Objects.requireNonNullElseGet(
                artifactPublicationPort,
                ToolArtifactPublicationPort::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String invocationId = nextInvocationId();
        String effectiveRunId = auditRunId(safeRequest.runId(), invocationId);
        String effectiveUserId = auditUserId(safeRequest.userId());
        Instant startedAt = clock.instant();
        auditPort.recordRequested(new ToolInvocationAuditRecord(
                invocationId,
                effectiveRunId,
                safeRequest.stepId(),
                safeRequest.agentId(),
                safeRequest.versionId(),
                safeRequest.rolloutId(),
                safeRequest.tenantId(),
                effectiveUserId,
                safeRequest.toolId(),
                safeRequest.idempotencyKey(),
                ToolInvocationStatus.REQUESTED,
                summarizeArguments(safeRequest),
                startedAt));
        Optional<ToolPort> toolPort = toolRegistry.find(safeRequest.toolId());

        // 策略裁决必须发生在真实工具执行之前；非 ALLOW 结果不得触达 ToolPort。
        PolicyDecision decision = Objects.requireNonNullElseGet(
                toolPolicy.decide(ToolPolicyRequest.from(safeRequest, toolPort.isPresent())),
                () -> PolicyDecision.deny("builtin-policy-null", ToolPolicyReasonCodes.POLICY_DECISION_MISSING,
                        "Tool policy did not return a decision"));
        boolean approvalSatisfied = approvalSatisfied(safeRequest, decision);
        ToolInvocationStatus decisionStatus = approvalSatisfied ? ToolInvocationStatus.ALLOWED : decisionStatus(decision);
        auditPort.recordDecision(new ToolInvocationAuditDecision(invocationId, decision.decisionId(), decisionStatus));
        if (!decision.allowsExecution() && !approvalSatisfied) {
            String approvalId = null;
            if (decision.effect() == PolicyDecision.Effect.APPROVAL_REQUIRED) {
                approvalId = createApprovalRequest(
                        safeRequest,
                        decision,
                        invocationId,
                        effectiveRunId,
                        effectiveUserId,
                        startedAt);
            }
            ToolInvocationResult result = ToolInvocationResult.failed(decision.reasonCode(), approvalId);
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    decisionStatus,
                    null,
                    result.error(),
                    clock.instant()));
            return result;
        }

        try {
            ToolPort executableTool = toolPort
                    .orElseGet(() -> ToolPort.notFound(safeRequest.toolId()));
            ToolInvocationResult rawResult = executableTool instanceof ToolInvocationRequestAwarePort awareTool
                    ? awareTool.invoke(safeRequest)
                    : executableTool.invoke(safeRequest.toolCallId(), safeRequest.toolId(), safeRequest.arguments());
            if (rawResult.success()) {
                publishArtifacts(safeRequest, rawResult);
            }
            ToolInvocationResult result = outputRedactionPort.redact(safeRequest, rawResult);
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    result.success() ? ToolInvocationStatus.SUCCEEDED : ToolInvocationStatus.FAILED,
                    summarizeResult(result),
                    result.error(),
                    clock.instant()));
            return result;
        } catch (Exception ex) {
            ToolInvocationResult result = ToolInvocationResult.failed(
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    ToolInvocationStatus.FAILED,
                    null,
                    result.error(),
                    clock.instant()));
            return result;
        }
    }

    private void publishArtifacts(ToolInvocationRequest request, ToolInvocationResult result) {
        try {
            artifactPublicationPort.publish(request, result);
        } catch (RuntimeException ex) {
            // Artifact publication is a side effect; the tool observation remains authoritative.
            log.warn("Artifact publication failed for tool={}, error={}",
                     request.toolId(), ex.getMessage(), ex);
        }
    }

    private String nextInvocationId() {
        return SnowflakeIds.nextIdString();
    }

    private String createApprovalRequest(ToolInvocationRequest request,
                                         PolicyDecision decision,
                                         String invocationId,
                                         String effectiveRunId,
                                         String effectiveUserId,
                                         Instant requestedAt) {
        // 审批请求保存的是可展示的参数预览，不保存完整敏感入参；真正恢复执行由后续 durable runtime 切片接管。
        String approvalId = approvalId(invocationId);
        approvalRequestRepository.save(new ApprovalRequest(
                approvalId,
                effectiveRunId,
                request.stepId(),
                invocationId,
                request.tenantId(),
                effectiveUserId,
                request.agentId(),
                request.rolloutId(),
                request.toolId(),
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                approvalSummary(request, decision),
                argumentsPreviewJson(request),
                ApprovalRequestStatus.PENDING,
                requestedAt,
                null,
                null,
                null,
                null));
        return approvalId;
    }

    private String approvalId(String invocationId) {
        return APPROVAL_ID_PREFIX + invocationId;
    }

    private String approvalSummary(ToolInvocationRequest request, PolicyDecision decision) {
        return truncate("Tool " + request.toolId() + " requires approval: " + decision.reasonCode());
    }

    private String argumentsPreviewJson(ToolInvocationRequest request) {
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(Map.of(
                    "argumentKeys", request.arguments().keySet(),
                    "argumentCount", request.arguments().size(),
                    "resourceRefs", request.resourceRefs())));
        } catch (JsonProcessingException ex) {
            return truncate("keys=" + request.arguments().keySet() + ", size=" + request.arguments().size());
        }
    }

    private String auditRunId(String runId, String invocationId) {
        if (runId != null && !runId.isBlank()) {
            return runId;
        }
        // 兼容直接调用 KernelAgentLoop 的 legacy 路径，避免持久审计因为缺少 runId 中断工具执行。
        return LEGACY_RUN_ID_PREFIX + invocationId;
    }

    private String auditUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        // 兼容没有登录上下文的 legacy 调用，企业运行时应始终传入真实 userId。
        return LEGACY_USER_ID;
    }

    private ToolInvocationStatus decisionStatus(PolicyDecision decision) {
        return switch (decision.effect()) {
            case ALLOW -> ToolInvocationStatus.ALLOWED;
            case APPROVAL_REQUIRED -> ToolInvocationStatus.APPROVAL_REQUIRED;
            default -> ToolInvocationStatus.DENIED;
        };
    }

    private boolean approvalSatisfied(ToolInvocationRequest request, PolicyDecision decision) {
        if (decision.effect() != PolicyDecision.Effect.APPROVAL_REQUIRED) {
            return false;
        }
        return approvalQueryPort.findLatestByRunIdAndStepId(request.runId(), request.stepId())
                .filter(approval -> approval.status() == ApprovalRequestStatus.APPROVED
                        || approval.status() == ApprovalRequestStatus.MODIFIED)
                .isPresent();
    }

    private String summarizeArguments(ToolInvocationRequest request) {
        if ("sandbox_browser".equals(request.toolId())) {
            return summarizeSandboxBrowserArguments(request);
        }
        if ("invoke_remote_a2a_agent".equals(request.toolId())) {
            return summarizeRemoteA2aArguments(request);
        }
        return truncate("keys=" + request.arguments().keySet() + ", size=" + request.arguments().size());
    }

    private String summarizeRemoteA2aArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        Map<String, Object> metadata = mapValue(arguments.get("metadata"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("agentName", argumentString(arguments, "agentName"));
        summary.put("promptLength", argumentString(arguments, "prompt").length());
        summary.put("metadataKeys", metadata.keySet());
        summary.put("metadataCount", metadata.size());
        if (hasText(argumentString(metadata, "version"))) {
            summary.put("version", argumentString(metadata, "version"));
        }
        summary.put("argumentKeys", arguments.keySet());
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=invoke_remote_a2a_agent, agentName="
                    + argumentString(arguments, "agentName")
                    + ", promptLength=" + argumentString(arguments, "prompt").length()
                    + ", metadataKeys=" + metadata.keySet()
                    + ", metadataCount=" + metadata.size());
        }
    }

    private String summarizeSandboxBrowserArguments(ToolInvocationRequest request) {
        Map<String, Object> arguments = request.arguments();
        boolean urlMode = hasText(argumentString(arguments, "url"));
        List<String> allowedHosts = argumentStringList(arguments.get("allowedHosts"));
        int cookieCount = listSize(arguments.get("cookies"));
        Map<String, Object> sessionState = mapValue(arguments.get("sessionState"));
        int sessionCookieCount = listSize(sessionState.get("cookies"));
        int sessionOriginCount = listSize(sessionState.get("origins"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolId", request.toolId());
        summary.put("mode", urlMode ? "url" : "inline");
        summary.put("action", argumentString(arguments, "action", "snapshot"));
        summary.put("networkRequested", urlMode);
        summary.put("allowedHosts", allowedHosts);
        summary.put("cookieCount", cookieCount);
        summary.put("sessionStateReplayRequested", !sessionState.isEmpty());
        summary.put("sessionStateCookieCount", sessionCookieCount);
        summary.put("sessionStateOriginCount", sessionOriginCount);
        summary.put("captureSessionState", booleanArgument(arguments, "captureSessionState"));
        summary.put("har", booleanArgument(arguments, "har"));
        summary.put("video", booleanArgument(arguments, "video"));
        summary.put("argumentKeys", arguments.keySet());
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("toolId=sandbox_browser, mode=" + (urlMode ? "url" : "inline")
                    + ", allowedHosts=" + allowedHosts
                    + ", cookieCount=" + cookieCount
                    + ", sessionStateReplayRequested=" + !sessionState.isEmpty()
                    + ", sessionStateCookieCount=" + sessionCookieCount
                    + ", sessionStateOriginCount=" + sessionOriginCount);
        }
    }

    private String argumentString(Map<String, Object> arguments, String name) {
        return argumentString(arguments, name, "");
    }

    private String argumentString(Map<String, Object> arguments, String name, String defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString().trim();
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private int listSize(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    private List<String> argumentStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(item -> item.toString().trim())
                    .filter(LocalToolGatewayPort::hasText)
                    .toList();
        }
        if (value instanceof String text && hasText(text)) {
            List<String> items = new ArrayList<>();
            for (String item : text.split(",")) {
                if (hasText(item)) {
                    items.add(item.trim());
                }
            }
            return items;
        }
        return List.of();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null) {
                result.put(key.toString(), item);
            }
        });
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String summarizeResult(ToolInvocationResult result) {
        if (result == null || result.content() == null) {
            return null;
        }
        return truncate("length=" + result.content().length());
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
