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

import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolExecutionPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolPermission;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentScopeToolFactoryTests {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void registersAllowedSeahorseToolsAndRoutesCallsThroughGovernedFacade() {
        CapturingToolExecution tools = new CapturingToolExecution();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), tools);
        AgentLoopRequest request = requestBuilder()
                .allowedToolIds(List.of("weather"))
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
        ToolInvocationRequest captured = tools.invokeRequest.get();
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
    void emptyAllowedToolIdsExposeAllRegisteredToolsAndPassConcreteToolAllowlistToFacade() {
        CapturingToolExecution tools = new CapturingToolExecution();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), tools);
        AgentLoopRequest request = requestBuilder()
                .allowedToolIds(List.of())
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
        assertEquals(List.of("weather", "hidden"), tools.invokeRequest.get().allowedToolIds());
    }

    @Test
    void explicitEmptyAllowedToolIdsExposeNoTools() {
        CapturingToolExecution tools = new CapturingToolExecution();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), tools);
        AgentLoopRequest request = requestBuilder()
                .allowedToolIds(List.of())
                .explicitToolAllowlist(true)
                .build();

        Toolkit toolkit = factory.toolkitFor(request);

        assertEquals(List.of(), toolkit.getToolNames().stream().toList());
        assertEquals(null, tools.invokeRequest.get());
    }

    @Test
    void restoresRequestTenantWhileInvokingFacadeAndThenRestoresPreviousTenant() {
        TenantContext.set("outer-tenant");
        CapturingToolExecution tools = new CapturingToolExecution();
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), tools);
        AgentLoopRequest request = requestBuilder()
                .allowedToolIds(List.of("weather"))
                .build();

        factory.toolkitFor(request).callTool(ToolCallParam.builder()
                        .toolUseBlock(ToolUseBlock.builder()
                                .id("call-1")
                                .name("weather")
                                .input(Map.of())
                                .content("{}")
                                .build())
                        .input(Map.of())
                        .build())
                .block();

        assertEquals("tenant-a", tools.tenantDuringInvoke.get());
        assertEquals("outer-tenant", TenantContext.get());
    }

    @Test
    void approvalRequiredPreflightAsksAgentscopeWithoutInvokingTool() {
        CapturingToolExecution tools = new CapturingToolExecution();
        tools.permission = GovernedToolPermission.approvalRequired(
                "approval-1",
                "TOOL_APPROVAL_REQUIRED",
                "approval required");
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), tools);
        ToolBase tool = assertInstanceOf(ToolBase.class,
                factory.toolkitFor(requestBuilder().allowedToolIds(List.of("weather")).build()).getTool("weather"));

        var decision = tool.checkPermissions(Map.of("city", "Hangzhou"), null).block();

        assertNotNull(decision);
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
        assertNotNull(tools.preflightRequest.get());
        assertEquals(null, tools.invokeRequest.get());
    }

    @Test
    void deniedPreflightDeniesAgentscopePermission() {
        CapturingToolExecution tools = new CapturingToolExecution();
        tools.permission = GovernedToolPermission.deny("TOOL_NOT_BOUND", "not bound");
        AgentScopeToolFactory factory = new AgentScopeToolFactory(new StaticRegistry(), tools);
        ToolBase tool = assertInstanceOf(ToolBase.class,
                factory.toolkitFor(requestBuilder().allowedToolIds(List.of("weather")).build()).getTool("weather"));

        var decision = tool.checkPermissions(Map.of("city", "Hangzhou"), null).block();

        assertNotNull(decision);
        assertEquals(PermissionBehavior.DENY, decision.getBehavior());
    }

    private static AgentLoopRequest.Builder requestBuilder() {
        return AgentLoopRequest.builder()
                .question("lookup")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId("run-1")
                .agentId("agent-1")
                .versionId("version-1")
                .rolloutId("rollout-1")
                .tenantId("tenant-a")
                .userId("user-1")
                .agentIdentityId("identity-1");
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

    private static final class CapturingToolExecution implements GovernedToolExecutionPort {
        private final AtomicReference<ToolInvocationRequest> preflightRequest = new AtomicReference<>();
        private final AtomicReference<ToolInvocationRequest> invokeRequest = new AtomicReference<>();
        private final AtomicReference<String> tenantDuringInvoke = new AtomicReference<>();
        private GovernedToolPermission permission = GovernedToolPermission.allow("ALLOW", "allowed");
        private ToolInvocationResult result = ToolInvocationResult.ok("weather=cloudy");

        @Override
        public GovernedToolPermission preflight(ToolInvocationRequest request) {
            preflightRequest.set(request);
            return permission;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            invokeRequest.set(request);
            tenantDuringInvoke.set(TenantContext.get());
            return result;
        }
    }
}
