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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationIdempotencyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolOutputRedactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceReferenceResolverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResultSpillPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LocalToolGatewayPort implements ToolGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(LocalToolGatewayPort.class);
    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final int MAX_PREVIEW_ARGUMENT_KEY_LENGTH = 64;
    private static final String APPROVAL_ID_PREFIX = "approval:";
    private static final String LEGACY_RUN_ID_PREFIX = "legacy-run:";
    private static final String LEGACY_USER_ID = "legacy-user";
    private final ToolRegistryPort toolRegistry;
    private final ToolPolicyPort toolPolicy;
    private final ToolInvocationAuditPort auditPort;
    private final ToolApprovalRequestRepositoryPort approvalRequestRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final ToolOutputRedactionPort outputRedactionPort;
    private final ToolArtifactPublicationPort artifactPublicationPort;
    private final ToolResultSpillPort toolResultSpillPort;
    private final ToolInvocationIdempotencyPort idempotencyPort;
    private final Clock clock;
    private final ToolArgumentAuditSummary auditSummary;

    private LocalToolGatewayPort(ToolRegistryPort toolRegistry,
                                 ToolPolicyPort toolPolicy,
                                 ToolInvocationAuditPort auditPort,
                                 ToolApprovalRequestRepositoryPort approvalRequestRepository,
                                 ApprovalRequestQueryPort approvalQueryPort,
                                 ToolOutputRedactionPort outputRedactionPort,
                                 ToolArtifactPublicationPort artifactPublicationPort,
                                 ToolResultSpillPort toolResultSpillPort,
                                 ToolInvocationIdempotencyPort idempotencyPort,
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
        this.toolResultSpillPort = Objects.requireNonNullElseGet(
                toolResultSpillPort,
                ToolResultSpillPort::noop);
        this.idempotencyPort = Objects.requireNonNullElseGet(
                idempotencyPort,
                ToolInvocationIdempotencyPort::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.auditSummary = new ToolArgumentAuditSummary();
    }

    /**
     * 构造器重载已折叠为 Builder：{{@code LocalToolGatewayPort.builder().toolRegistry(...)...build()}}。
     * 可选依赖均有默认实现（noop/empty/defaults），按需覆盖即可。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ToolRegistryPort toolRegistry;
        private ToolPolicyPort toolPolicy = ToolPolicyPort.defaults();
        private ToolInvocationAuditPort auditPort = ToolInvocationAuditPort.noop();
        private ToolApprovalRequestRepositoryPort approvalRequestRepository = ToolApprovalRequestRepositoryPort.noop();
        private ApprovalRequestQueryPort approvalQueryPort = ApprovalRequestQueryPort.empty();
        private ToolOutputRedactionPort outputRedactionPort = ToolOutputRedactionPort.noop();
        private ToolArtifactPublicationPort artifactPublicationPort = ToolArtifactPublicationPort.noop();
        private ToolResultSpillPort toolResultSpillPort = ToolResultSpillPort.noop();
        private ToolInvocationIdempotencyPort idempotencyPort = ToolInvocationIdempotencyPort.noop();
        private Clock clock;

        public Builder toolRegistry(ToolRegistryPort toolRegistry) {
            this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
            return this;
        }

        public Builder toolPolicy(ToolPolicyPort toolPolicy) {
            this.toolPolicy = Objects.requireNonNullElseGet(toolPolicy, ToolPolicyPort::defaults);
            return this;
        }

        public Builder auditPort(ToolInvocationAuditPort auditPort) {
            this.auditPort = Objects.requireNonNullElseGet(auditPort, ToolInvocationAuditPort::noop);
            return this;
        }

        public Builder approvalRequestRepository(ToolApprovalRequestRepositoryPort approvalRequestRepository) {
            this.approvalRequestRepository = Objects.requireNonNullElseGet(
                    approvalRequestRepository,
                    ToolApprovalRequestRepositoryPort::noop);
            return this;
        }

        public Builder approvalQueryPort(ApprovalRequestQueryPort approvalQueryPort) {
            this.approvalQueryPort = Objects.requireNonNullElseGet(approvalQueryPort, ApprovalRequestQueryPort::empty);
            return this;
        }

        public Builder outputRedactionPort(ToolOutputRedactionPort outputRedactionPort) {
            this.outputRedactionPort = Objects.requireNonNullElseGet(outputRedactionPort, ToolOutputRedactionPort::noop);
            return this;
        }

        public Builder artifactPublicationPort(ToolArtifactPublicationPort artifactPublicationPort) {
            this.artifactPublicationPort = Objects.requireNonNullElseGet(
                    artifactPublicationPort,
                    ToolArtifactPublicationPort::noop);
            return this;
        }

        public Builder toolResultSpillPort(ToolResultSpillPort toolResultSpillPort) {
            this.toolResultSpillPort = Objects.requireNonNullElseGet(toolResultSpillPort, ToolResultSpillPort::noop);
            return this;
        }

        public Builder idempotencyPort(ToolInvocationIdempotencyPort idempotencyPort) {
            this.idempotencyPort = Objects.requireNonNullElseGet(idempotencyPort, ToolInvocationIdempotencyPort::noop);
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
            return this;
        }

        public LocalToolGatewayPort build() {
            return new LocalToolGatewayPort(
                    Objects.requireNonNull(toolRegistry, "toolRegistry must not be null"),
                    toolPolicy,
                    auditPort,
                    approvalRequestRepository,
                    approvalQueryPort,
                    outputRedactionPort,
                    artifactPublicationPort,
                    toolResultSpillPort,
                    idempotencyPort,
                    clock);
        }
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String invocationId = nextInvocationId();
        String effectiveRunId = auditRunId(safeRequest.runId(), invocationId);
        String effectiveUserId = auditUserId(safeRequest.userId());
        Instant startedAt = clock.instant();
        Optional<ToolPort> toolPort = toolRegistry.find(safeRequest.toolId());
        ToolInvocationRequest effectiveRequest = safeRequest;
        PolicyDecision referenceResolutionDecision = null;
        if (toolPort.isPresent() && toolPort.get() instanceof ToolResourceReferenceResolverPort resolver) {
            try {
                Map<String, String> resolvedResourceRefs = Objects.requireNonNull(
                        resolver.resolveResourceRefs(safeRequest),
                        "trusted resource reference resolver returned null");
                effectiveRequest = safeRequest.withResourceRefs(resolvedResourceRefs);
            } catch (RuntimeException ex) {
                referenceResolutionDecision = PolicyDecision.deny(
                        "builtin-resource-reference-resolution",
                        ToolPolicyReasonCodes.RESOURCE_FORBIDDEN,
                        "Trusted resource reference resolution failed");
            }
        }
        auditPort.recordRequested(new ToolInvocationAuditRecord(
                invocationId,
                effectiveRunId,
                effectiveRequest.stepId(),
                effectiveRequest.agentId(),
                effectiveRequest.versionId(),
                effectiveRequest.rolloutId(),
                effectiveRequest.tenantId(),
                effectiveUserId,
                effectiveRequest.toolId(),
                effectiveRequest.idempotencyKey(),
                ToolInvocationStatus.REQUESTED,
                auditSummary.summarizeArguments(effectiveRequest),
                startedAt));

        // 策略裁决必须发生在真实工具执行之前；非 ALLOW 结果不得触达 ToolPort。
        PolicyDecision decision = referenceResolutionDecision != null
                ? referenceResolutionDecision
                : Objects.requireNonNullElseGet(
                        toolPolicy.decide(ToolPolicyRequest.from(effectiveRequest, toolPort.isPresent())),
                        () -> PolicyDecision.deny("builtin-policy-null", ToolPolicyReasonCodes.POLICY_DECISION_MISSING,
                                "Tool policy did not return a decision"));
        boolean approvalSatisfied = approvalSatisfied(effectiveRequest, decision);
        ToolInvocationStatus decisionStatus = approvalSatisfied ? ToolInvocationStatus.ALLOWED : decisionStatus(decision);
        auditPort.recordDecision(new ToolInvocationAuditDecision(invocationId, decision.decisionId(), decisionStatus));
        if (!decision.allowsExecution() && !approvalSatisfied) {
            String approvalId = null;
            if (decision.effect() == PolicyDecision.Effect.APPROVAL_REQUIRED) {
                approvalId = createApprovalRequest(
                        effectiveRequest,
                        decision,
                        invocationId,
                        effectiveRunId,
                        effectiveUserId,
                        startedAt);
            }
            ToolInvocationResult result = ToolInvocationResult.failed(decision.reasonCode(), approvalId);
            String auditError = auditErrorMessage(result.error());
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    decisionStatus,
                    auditSummary.summarizeResult(result, auditError),
                    auditError,
                    clock.instant()));
            return result;
        }

        if (effectiveRequest.idempotencyKey() != null) {
            boolean claimed;
            try {
                claimed = idempotencyPort.tryClaim(
                        effectiveRequest.tenantId(), effectiveRequest.idempotencyKey(), startedAt);
            } catch (RuntimeException ex) {
                return completeWithoutExecution(
                        invocationId,
                        "Tool idempotency claim failed",
                        ToolInvocationStatus.FAILED,
                        clock.instant());
            }
            if (!claimed) {
                // 重复键命中意味着上一次 PROCESSING 调用的副作用结果未知：可能已执行、
                // 可能未执行。这是不确定结果，必须记录 UNKNOWN 等待对账，而不是伪造 FAILED。
                return completeWithoutExecution(
                        invocationId,
                        "Duplicate or unresolved tool invocation",
                        ToolInvocationStatus.UNKNOWN,
                        clock.instant());
            }
        }

        try {
            ToolPort executableTool = toolPort.isPresent()
                    ? toolPort.get()
                    : ToolPort.notFound(effectiveRequest.toolId());
            ToolInvocationResult rawResult = executableTool instanceof ToolInvocationRequestAwarePort awareTool
                    ? awareTool.invoke(effectiveRequest)
                    : executableTool.invoke(
                            effectiveRequest.toolCallId(),
                            effectiveRequest.toolId(),
                            effectiveRequest.arguments());
            if (rawResult.success()) {
                publishArtifacts(effectiveRequest, rawResult);
            }
            ToolInvocationResult redactedResult = outputRedactionPort.redact(effectiveRequest, rawResult);
            ToolInvocationResult result = redactedResult.success()
                    ? toolResultSpillPort.spill(effectiveRequest, redactedResult)
                    : redactedResult;
            String auditError = auditErrorMessage(result.error());
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    result.success() ? ToolInvocationStatus.SUCCEEDED : ToolInvocationStatus.FAILED,
                    auditSummary.summarizeResult(result, auditError),
                    auditError,
                    clock.instant()));
            markIdempotencyCompleted(effectiveRequest);
            return result;
        } catch (Exception ex) {
            ToolInvocationResult result = ToolInvocationResult.failed(
                    redactFailureText(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
            String auditError = auditErrorMessage(result.error());
            auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                    invocationId,
                    ToolInvocationStatus.FAILED,
                    auditSummary.summarizeResult(result, auditError),
                    auditError,
                    clock.instant()));
            return result;
        }
    }

    private void markIdempotencyCompleted(ToolInvocationRequest request) {
        if (request.idempotencyKey() == null) {
            return;
        }
        try {
            idempotencyPort.markCompleted(request.tenantId(), request.idempotencyKey(), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Tool idempotency completion failed for tool={}, errorType={}",
                    request.toolId(), ex.getClass().getSimpleName());
        }
    }

    private ToolInvocationResult completeWithoutExecution(
            String invocationId, String error, ToolInvocationStatus status, Instant completedAt) {
        ToolInvocationResult result = ToolInvocationResult.failed(error);
        auditPort.recordCompleted(new ToolInvocationAuditCompletion(
                invocationId,
                status,
                auditSummary.summarizeResult(result, error),
                error,
                completedAt));
        return result;
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
                auditSummary.approvalSummary(request, decision),
                auditSummary.argumentsPreviewJson(request),
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

    private String auditErrorMessage(String errorMessage) {
        if (!hasText(errorMessage)) {
            return errorMessage;
        }
        return truncate(redactFailureText(errorMessage));
    }

    private String redactFailureText(String errorMessage) {
        return CredentialTextRedactor.redact(errorMessage);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH);
    }


    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
