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

package com.miracle.ai.seahorse.agent.kernel.application.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffLimits;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCollaborationPolicyPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 协作授权端口内核默认实现：内存策略表。
 *
 * <p>决策语义（声明于端口契约）：
 * 1) 无显式策略 → 放行（兼容既有 agent-as-tool / team 分派）；
 * 2) 显式策略存在 → 按策略 allowed 值裁决；
 * 3) 命中 allow 策略且 depth 超过策略 maxDepth → 拒绝；
 * 4) depth 超过全局 {@link AgentHandoffLimits#MAX_LOCAL_HANDOFF_DEPTH} → 拒绝
 * （全局深度/环校验仍由 MeshPolicyPort 负责，此处只做协作授权维度）。
 */
public class DefaultAgentCollaborationPolicyPort implements AgentCollaborationPolicyPort {

    private final Map<String, AgentCollaborationPolicy> policies = new ConcurrentHashMap<>();

    @Override
    public AgentCollaborationPolicyDecision decide(AgentCollaborationPolicyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.depth() > AgentHandoffLimits.MAX_LOCAL_HANDOFF_DEPTH) {
            return AgentCollaborationPolicyDecision.deny(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED,
                    "协作深度超过全局上限");
        }
        AgentCollaborationPolicy policy = policies.values().stream()
                .filter(candidate -> candidate.matches(request.tenantId(), request.sourceAgentId(),
                        request.targetAgentId()))
                .findFirst()
                .orElse(null);
        if (policy == null) {
            return AgentCollaborationPolicyDecision.allow(null);
        }
        if (!policy.allowed()) {
            return AgentCollaborationPolicyDecision.deny(AgentHandoffFailureCode.POLICY_DENIED,
                    "协作策略拒绝 " + request.sourceAgentId() + " -> " + request.targetAgentId());
        }
        if (policy.maxDepth() != null && request.depth() > policy.maxDepth()) {
            return AgentCollaborationPolicyDecision.deny(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED,
                    "协作策略深度上限 " + policy.maxDepth());
        }
        return AgentCollaborationPolicyDecision.allow("显式协作策略允许");
    }

    @Override
    public AgentCollaborationPolicy savePolicy(AgentCollaborationPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        policies.put(policy.policyId(), policy);
        return policy;
    }

    @Override
    public List<AgentCollaborationPolicy> listPolicies(String tenantId) {
        return policies.values().stream()
                .filter(policy -> policy.tenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    @Override
    public void deletePolicy(String policyId) {
        policies.remove(Objects.requireNonNull(policyId, "policyId must not be null").trim());
    }
}
