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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 基于工具目录与 Agent 版本绑定的内置策略实现。
 */
public class CatalogBackedToolPolicyPort implements ToolPolicyPort {

    private static final Set<ToolActionType> APPROVAL_ACTION_TYPES = EnumSet.of(
            ToolActionType.DELETE,
            ToolActionType.EXTERNAL_SEND);

    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final AgentToolBindingRepositoryPort bindingRepository;
    private final Predicate<ToolPolicyRequest> toolRegisteredPredicate;

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository) {
        this(toolCatalogRepository, bindingRepository, ToolPolicyRequest::toolRegistered);
    }

    public CatalogBackedToolPolicyPort(ToolCatalogRepositoryPort toolCatalogRepository,
                                       AgentToolBindingRepositoryPort bindingRepository,
                                       Predicate<ToolPolicyRequest> toolRegisteredPredicate) {
        this.toolCatalogRepository = Objects.requireNonNullElseGet(
                toolCatalogRepository,
                ToolCatalogRepositoryPort::empty);
        this.bindingRepository = Objects.requireNonNullElseGet(
                bindingRepository,
                AgentToolBindingRepositoryPort::empty);
        this.toolRegisteredPredicate = Objects.requireNonNullElseGet(
                toolRegisteredPredicate,
                () -> ToolPolicyRequest::toolRegistered);
    }

    @Override
    public PolicyDecision decide(ToolPolicyRequest request) {
        if (request == null || !toolRegisteredPredicate.test(request)) {
            return deny(ToolPolicyReasonCodes.TOOL_NOT_FOUND, "Tool is not registered");
        }

        return toolCatalogRepository.findById(request.toolId())
                .map(tool -> decideWithCatalog(request, tool))
                .orElseGet(() -> deny(ToolPolicyReasonCodes.TOOL_NOT_FOUND, "Tool is not in catalog"));
    }

    private PolicyDecision decideWithCatalog(ToolPolicyRequest request, ToolCatalogEntry tool) {
        if (!tool.enabled()) {
            return deny(ToolPolicyReasonCodes.TOOL_DISABLED, "Tool is disabled");
        }
        if (bindingRepository.findBinding(request.agentId(), request.versionId(), request.toolId()).isEmpty()) {
            return deny(ToolPolicyReasonCodes.TOOL_NOT_BOUND, "Tool is not bound to the current agent version");
        }
        // 高风险、删除、对外发送或显式审批工具先中断执行，后续由 HITL 阶段接管审批流。
        if (requiresApproval(tool)) {
            return PolicyDecision.approvalRequired(
                    "builtin-tool-approval-required",
                    ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                    "Tool requires approval");
        }
        return PolicyDecision.allow("builtin-tool-allow");
    }

    private boolean requiresApproval(ToolCatalogEntry tool) {
        return tool.requiresApproval()
                || tool.riskLevel() == ToolRiskLevel.CRITICAL
                || APPROVAL_ACTION_TYPES.contains(tool.actionType());
    }

    private PolicyDecision deny(String reasonCode, String reasonMessage) {
        return PolicyDecision.deny("builtin-" + reasonCode.toLowerCase().replace('_', '-'),
                reasonCode,
                reasonMessage);
    }
}
