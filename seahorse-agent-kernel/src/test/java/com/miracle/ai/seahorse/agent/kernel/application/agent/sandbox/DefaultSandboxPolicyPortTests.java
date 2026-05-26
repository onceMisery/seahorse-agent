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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultSandboxPolicyPortTests {

    @Test
    void shouldDenyNetworkByDefaultWhenAllowlistIsEmpty() {
        DefaultSandboxPolicyPort policy = new DefaultSandboxPolicyPort(SandboxNetworkPolicy.DENY_ALL, List.of());

        SandboxPolicyDecision decision = policy.decide(new SandboxPolicyRequest(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                true,
                List.of("api.example.com")));

        assertEquals(SandboxPolicyEffect.DENY, decision.effect());
        assertEquals(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT, decision.reasonCode());
        assertFalse(decision.allowsExecution());
    }

    @Test
    void shouldAllowNoNetworkRequestUnderDefaultDenyPolicy() {
        DefaultSandboxPolicyPort policy = new DefaultSandboxPolicyPort(SandboxNetworkPolicy.DENY_ALL, List.of());

        SandboxPolicyDecision decision = policy.decide(new SandboxPolicyRequest(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.FILE_CONVERSION,
                false,
                List.of()));

        assertEquals(SandboxPolicyEffect.ALLOW, decision.effect());
        assertEquals(SandboxPolicyReasonCode.VALID_REQUEST, decision.reasonCode());
    }
}
