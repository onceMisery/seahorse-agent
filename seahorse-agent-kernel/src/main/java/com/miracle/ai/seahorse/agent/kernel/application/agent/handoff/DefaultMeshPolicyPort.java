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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.MeshPolicyPort;

public class DefaultMeshPolicyPort implements MeshPolicyPort {

    @Override
    public MeshPolicyDecision decide(MeshPolicyRequest request) {
        if (request.depth() > AgentHandoffLimits.MAX_LOCAL_HANDOFF_DEPTH) {
            return MeshPolicyDecision.deny(
                    AgentHandoffFailureCode.DEPTH_LIMIT_EXCEEDED,
                    "Local handoff depth exceeded");
        }
        if (request.ancestorAgentIds().contains(request.targetAgentId())) {
            return MeshPolicyDecision.deny(
                    AgentHandoffFailureCode.CYCLE_DETECTED,
                    "Local handoff cycle detected");
        }
        return MeshPolicyDecision.allow();
    }
}
