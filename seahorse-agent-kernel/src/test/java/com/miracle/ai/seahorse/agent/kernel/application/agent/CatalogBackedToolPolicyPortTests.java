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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogBackedToolPolicyPortTests {

    @Test
    void shouldDenyDisabledToolBeforeBindingCheck() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("memory-read", ToolRiskLevel.LOW, ToolActionType.READ, false, false),
                binding("memory-read", 3));

        PolicyDecision decision = policy.decide(request("memory-read"));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals("TOOL_DISABLED", decision.reasonCode());
    }

    @Test
    void shouldDenyToolThatIsNotBoundToAgentVersion() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("memory-read", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                null);

        PolicyDecision decision = policy.decide(request("memory-read"));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals("TOOL_NOT_BOUND", decision.reasonCode());
    }

    @Test
    void shouldDenyToolWhenRuntimeAllowlistIsEmptyEvenIfBindingExists() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                binding("knowledge-search", 3));

        PolicyDecision decision = policy.decide(request("knowledge-search", Map.of("input", "value"), Map.of(),
                List.of()));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals("TOOL_NOT_BOUND", decision.reasonCode());
    }

    @Test
    void shouldAllowLowRiskReadToolWhenCatalogAndBindingAllowIt() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                binding("knowledge-search", 3));

        PolicyDecision decision = policy.decide(request("knowledge-search"));

        assertEquals(PolicyDecision.Effect.ALLOW, decision.effect());
        assertEquals(ToolPolicyReasonCodes.ALLOW, decision.reasonCode());
    }

    @Test
    void shouldRequireApprovalForCriticalDeleteOrExternalSendTool() {
        assertApprovalRequired(tool("critical-export", ToolRiskLevel.CRITICAL, ToolActionType.READ, true, false));
        assertApprovalRequired(tool("memory-forget", ToolRiskLevel.HIGH, ToolActionType.DELETE, true, false));
        assertApprovalRequired(tool("send-email", ToolRiskLevel.MEDIUM, ToolActionType.EXTERNAL_SEND, true, false));
    }

    @Test
    void shouldRequireApprovalWhenCatalogMarksToolAsApprovalRequired() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("memory-write", ToolRiskLevel.MEDIUM, ToolActionType.WRITE, true, true),
                binding("memory-write", 3));

        PolicyDecision decision = policy.decide(request("memory-write"));

        assertEquals(PolicyDecision.Effect.APPROVAL_REQUIRED, decision.effect());
        assertEquals("TOOL_APPROVAL_REQUIRED", decision.reasonCode());
    }

    @Test
    void shouldDenyWhenRunExceedsBindingCallLimit() {
        CatalogBackedToolPolicyPort policy = new CatalogBackedToolPolicyPort(
                new SingleToolCatalogRepository(tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ,
                        true, false)),
                new SingleAgentToolBindingRepository(binding("knowledge-search", 2)),
                new FixedToolInvocationUsagePort(3L),
                ToolPolicyRequest::toolRegistered);

        PolicyDecision decision = policy.decide(request("knowledge-search"));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals("TOOL_CALL_LIMIT_EXCEEDED", decision.reasonCode());
    }

    @Test
    void shouldAllowWhenRunIsAtBindingCallLimitIncludingCurrentRequest() {
        CatalogBackedToolPolicyPort policy = new CatalogBackedToolPolicyPort(
                new SingleToolCatalogRepository(tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ,
                        true, false)),
                new SingleAgentToolBindingRepository(binding("knowledge-search", 2)),
                new FixedToolInvocationUsagePort(2L),
                ToolPolicyRequest::toolRegistered);

        PolicyDecision decision = policy.decide(request("knowledge-search"));

        assertEquals(PolicyDecision.Effect.ALLOW, decision.effect());
        assertEquals("ALLOW", decision.reasonCode());
    }

    @Test
    void shouldDenyWhenRequiredArgumentIsMissing() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                binding("knowledge-search", 3, "{\"required\":[\"query\"]}"));

        PolicyDecision decision = policy.decide(request("knowledge-search", Map.of("input", "value")));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals(ToolPolicyReasonCodes.TOOL_ARGUMENT_REQUIRED_MISSING, decision.reasonCode());
    }

    @Test
    void shouldDenyWhenArgumentIsNotAllowedByBindingPolicy() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                binding("knowledge-search", 3, "{\"allowed\":[\"query\"]}"));

        PolicyDecision decision = policy.decide(request("knowledge-search",
                Map.of("query", "memory", "unsafe", true)));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals(ToolPolicyReasonCodes.TOOL_ARGUMENT_NOT_ALLOWED, decision.reasonCode());
    }

    @Test
    void shouldAllowWhenArgumentsSatisfyBindingPolicy() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                binding("knowledge-search", 3, "{\"required\":[\"query\"],\"allowed\":[\"query\",\"topK\"]}"));

        PolicyDecision decision = policy.decide(request("knowledge-search",
                Map.of("query", "memory", "topK", 5)));

        assertEquals(PolicyDecision.Effect.ALLOW, decision.effect());
        assertEquals("ALLOW", decision.reasonCode());
    }

    @Test
    void shouldDenyWhenResourceAccessPolicyRejectsReferencedResource() {
        CatalogBackedToolPolicyPort policy = new CatalogBackedToolPolicyPort(
                new SingleToolCatalogRepository(tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ,
                        true, false)),
                new SingleAgentToolBindingRepository(binding("knowledge-search", 3)),
                ToolInvocationUsagePort.empty(),
                request -> true,
                request -> ToolResourceAccessDecision.deny("kb-1 forbidden"));

        PolicyDecision decision = policy.decide(request(
                "knowledge-search",
                Map.of("input", "value"),
                Map.of("knowledgeBaseId", "kb-1")));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals(ToolPolicyReasonCodes.RESOURCE_FORBIDDEN, decision.reasonCode());
    }

    @Test
    void shouldDenyWhenArgumentPolicyJsonIsInvalid() {
        CatalogBackedToolPolicyPort policy = policy(
                tool("knowledge-search", ToolRiskLevel.LOW, ToolActionType.READ, true, false),
                binding("knowledge-search", 3, "[\"query\"]"));

        PolicyDecision decision = policy.decide(request("knowledge-search",
                Map.of("query", "memory")));

        assertEquals(PolicyDecision.Effect.DENY, decision.effect());
        assertEquals(ToolPolicyReasonCodes.TOOL_ARGUMENT_POLICY_INVALID, decision.reasonCode());
    }

    private static void assertApprovalRequired(ToolCatalogEntry tool) {
        CatalogBackedToolPolicyPort policy = policy(tool, binding(tool.toolId(), 3));

        PolicyDecision decision = policy.decide(request(tool.toolId()));

        assertEquals(PolicyDecision.Effect.APPROVAL_REQUIRED, decision.effect());
        assertEquals("TOOL_APPROVAL_REQUIRED", decision.reasonCode());
    }

    private static CatalogBackedToolPolicyPort policy(ToolCatalogEntry tool, AgentToolBinding binding) {
        return new CatalogBackedToolPolicyPort(
                new SingleToolCatalogRepository(tool),
                new SingleAgentToolBindingRepository(binding),
                ToolInvocationUsagePort.empty(),
                ToolPolicyRequest::toolRegistered);
    }

    private static ToolPolicyRequest request(String toolId) {
        return request(toolId, Map.of("input", "value"));
    }

    private static ToolPolicyRequest request(String toolId, Map<String, Object> arguments) {
        return request(toolId, arguments, Map.of());
    }

    private static ToolPolicyRequest request(String toolId,
                                             Map<String, Object> arguments,
                                             Map<String, String> resourceRefs) {
        return request(toolId, arguments, resourceRefs, List.of(toolId));
    }

    private static ToolPolicyRequest request(String toolId,
                                             Map<String, Object> arguments,
                                             Map<String, String> resourceRefs,
                                             List<String> allowedToolIds) {
        return new ToolPolicyRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "agent-1-v1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                toolId,
                arguments,
                resourceRefs,
                "run-1:call-1",
                allowedToolIds,
                true);
    }

    private static ToolCatalogEntry tool(String toolId,
                                         ToolRiskLevel riskLevel,
                                         ToolActionType actionType,
                                         boolean enabled,
                                         boolean requiresApproval) {
        return new ToolCatalogEntry(
                toolId,
                ToolProvider.BUILTIN,
                toolId,
                toolId + " description",
                "{}",
                null,
                riskLevel,
                actionType,
                "MEMORY",
                "platform",
                enabled,
                requiresApproval,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static AgentToolBinding binding(String toolId, int maxCallsPerRun) {
        return binding(toolId, maxCallsPerRun, "{}");
    }

    private static AgentToolBinding binding(String toolId, int maxCallsPerRun, String argumentPolicyJson) {
        return new AgentToolBinding(
                "binding-" + toolId,
                "agent-1",
                "agent-1-v1",
                toolId,
                maxCallsPerRun,
                argumentPolicyJson,
                "admin-1",
                Instant.EPOCH);
    }

    private static final class SingleToolCatalogRepository implements ToolCatalogRepositoryPort {
        private final ToolCatalogEntry tool;

        private SingleToolCatalogRepository(ToolCatalogEntry tool) {
            this.tool = tool;
        }

        @Override
        public void save(ToolCatalogEntry entry) {
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return tool == null || !tool.toolId().equals(toolId) ? Optional.empty() : Optional.of(tool);
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
        }
    }

    private static final class SingleAgentToolBindingRepository implements AgentToolBindingRepositoryPort {
        private final AgentToolBinding binding;

        private SingleAgentToolBindingRepository(AgentToolBinding binding) {
            this.binding = binding;
        }

        @Override
        public void saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings) {
        }

        @Override
        public List<AgentToolBinding> listBindings(String agentId, String versionId) {
            return binding == null
                    || !binding.agentId().equals(agentId)
                    || !binding.versionId().equals(versionId) ? List.of() : List.of(binding);
        }

        @Override
        public Optional<AgentToolBinding> findBinding(String agentId, String versionId, String toolId) {
            if (binding == null
                    || !binding.agentId().equals(agentId)
                    || !binding.versionId().equals(versionId)
                    || !binding.toolId().equals(toolId)) {
                return Optional.empty();
            }
            return Optional.of(binding);
        }
    }

    private static final class FixedToolInvocationUsagePort implements ToolInvocationUsagePort {
        private final long callCount;

        private FixedToolInvocationUsagePort(long callCount) {
            this.callCount = callCount;
        }

        @Override
        public long countRequestedCalls(String runId, String agentId, String versionId, String toolId) {
            return callCount;
        }
    }
}
