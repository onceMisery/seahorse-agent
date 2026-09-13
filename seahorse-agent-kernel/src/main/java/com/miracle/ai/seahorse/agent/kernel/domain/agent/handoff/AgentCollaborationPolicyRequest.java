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

import java.util.List;

/**
 * 协作授权决策请求（设计 §5.3）。
 *
 * @param tenantId             租户
 * @param sourceAgentId        发起 Agent
 * @param targetAgentId        目标 Agent
 * @param depth                本次 handoff 深度
 * @param ancestorAgentIds     祖先 Agent 链
 * @param requestedCapabilities 请求能力（可空，预留扩展）
 */
public record AgentCollaborationPolicyRequest(String tenantId,
                                              String sourceAgentId,
                                              String targetAgentId,
                                              int depth,
                                              List<String> ancestorAgentIds,
                                              List<String> requestedCapabilities) {

    public AgentCollaborationPolicyRequest {
        tenantId = AgentCollaborationPolicy.requireTextOf(tenantId, "tenantId 不能为空");
        sourceAgentId = AgentCollaborationPolicy.requireTextOf(sourceAgentId, "sourceAgentId 不能为空");
        targetAgentId = AgentCollaborationPolicy.requireTextOf(targetAgentId, "targetAgentId 不能为空");
        ancestorAgentIds = ancestorAgentIds == null ? List.of() : List.copyOf(ancestorAgentIds);
        requestedCapabilities = requestedCapabilities == null
                ? List.of()
                : List.copyOf(requestedCapabilities);
    }
}
