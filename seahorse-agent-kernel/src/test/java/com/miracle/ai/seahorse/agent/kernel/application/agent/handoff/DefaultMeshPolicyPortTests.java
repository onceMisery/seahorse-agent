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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.MeshPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.MeshPolicyRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMeshPolicyPortTests {

    @Test
    void shouldDenyDepthOverflowAndCycles() {
        DefaultMeshPolicyPort policy = new DefaultMeshPolicyPort();

        MeshPolicyDecision depth = policy.decide(request(
                AgentHandoffLimits.MAX_LOCAL_HANDOFF_DEPTH + 1,
                List.of("source-agent", "previous-agent"),
                "target-agent"));
        MeshPolicyDecision cycle = policy.decide(request(
                AgentHandoffLimits.MAX_LOCAL_HANDOFF_DEPTH,
                List.of("source-agent", "target-agent"),
                "target-agent"));
        MeshPolicyDecision allowed = policy.decide(request(
                AgentHandoffLimits.MAX_LOCAL_HANDOFF_DEPTH,
                List.of("source-agent"),
                "target-agent"));

        assertFalse(depth.allowed());
        assertEquals(AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED, depth.failureCode());
        assertFalse(cycle.allowed());
        assertEquals(AgentHandoffFailureCode.CYCLE_DETECTED, cycle.failureCode());
        assertTrue(allowed.allowed());
    }

    private static MeshPolicyRequest request(int depth, List<String> ancestorAgentIds, String targetAgentId) {
        return new MeshPolicyRequest(
                "tenant-1",
                "parent-run-1",
                "source-agent",
                targetAgentId,
                depth,
                ancestorAgentIds);
    }
}
