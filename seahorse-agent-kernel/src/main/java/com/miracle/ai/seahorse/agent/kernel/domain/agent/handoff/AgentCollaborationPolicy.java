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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff;

import java.util.Objects;

/**
 * Agent 协作授权策略：一条 source→target 的显式授权规则（设计 §5.3）。
 *
 * @param policyId     策略 ID
 * @param tenantId     租户
 * @param sourceAgentId 发起 Agent
 * @param targetAgentId 目标 Agent
 * @param allowed      是否允许 source 调用 target
 * @param maxDepth     该授权允许的最大 handoff 深度（空表示沿用全局上限）
 */
public record AgentCollaborationPolicy(String policyId,
                                       String tenantId,
                                       String sourceAgentId,
                                       String targetAgentId,
                                       boolean allowed,
                                       Integer maxDepth) {

    public AgentCollaborationPolicy {
        policyId = requireText(policyId, "policyId 不能为空");
        tenantId = requireText(tenantId, "tenantId 不能为空");
        sourceAgentId = requireText(sourceAgentId, "sourceAgentId 不能为空");
        targetAgentId = requireText(targetAgentId, "targetAgentId 不能为空");
        if (maxDepth != null && maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth 必须为正数");
        }
    }

    public boolean matches(String candidateTenantId, String candidateSource, String candidateTarget) {
        return tenantId.equals(candidateTenantId)
                && sourceAgentId.equals(candidateSource)
                && targetAgentId.equals(candidateTarget);
    }

    static String requireTextOf(String value, String message) {
        return requireText(value, message);
    }

    static String normalizeOf(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentCollaborationPolicy that)) {
            return false;
        }
        return policyId.equals(that.policyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyId);
    }
}
