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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalGovernedToolExecutionPortTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void approvalRequiredPreflightCreatesApprovalWithoutInvokingGateway() {
        CountingGateway gateway = new CountingGateway();
        CapturingApprovalRepository approvals = new CapturingApprovalRepository();
        ToolPolicyPort policy = request -> PolicyDecision.approvalRequired(
                "decision-1",
                ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                "approval required");
        LocalGovernedToolExecutionPort port = new LocalGovernedToolExecutionPort(
                new SingleToolRegistry(),
                gateway,
                policy,
                approvals,
                ApprovalRequestQueryPort.empty(),
                new ObjectMapper(),
                FIXED_CLOCK);

        GovernedToolPermission permission = port.preflight(request("weather", Map.of("city", "Hangzhou")));

        assertEquals(GovernedToolPermission.Effect.APPROVAL_REQUIRED, permission.effect());
        assertNotNull(permission.approvalId());
        assertEquals(0, gateway.calls.get());
        ApprovalRequest approval = approvals.request.get();
        assertNotNull(approval);
        assertEquals(permission.approvalId(), approval.approvalId());
        assertEquals("run-1", approval.runId());
        assertEquals("agentscope-step", approval.stepId());
        assertEquals("tenant-a", approval.tenantId());
        assertEquals("user-1", approval.userId());
        assertEquals("agent-1", approval.agentId());
        assertEquals("weather", approval.toolId());
        assertEquals(ApprovalRequestStatus.PENDING, approval.status());
    }

    private static ToolInvocationRequest request(String toolId, Map<String, Object> arguments) {
        return new ToolInvocationRequest(
                "run-1",
                "agentscope-step",
                "call-1",
                "agent-1",
                "version-1",
                "rollout-1",
                "tenant-a",
                "user-1",
                "identity-1",
                toolId,
                arguments,
                Map.of(),
                "run-1:call-1",
                List.of(toolId));
    }

    private static final class SingleToolRegistry implements ToolRegistryPort {
        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            return "weather".equals(toolId) ? Optional.of((toolCallId, id, arguments) -> ToolInvocationResult.ok("ok"))
                    : Optional.empty();
        }
    }

    private static final class CountingGateway implements ToolGatewayPort {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            calls.incrementAndGet();
            return ToolInvocationResult.ok("ok");
        }
    }

    private static final class CapturingApprovalRepository implements ToolApprovalRequestRepositoryPort {
        private final AtomicReference<ApprovalRequest> request = new AtomicReference<>();

        @Override
        public void save(ApprovalRequest request) {
            this.request.set(request);
        }
    }
}
