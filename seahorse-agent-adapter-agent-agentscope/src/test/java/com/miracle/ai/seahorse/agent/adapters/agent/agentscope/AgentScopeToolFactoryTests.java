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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AgentScopeToolFactoryTests {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void registersAllowedSeahorseToolsAndRoutesCallsThroughGateway() {
        CapturingGateway gateway = new CapturingGateway();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), gateway);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .allowedToolIds(List.of("weather"))
                .runId("run-1")
                .agentId("agent-1")
                .versionId("version-1")
                .rolloutId("rollout-1")
                .tenantId("tenant-a")
                .userId("user-1")
                .agentIdentityId("identity-1")
                .build();

        Toolkit toolkit = factory.toolkitFor(request);
        assertEquals(List.of("weather"), toolkit.getToolNames().stream().toList());

        var result = toolkit.callTool(ToolCallParam.builder()
                        .toolUseBlock(ToolUseBlock.builder()
                                .id("call-1")
                                .name("weather")
                                .input(Map.of("city", "Hangzhou"))
                                .content("{\"city\":\"Hangzhou\"}")
                                .build())
                        .input(Map.of("city", "Hangzhou"))
                        .build())
                .block();

        assertNotNull(result);
        assertEquals(ToolResultState.SUCCESS, result.getState());
        assertEquals("weather=cloudy", result.getOutput().get(0).toString());
        ToolInvocationRequest captured = gateway.request.get();
        assertEquals("run-1", captured.runId());
        assertEquals("agent-1", captured.agentId());
        assertEquals("version-1", captured.versionId());
        assertEquals("rollout-1", captured.rolloutId());
        assertEquals("tenant-a", captured.tenantId());
        assertEquals("user-1", captured.userId());
        assertEquals("identity-1", captured.agentIdentityId());
        assertEquals("weather", captured.toolId());
        assertEquals("Hangzhou", captured.arguments().get("city"));
        assertEquals(List.of("weather"), captured.allowedToolIds());
    }

    @Test
    void emptyAllowedToolIdsExposeAllRegisteredToolsAndPassConcreteToolAllowlistToGateway() {
        CapturingGateway gateway = new CapturingGateway();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), gateway);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .allowedToolIds(List.of())
                .runId("run-1")
                .agentId("legacy-react-agent")
                .tenantId("tenant-a")
                .build();

        Toolkit toolkit = factory.toolkitFor(request);

        assertEquals(Set.of("weather", "hidden"), Set.copyOf(toolkit.getToolNames()));
        toolkit.callTool(ToolCallParam.builder()
                        .toolUseBlock(ToolUseBlock.builder()
                                .id("call-1")
                                .name("weather")
                                .input(Map.of("city", "Hangzhou"))
                                .content("{\"city\":\"Hangzhou\"}")
                                .build())
                        .input(Map.of("city", "Hangzhou"))
                        .build())
                .block();
        assertEquals(List.of("weather", "hidden"), gateway.request.get().allowedToolIds());
    }

    @Test
    void restoresRequestTenantWhileInvokingGatewayAndThenRestoresPreviousTenant() {
        TenantContext.set("outer-tenant");
        CapturingGateway gateway = new CapturingGateway();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), gateway);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .allowedToolIds(List.of("weather"))
                .tenantId("tenant-a")
                .build();

        Toolkit toolkit = factory.toolkitFor(request);
        toolkit.callTool(ToolCallParam.builder()
                        .toolUseBlock(ToolUseBlock.builder()
                                .id("call-1")
                                .name("weather")
                                .input(Map.of())
                                .content("{}")
                                .build())
                        .input(Map.of())
                        .build())
                .block();

        assertEquals("tenant-a", gateway.tenantDuringInvoke.get());
        assertEquals("outer-tenant", TenantContext.get());
    }

    @Test
    void approvalRequiredPolicyDecisionCreatesSeahorseApprovalAndAsksAgentscopeToPause() {
        CapturingGateway gateway = new CapturingGateway();
        CapturingApprovalRepository approvalRepository = new CapturingApprovalRepository();
        ToolPolicyPort policy = request -> PolicyDecision.approvalRequired(
                "decision-1", ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, "approval required");
        AgentScopeToolFactory factory = new AgentScopeToolFactory(
                new StaticRegistry(),
                gateway,
                policy,
                approvalRepository);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .allowedToolIds(List.of("weather"))
                .runId("run-1")
                .agentId("agent-1")
                .tenantId("tenant-a")
                .userId("user-1")
                .build();

        Toolkit toolkit = factory.toolkitFor(request);
        ToolBase tool = assertInstanceOf(ToolBase.class, toolkit.getTool("weather"));

        var decision = tool.checkPermissions(Map.of("city", "Hangzhou"), null).block();

        assertNotNull(decision);
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
        ApprovalRequest approval = approvalRepository.request.get();
        assertNotNull(approval);
        assertEquals("run-1", approval.runId());
        assertEquals("agentscope-step", approval.stepId());
        assertEquals("tenant-a", approval.tenantId());
        assertEquals("user-1", approval.userId());
        assertEquals("agent-1", approval.agentId());
        assertEquals("weather", approval.toolId());
        assertEquals(null, gateway.request.get());
    }

    @Test
    void approvedApprovalForAnotherToolDoesNotSatisfyCurrentPermissionCheck() {
        CapturingGateway gateway = new CapturingGateway();
        CapturingApprovalRepository approvalRepository = new CapturingApprovalRepository();
        ToolPolicyPort policy = request -> PolicyDecision.approvalRequired(
                "decision-1", ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, "approval required");
        AgentScopeToolFactory factory = new AgentScopeToolFactory(
                new StaticRegistry(),
                gateway,
                policy,
                approvalRepository,
                approvalQuery(approvedApproval("approval-1", "run-1", "agentscope-step", "call-hidden", "hidden")),
                null,
                null);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .allowedToolIds(List.of("weather"))
                .runId("run-1")
                .agentId("agent-1")
                .tenantId("tenant-a")
                .userId("user-1")
                .build();

        ToolBase tool = assertInstanceOf(ToolBase.class, factory.toolkitFor(request).getTool("weather"));
        var decision = tool.checkPermissions(Map.of("city", "Hangzhou"), null).block();

        assertNotNull(decision);
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
        ApprovalRequest approval = approvalRepository.request.get();
        assertNotNull(approval);
        assertEquals("weather", approval.toolId());
    }

    @Test
    void approvedApprovalForSameToolWithDifferentArgumentsDoesNotSatisfyCurrentPermissionCheck() {
        CapturingGateway gateway = new CapturingGateway();
        CapturingApprovalRepository firstApprovalRepository = new CapturingApprovalRepository();
        ToolPolicyPort policy = request -> PolicyDecision.approvalRequired(
                "decision-1", ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, "approval required");
        AgentScopeToolFactory firstFactory = new AgentScopeToolFactory(
                new StaticRegistry(),
                gateway,
                policy,
                firstApprovalRepository);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .allowedToolIds(List.of("weather"))
                .runId("run-1")
                .agentId("agent-1")
                .tenantId("tenant-a")
                .userId("user-1")
                .build();
        ToolBase firstTool = assertInstanceOf(ToolBase.class, firstFactory.toolkitFor(request).getTool("weather"));
        firstTool.checkPermissions(Map.of("city", "Hangzhou"), null).block();
        ApprovalRequest oldApproval = firstApprovalRepository.request.get();
        assertNotNull(oldApproval);

        CapturingApprovalRepository approvalRepository = new CapturingApprovalRepository();
        ApprovalRequest previousApproval = approvedApproval(
                "approval-1",
                oldApproval.runId(),
                oldApproval.stepId(),
                oldApproval.toolInvocationId(),
                oldApproval.toolId(),
                oldApproval.argumentsPreviewJson());
        AgentScopeToolFactory factory = new AgentScopeToolFactory(
                new StaticRegistry(),
                gateway,
                policy,
                approvalRepository,
                approvalQuery(previousApproval),
                null,
                null);

        ToolBase tool = assertInstanceOf(ToolBase.class, factory.toolkitFor(request).getTool("weather"));
        var decision = tool.checkPermissions(Map.of("city", "Shanghai"), null).block();

        assertNotNull(decision);
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
        ApprovalRequest approval = approvalRepository.request.get();
        assertNotNull(approval);
        assertEquals("weather", approval.toolId());
    }

    private static ApprovalRequest approvedApproval(
            String approvalId,
            String runId,
            String stepId,
            String toolInvocationId,
            String toolId) {
        return approvedApproval(approvalId, runId, stepId, toolInvocationId, toolId, "{}");
    }

    private static ApprovalRequest approvedApproval(
            String approvalId,
            String runId,
            String stepId,
            String toolInvocationId,
            String toolId,
            String argumentsPreviewJson) {
        return new ApprovalRequest(
                approvalId,
                runId,
                stepId,
                toolInvocationId,
                "tenant-a",
                "user-1",
                "agent-1",
                null,
                toolId,
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool requires approval",
                argumentsPreviewJson,
                ApprovalRequestStatus.APPROVED,
                Instant.parse("2026-06-20T00:00:00Z"),
                null,
                "approver-1",
                Instant.parse("2026-06-20T00:01:00Z"),
                "approved");
    }

    private static ApprovalRequestQueryPort approvalQuery(ApprovalRequest approval) {
        return new ApprovalRequestQueryPort() {
            @Override
            public Optional<ApprovalRequest> findById(String approvalId) {
                return approval.approvalId().equals(approvalId) ? Optional.of(approval) : Optional.empty();
            }

            @Override
            public Optional<ApprovalRequest> findLatestByRunIdAndStepId(String runId, String stepId) {
                return approval.runId().equals(runId) && approval.stepId().equals(stepId)
                        ? Optional.of(approval)
                        : Optional.empty();
            }

            @Override
            public ApprovalRequestPage page(ApprovalRequestQuery query) {
                return new ApprovalRequestPage(List.of(approval), 1L, 1L, 1L, 1L);
            }
        };
    }

    private static final class StaticRegistry implements ToolRegistryPort {
        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(
                    new ToolDescriptor("weather", "Weather", "Get weather",
                            "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}"),
                    new ToolDescriptor("hidden", "Hidden", "Hidden tool", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            return Optional.empty();
        }
    }

    private static final class CapturingGateway implements ToolGatewayPort {
        private final AtomicReference<ToolInvocationRequest> request = new AtomicReference<>();
        private final AtomicReference<String> tenantDuringInvoke = new AtomicReference<>();
        private ToolInvocationResult result = ToolInvocationResult.ok("weather=cloudy");

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            this.request.set(request);
            this.tenantDuringInvoke.set(TenantContext.get());
            return result;
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
