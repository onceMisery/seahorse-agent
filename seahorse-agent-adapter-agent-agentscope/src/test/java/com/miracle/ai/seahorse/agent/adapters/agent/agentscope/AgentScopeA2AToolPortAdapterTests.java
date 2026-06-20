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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResolveRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.RemoteAgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final class CapturingConnector implements A2AAgentConnectorPort {
        private final AtomicReference<A2AAgentRequest> request = new AtomicReference<>();

        @Override
        public RemoteAgentCard resolve(A2AAgentResolveRequest request) {
            return new RemoteAgentCard(request.tenantId(), request.agentName(), "1.0.0", "remote", "http://remote",
                    Map.of());
        }

        @Override
        public A2AAgentResult invoke(A2AAgentRequest request) {
            this.request.set(request);
            return new A2AAgentResult(request.tenantId(), request.agentName(), "remote answer", Map.of());
        }
    }
}
