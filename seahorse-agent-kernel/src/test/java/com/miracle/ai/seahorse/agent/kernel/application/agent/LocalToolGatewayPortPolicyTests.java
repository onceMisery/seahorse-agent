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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalToolGatewayPortPolicyTests {

    @Test
    void shouldExecuteToolOnlyAfterAllowDecision() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("ok"));
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new RecordingToolPolicyPort(PolicyDecision.allow("allow-1")));

        ToolInvocationResult result = gateway.invoke(request("weather", List.of("weather")));

        assertEquals("ok", result.content());
        assertEquals(1, tool.calls.get());
    }

    @Test
    void shouldDenyWithoutExecutingTool() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("should-not-run"));
        RecordingToolPolicyPort policy = new RecordingToolPolicyPort(
                PolicyDecision.deny("deny-1", "TOOL_NOT_BOUND", "Tool is not bound"));
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(new SingleToolRegistry(tool), policy);

        ToolInvocationResult result = gateway.invoke(request("delete-memory", List.of("weather")));

        assertFalse(result.success());
        assertEquals("TOOL_NOT_BOUND", result.error());
        assertEquals(0, tool.calls.get());
        assertEquals("delete-memory", policy.requests.get(0).toolId());
        assertEquals(List.of("weather"), policy.requests.get(0).allowedToolIds());
    }

    @Test
    void defaultPolicyShouldDenyWhenAllowlistIsEmpty() {
        PolicyDecision decision = ToolPolicyPort.defaults()
                .decide(ToolPolicyRequest.from(request("weather", List.of()), true));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals("TOOL_NOT_BOUND", decision.reasonCode());
    }

    @Test
    void shouldRequireApprovalWithoutExecutingTool() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("should-not-run"));
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new RecordingToolPolicyPort(
                        PolicyDecision.approvalRequired("approval-1", "TOOL_APPROVAL_REQUIRED",
                                "Approval required")));

        ToolInvocationResult result = gateway.invoke(request("send-email", List.of("send-email")));

        assertFalse(result.success());
        assertEquals("TOOL_APPROVAL_REQUIRED", result.error());
        assertEquals(0, tool.calls.get());
    }

    private static ToolInvocationRequest request(String toolId, List<String> allowedToolIds) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                toolId,
                Map.of("input", "value"),
                Map.of(),
                "run-1:call-1",
                allowedToolIds);
    }

    private static final class RecordingToolPolicyPort implements ToolPolicyPort {
        private final PolicyDecision decision;
        private final List<ToolPolicyRequest> requests = new ArrayList<>();

        private RecordingToolPolicyPort(PolicyDecision decision) {
            this.decision = decision;
        }

        @Override
        public PolicyDecision decide(ToolPolicyRequest request) {
            requests.add(request);
            return decision;
        }
    }

    private static final class SingleToolRegistry implements ToolRegistryPort {
        private final ToolPort tool;

        private SingleToolRegistry(ToolPort tool) {
            this.tool = tool;
        }

        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            return Optional.of(tool);
        }
    }

    private static final class CountingToolPort implements ToolPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        private final ToolInvocationResult result;

        private CountingToolPort(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
            calls.incrementAndGet();
            this.arguments.set(arguments);
            return result;
        }
    }
}
