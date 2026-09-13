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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAgentCollaborationPolicyPortTests {

    private final DefaultAgentCollaborationPolicyPort port = new DefaultAgentCollaborationPolicyPort();

    @Test
    void shouldAllowWhenNoExplicitPolicyExists() {
        AgentCollaborationPolicyDecision decision = port.decide(request(1));

        assertTrue(decision.allowed());
    }

    @Test
    void shouldDenyWhenExplicitPolicyDenies() {
        port.savePolicy(new AgentCollaborationPolicy("p-1", "tenant-1", "agent-a", "agent-b", false, null));

        AgentCollaborationPolicyDecision decision = port.decide(request(1));

        assertEquals(false, decision.allowed());
        assertEquals(AgentHandoffFailureCode.POLICY_DENIED, decision.failureCode());
    }

    @Test
    void shouldAllowWhenExplicitPolicyAllows() {
        port.savePolicy(new AgentCollaborationPolicy("p-1", "tenant-1", "agent-a", "agent-b", true, null));

        assertTrue(port.decide(request(1)).allowed());
    }

    @Test
    void shouldDenyWhenDepthExceedsPolicyMaxDepth() {
        port.savePolicy(new AgentCollaborationPolicy("p-1", "tenant-1", "agent-a", "agent-b", true, 1));

        AgentCollaborationPolicyDecision decision = port.decide(request(2));

        assertEquals(false, decision.allowed());
        assertEquals(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED, decision.failureCode());
    }

    @Test
    void shouldDenyWhenDepthExceedsGlobalLimitEvenWithoutPolicy() {
        AgentCollaborationPolicyDecision decision = port.decide(request(Integer.MAX_VALUE));

        assertEquals(false, decision.allowed());
        assertEquals(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED, decision.failureCode());
    }

    @Test
    void shouldScopePoliciesByTenantAndDelete() {
        port.savePolicy(new AgentCollaborationPolicy("p-1", "tenant-1", "agent-a", "agent-b", false, null));
        // 其他租户的同源/目标组合不受影响
        assertTrue(port.decide(new AgentCollaborationPolicyRequest(
                "tenant-2", "agent-a", "agent-b", 1, List.of(), List.of())).allowed());
        // 删除后回到默认放行
        port.deletePolicy("p-1");
        assertTrue(port.decide(request(1)).allowed());
        assertEquals(0, port.listPolicies("tenant-1").size());
    }

    private AgentCollaborationPolicyRequest request(int depth) {
        return new AgentCollaborationPolicyRequest(
                "tenant-1", "agent-a", "agent-b", depth, List.of(), List.of());
    }
}
