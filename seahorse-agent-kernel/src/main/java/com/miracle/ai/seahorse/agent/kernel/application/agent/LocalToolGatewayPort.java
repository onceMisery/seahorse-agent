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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class LocalToolGatewayPort implements ToolGatewayPort {

    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final String LEGACY_RUN_ID_PREFIX = "legacy-run:";
    private static final String LEGACY_USER_ID = "legacy-user";

    private final ToolRegistryPort toolRegistry;
    private final ToolPolicyPort toolPolicy;
    private final ToolInvocationAuditPort auditPort;
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
        this.toolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolPolicy = Objects.requireNonNullElseGet(toolPolicy, ToolPolicyPort::defaults);
        this.auditPort = Objects.requireNonNullElseGet(auditPort, ToolInvocationAuditPort::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String invocationId = nextInvocationId();
        Instant startedAt = clock.instant();
        auditPort.recordRequested(new ToolInvocationAuditRecord(
                invocationId,
                auditRunId(safeRequest.runId(), invocationId),
                safeRequest.stepId(),
                safeRequest.agentId(),
                safeRequest.versionId(),
                safeRequest.tenantId(),
                auditUserId(safeRequest.userId()),
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
        ToolInvocationStatus decisionStatus = decisionStatus(decision);
        auditPort.recordDecision(new ToolInvocationAuditDecision(invocationId, decision.decisionId(), decisionStatus));
        if (!decision.allowsExecution()) {
            ToolInvocationResult result = ToolInvocationResult.failed(decision.reasonCode());
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
            ToolInvocationResult result = executableTool.invoke(
                    safeRequest.toolCallId(), safeRequest.toolId(), safeRequest.arguments());
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

    private String nextInvocationId() {
        return UUID.randomUUID().toString();
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

    private String summarizeArguments(ToolInvocationRequest request) {
        return truncate("keys=" + request.arguments().keySet() + ", size=" + request.arguments().size());
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
