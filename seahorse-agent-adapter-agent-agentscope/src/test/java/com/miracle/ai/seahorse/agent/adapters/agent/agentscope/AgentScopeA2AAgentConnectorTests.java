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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResolveRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.RemoteAgentCard;
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentScopeA2AAgentConnectorTests {

    @Test
    void rejectsRemoteCardFromDifferentTenant() {
        AgentCard card = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-b", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(resolver(card), (ignored, request) -> "");

        assertThrows(SecurityException.class,
                () -> connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner")));
    }

    @Test
    void resolvesAndInvokesMatchingTenant() {
        AgentCard card = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                resolver(card),
                (ignored, request) -> "remote: " + request.prompt());

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));
        A2AAgentResult result = connector.invoke(new A2AAgentRequest("tenant-a", "planner", "draft", Map.of()));

        assertEquals("tenant-a", remoteCard.tenantId());
        assertEquals("planner", remoteCard.agentName());
        assertEquals("remote: draft", result.content());
    }

    @Test
    void resolvesTenantQualifiedCardBeforePlainAgentName() {
        AgentCard wrongTenant = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-b", Map.of());
        AgentCard matchingTenant = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> "tenant-a/planner".equals(agentName) ? matchingTenant : wrongTenant,
                (ignored, request) -> "remote: " + request.prompt());

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("tenant-a", remoteCard.tenantId());
        assertEquals("planner", remoteCard.agentName());
    }

    @Test
    void continuesCandidateLookupWhenResolverThrowsForMissingQualifiedName() {
        AgentCard matchingTenant = A2ATenantMetadata.withTenant(A2ATenantMetadataTests.baseCard(), "tenant-a", Map.of());
        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                agentName -> {
                    if ("planner".equals(agentName)) {
                        return matchingTenant;
                    }
                    throw new IllegalStateException("agent not found: " + agentName);
                },
                (ignored, request) -> "remote: " + request.prompt());

        RemoteAgentCard remoteCard = connector.resolve(new A2AAgentResolveRequest("tenant-a", "planner"));

        assertEquals("tenant-a", remoteCard.tenantId());
        assertEquals("planner", remoteCard.agentName());
    }

    private AgentCardResolver resolver(AgentCard card) {
        return agentName -> card;
    }
}
