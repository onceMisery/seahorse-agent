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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessRequest;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class CatalogBackedToolPolicyPort implements ToolPolicyPort {

    private static final Set<ToolActionType> APPROVAL_ACTION_TYPES = EnumSet.of(
            ToolActionType.DELETE,
            ToolActionType.EXTERNAL_SEND);

    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final AgentToolBindingRepositoryPort bindingRepository;
    private final ToolInvocationUsagePort invocationUsagePort;
    private final Predicate<ToolPolicyRequest> toolRegisteredPredicate;
    private final ToolResourceAccessPort resourceAccessPort;
    private final QuotaManagementInboundPort quotaManagementPort;

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository) {
        this(toolCatalogRepository, bindingRepository, ToolInvocationUsagePort.empty(),
                ToolPolicyRequest::toolRegistered);
    }

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository,
                                       Predicate<ToolPolicyRequest> toolRegisteredPredicate) {
        this(toolCatalogRepository, bindingRepository, ToolInvocationUsagePort.empty(), toolRegisteredPredicate);
    }

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository,
                                       ToolInvocationUsagePort invocationUsagePort) {
        this(toolCatalogRepository, bindingRepository, invocationUsagePort, ToolPolicyRequest::toolRegistered);
    }

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository,
                                       ToolInvocationUsagePort invocationUsagePort,
                                       Predicate<ToolPolicyRequest> toolRegisteredPredicate) {
        this(toolCatalogRepository, bindingRepository, invocationUsagePort, toolRegisteredPredicate,
                ToolResourceAccessPort.allowAll());
    }

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository,
                                       ToolInvocationUsagePort invocationUsagePort,
                                       Predicate<ToolPolicyRequest> toolRegisteredPredicate,
                                       ToolResourceAccessPort resourceAccessPort) {
        this(toolCatalogRepository, bindingRepository, invocationUsagePort, toolRegisteredPredicate,
                resourceAccessPort, null);
    }

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository,
                                       ToolInvocationUsagePort invocationUsagePort,
                                       Predicate<ToolPolicyRequest> toolRegisteredPredicate,
                                       ToolResourceAccessPort resourceAccessPort,
                                       QuotaManagementInboundPort quotaManagementPort) {
        this.toolCatalogRepository = Objects.requireNonNullElseGet(
                toolCatalogRepository,
                ToolCatalogRepositoryPort::empty);
        this.bindingRepository = Objects.requireNonNullElseGet(
                bindingRepository,
                AgentToolBindingRepositoryPort::empty);
        this.invocationUsagePort = Objects.requireNonNullElseGet(
                invocationUsagePort,
                ToolInvocationUsagePort::empty);
        this.toolRegisteredPredicate = Objects.requireNonNullElseGet(
                toolRegisteredPredicate,
                () -> ToolPolicyRequest::toolRegistered);
        this.resourceAccessPort = Objects.requireNonNullElseGet(
                resourceAccessPort,
                ToolResourceAccessPort::allowAll);
        this.quotaManagementPort = quotaManagementPort;
    }

    @Override
    public PolicyDecision decide(ToolPolicyRequest request) {
        if (request == null || !toolRegisteredPredicate.test(request)) {
            return deny(ToolPolicyReasonCodes.TOOL_NOT_FOUND, "Tool is not registered");
        }
        if (request.allowedToolIds().isEmpty() || !request.allowedToolIds().contains(request.toolId())) {
            return deny(ToolPolicyReasonCodes.TOOL_NOT_BOUND,
                    "Tool is not bound to the current agent version");
        }

        return toolCatalogRepository.findById(request.toolId())
                .map(tool -> decideWithCatalog(request, tool))
                .orElseGet(() -> deny(ToolPolicyReasonCodes.TOOL_NOT_FOUND, "Tool is not in catalog"));
    }

    private PolicyDecision decideWithCatalog(ToolPolicyRequest request, ToolCatalogEntry tool) {
        if (!tool.enabled()) {
            return deny(ToolPolicyReasonCodes.TOOL_DISABLED, "Tool is disabled");
        }
        // Legacy agent mode uses allowedToolIds list instead of explicit bindings
        boolean isLegacyAgent = request.agentId() == null
                || "legacy-react-agent".equals(request.agentId());
        if (!isLegacyAgent) {
            AgentToolBinding binding = bindingRepository
                    .findBinding(request.agentId(), request.versionId(), request.toolId())
                    .orElse(null);
            if (binding == null) {
                return deny(ToolPolicyReasonCodes.TOOL_NOT_BOUND,
                        "Tool is not bound to the current agent version");
            }
            if (exceedsCallLimit(request, binding)) {
                return deny(ToolPolicyReasonCodes.TOOL_CALL_LIMIT_EXCEEDED,
                        "Tool call limit exceeded for this run");
            }
            PolicyDecision argumentDecision = validateArguments(request, binding);
            if (argumentDecision != null) {
                return argumentDecision;
            }
        }
        PolicyDecision resourceDecision = validateResourceAccess(request, tool);
        if (resourceDecision != null) {
            return resourceDecision;
        }
        PolicyDecision quotaDecision = validateQuota(request, tool);
        if (quotaDecision != null) {
            return quotaDecision;
        }
        if (requiresApproval(tool)) {
            return PolicyDecision.approvalRequired(
                    "builtin-tool-approval-required",
                    ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                    "Tool requires approval");
        }
        return PolicyDecision.allow("builtin-tool-allow");
    }

    private PolicyDecision validateArguments(ToolPolicyRequest request, AgentToolBinding binding) {
        try {
            return ToolArgumentPolicy.parse(binding.argumentPolicyJson())
                    .validate(request.arguments())
                    .map(violation -> deny(violation.reasonCode(), violation.reasonMessage()))
                    .orElse(null);
        } catch (IllegalArgumentException ex) {
            return deny(ToolPolicyReasonCodes.TOOL_ARGUMENT_POLICY_INVALID,
                    "Tool argument policy is invalid");
        }
    }

    private PolicyDecision validateResourceAccess(ToolPolicyRequest request, ToolCatalogEntry tool) {
        if (request.resourceRefs().isEmpty()) {
            return null;
        }
        ToolResourceAccessDecision accessDecision = resourceAccessPort.decide(new ToolResourceAccessRequest(
                request.runId(),
                request.agentId(),
                request.versionId(),
                request.tenantId(),
                request.userId(),
                request.agentIdentityId(),
                request.toolId(),
                tool.resourceType(),
                request.resourceRefs()));
        if (accessDecision != null && accessDecision.allowed()) {
            return null;
        }
        String reason = accessDecision == null ? "Resource access decision missing" : accessDecision.reason();
        return deny(ToolPolicyReasonCodes.RESOURCE_FORBIDDEN, reason);
    }

    private PolicyDecision validateQuota(ToolPolicyRequest request, ToolCatalogEntry tool) {
        if (quotaManagementPort == null) {
            return null;
        }
        QuotaDecisionResult quotaDecision = quotaManagementPort.evaluate(new QuotaDecisionCommand(
                request.tenantId(),
                request.agentId(),
                request.userId(),
                request.toolId(),
                null,
                request.runId(),
                riskLevel(tool.riskLevel()),
                new QuotaUsage(0L, 1L, 0d)));
        if (quotaDecision == null) {
            return deny(ToolPolicyReasonCodes.QUOTA_DECISION_MISSING,
                    "Quota policy did not return a decision");
        }
        if (isNoPolicyDecision(quotaDecision)) {
            return null;
        }
        return switch (quotaDecision.effect()) {
            case DENY -> quotaPolicyDecision(
                    PolicyDecision.Effect.DENY,
                    ToolPolicyReasonCodes.QUOTA_HARD_LIMIT_EXCEEDED,
                    "Quota hard limit exceeded",
                    quotaDecision);
            case REQUIRE_APPROVAL -> quotaPolicyDecision(
                    PolicyDecision.Effect.APPROVAL_REQUIRED,
                    ToolPolicyReasonCodes.QUOTA_APPROVAL_REQUIRED,
                    "Quota policy requires approval",
                    quotaDecision);
            case WARN, ALLOW -> null;
        };
    }

    private boolean isNoPolicyDecision(QuotaDecisionResult quotaDecision) {
        return quotaDecision.reasonCode() == QuotaDecisionReasonCode.NO_POLICY_LOW_RISK
                || quotaDecision.reasonCode() == QuotaDecisionReasonCode.NO_POLICY_HIGH_RISK;
    }

    private PolicyDecision quotaPolicyDecision(PolicyDecision.Effect effect,
                                               String reasonCode,
                                               String reasonMessage,
                                               QuotaDecisionResult quotaDecision) {
        String decisionId = "quota-" + quotaDecision.reasonCode().name().toLowerCase().replace('_', '-');
        String policyId = quotaDecision.policyId() == null ? "builtin-quota-policy" : quotaDecision.policyId();
        String approvalPolicyId = effect == PolicyDecision.Effect.APPROVAL_REQUIRED
                ? "quota-approval"
                : null;
        return new PolicyDecision(
                decisionId,
                effect,
                reasonCode,
                reasonMessage,
                policyId,
                "1",
                approvalPolicyId,
                null);
    }

    private boolean exceedsCallLimit(ToolPolicyRequest request, AgentToolBinding binding) {
        if (request.runId() == null) {
            return false;
        }
        long requestedCalls = invocationUsagePort.countRequestedCalls(
                request.runId(), request.agentId(), request.versionId(), request.toolId());
        return requestedCalls > binding.maxCallsPerRun();
    }

    private boolean requiresApproval(ToolCatalogEntry tool) {
        return tool.requiresApproval()
                || tool.riskLevel() == ToolRiskLevel.CRITICAL
                || APPROVAL_ACTION_TYPES.contains(tool.actionType());
    }

    private AgentRiskLevel riskLevel(ToolRiskLevel riskLevel) {
        return switch (Objects.requireNonNullElse(riskLevel, ToolRiskLevel.LOW)) {
            case LOW -> AgentRiskLevel.LOW;
            case MEDIUM -> AgentRiskLevel.MEDIUM;
            case HIGH -> AgentRiskLevel.HIGH;
            case CRITICAL -> AgentRiskLevel.CRITICAL;
        };
    }

    private PolicyDecision deny(String reasonCode, String reasonMessage) {
        return PolicyDecision.deny("builtin-" + reasonCode.toLowerCase().replace('_', '-'),
                reasonCode,
                reasonMessage);
    }
}
