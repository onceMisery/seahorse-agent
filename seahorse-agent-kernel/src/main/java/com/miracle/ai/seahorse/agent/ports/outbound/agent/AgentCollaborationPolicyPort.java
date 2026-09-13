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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyRequest;

import java.util.List;

/**
 * Agent 协作授权端口（设计 §5.3）：source→target 显式授权规则的评估与治理。
 *
 * <p>语义约定：同 (tenantId, sourceAgentId, targetAgentId) 只有一条生效策略；
 * 无显式策略时的放行/拒绝行为由实现决定并在实现文档中声明
 * （内核默认实现为放行，保证既有 agent-as-tool / team 分派兼容；
 * 生产可通过 JDBC 适配器装载显式 allowlist 策略收紧）。
 */
public interface AgentCollaborationPolicyPort {

    AgentCollaborationPolicyDecision decide(AgentCollaborationPolicyRequest request);

    AgentCollaborationPolicy savePolicy(AgentCollaborationPolicy policy);

    List<AgentCollaborationPolicy> listPolicies(String tenantId);

    void deletePolicy(String policyId);
}
