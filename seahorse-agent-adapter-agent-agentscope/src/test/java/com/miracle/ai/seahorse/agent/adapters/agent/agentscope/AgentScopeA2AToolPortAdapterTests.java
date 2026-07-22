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

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResolveRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.RemoteAgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeA2AToolPortAdapterTests {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void invokesRemoteAgentThroughConnectorUsingCurrentTenant() {
        TenantContext.set("tenant-a");
        CapturingConnector connector = new CapturingConnector();
        AgentScopeA2AToolPortAdapter adapter = new AgentScopeA2AToolPortAdapter(connector);

        var result = adapter.invoke("call-1", AgentScopeA2AToolPortAdapter.TOOL_ID, Map.of(
                "agentName", "planner",
                "prompt", "draft a plan",
                "metadata", Map.of("source", "agent-loop")));

        assertTrue(result.success());
        assertEquals("remote answer", result.content());
        A2AAgentRequest captured = connector.request.get();
        assertEquals("tenant-a", captured.tenantId());
        assertEquals("planner", captured.agentName());
        assertEquals("draft a plan", captured.prompt());
        assertEquals("agent-loop", captured.metadata().get("source"));
    }

    @Test
    void resolvesAgentNameAsAuthoritativeRemoteResourceReference() {
        AgentScopeA2AToolPortAdapter adapter = new AgentScopeA2AToolPortAdapter(new CapturingConnector());
        ToolInvocationRequest request = new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-a",
                "user-1",
                "agent-identity-1",
                AgentScopeA2AToolPortAdapter.TOOL_ID,
                Map.of("agentName", "planner", "prompt", "draft a plan"),
                Map.of("callerSupplied", "untrusted-resource"),
                "run-1:call-1",
                List.of());

        assertEquals(Map.of("agentName", "planner"), adapter.resolveResourceRefs(request));
    }

    @Test
    void degradesRemoteInvocationFailuresWithoutLeakingPrompt() {
        TenantContext.set("tenant-a");
        CapturingConnector connector = new CapturingConnector();
        connector.failure = new IllegalStateException("remote unavailable for draft a confidential launch plan");
        AgentScopeA2AToolPortAdapter adapter = new AgentScopeA2AToolPortAdapter(connector);

        var result = adapter.invoke("call-1", AgentScopeA2AToolPortAdapter.TOOL_ID, Map.of(
                "agentName", "planner",
                "prompt", "draft a confidential launch plan"));

        assertFalse(result.success());
        assertTrue(result.error().contains("agentName=planner"));
        assertTrue(result.error().contains("remote unavailable"));
        assertTrue(result.error().contains("[redacted-prompt]"));
        assertFalse(result.error().contains("confidential launch plan"));
    }

    @Test
    void degradesRemoteInvocationFailuresWithoutLeakingCredentialShapedErrorText() {
        TenantContext.set("tenant-a");
        CapturingConnector connector = new CapturingConnector();
        connector.failure = new IllegalStateException(
                "remote unavailable for draft a confidential launch plan "
                        + "Authorization: Bearer abcdefghijklmnop api_key=plain-a2a-secret");
        AgentScopeA2AToolPortAdapter adapter = new AgentScopeA2AToolPortAdapter(connector);

        var result = adapter.invoke("call-1", AgentScopeA2AToolPortAdapter.TOOL_ID, Map.of(
                "agentName", "planner",
                "prompt", "draft a confidential launch plan"));

        assertFalse(result.success());
        assertTrue(result.error().contains("agentName=planner"));
        assertTrue(result.error().contains("remote unavailable"));
        assertTrue(result.error().contains("[redacted-prompt]"));
        assertTrue(result.error().contains("[REDACTED]"));
        assertFalse(result.error().contains("confidential launch plan"));
        assertFalse(result.error().contains("abcdefghijklmnop"));
        assertFalse(result.error().contains("plain-a2a-secret"));
    }

    @Test
    void rejectsOversizedMetadataBeforeInvokingConnector() {
        TenantContext.set("tenant-a");
        CapturingConnector connector = new CapturingConnector();
        AgentScopeA2AToolPortAdapter adapter = new AgentScopeA2AToolPortAdapter(connector);
        Map<String, String> metadata = IntStream.range(0, 17)
                .boxed()
                .collect(Collectors.toMap(index -> "key-" + index, index -> "value-" + index));

        var result = adapter.invoke("call-1", AgentScopeA2AToolPortAdapter.TOOL_ID, Map.of(
                "agentName", "planner",
                "prompt", "draft a plan",
                "metadata", metadata));

        assertFalse(result.success());
        assertTrue(result.error().contains("metadata exceeds max entries"));
        assertEquals(null, connector.request.get());
    }

    @Test
    void rejectsMetadataControlCharactersBeforeInvokingConnector() {
        TenantContext.set("tenant-a");
        CapturingConnector connector = new CapturingConnector();
        AgentScopeA2AToolPortAdapter adapter = new AgentScopeA2AToolPortAdapter(connector);

        var result = adapter.invoke("call-1", AgentScopeA2AToolPortAdapter.TOOL_ID, Map.of(
                "agentName", "planner",
                "prompt", "draft a plan",
                "metadata", Map.of("source", "agent\u0000loop")));

        assertFalse(result.success());
        assertTrue(result.error().contains("metadata contains control characters"));
        assertEquals(null, connector.request.get());
    }

    private static final class CapturingConnector implements A2AAgentConnectorPort {
        private final AtomicReference<A2AAgentRequest> request = new AtomicReference<>();
        private RuntimeException failure;

        @Override
        public RemoteAgentCard resolve(A2AAgentResolveRequest request) {
            return new RemoteAgentCard(request.tenantId(), request.agentName(), "1.0.0", "remote", "http://remote",
                    Map.of());
        }

        @Override
        public A2AAgentResult invoke(A2AAgentRequest request) {
            if (failure != null) {
                throw failure;
            }
            this.request.set(request);
            return new A2AAgentResult(request.tenantId(), request.agentName(), "remote answer", Map.of());
        }
    }
}
