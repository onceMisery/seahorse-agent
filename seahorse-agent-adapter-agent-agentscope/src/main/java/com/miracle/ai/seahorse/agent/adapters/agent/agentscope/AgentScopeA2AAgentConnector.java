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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResolveRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.RemoteAgentCard;
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentScopeA2AAgentConnector implements A2AAgentConnectorPort {

    private final AgentCardResolver resolver;
    private final AgentScopeRemoteAgentInvoker invoker;

    public AgentScopeA2AAgentConnector(AgentCardResolver resolver, AgentScopeRemoteAgentInvoker invoker) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker must not be null");
    }

    @Override
    public RemoteAgentCard resolve(A2AAgentResolveRequest request) {
        AgentCard card = resolveTenantCard(request.tenantId(), request.agentName());
        return toRemoteCard(card, request.tenantId());
    }

    @Override
    public A2AAgentResult invoke(A2AAgentRequest request) {
        AgentCard card = resolveTenantCard(request.tenantId(), request.agentName());
        String content = invoker.invoke(card, request);
        return new A2AAgentResult(request.tenantId(), request.agentName(), content, metadata(card, request.tenantId()));
    }

    private AgentCard resolveTenantCard(String tenantId, String agentName) {
        AgentCard card = firstMatchingTenantCard(tenantId, agentName);
        if (card == null) {
            throw new IllegalStateException("A2A agent card not found: " + agentName);
        }
        String cardTenantId = A2ATenantMetadata.tenantId(card)
                .orElseThrow(() -> new SecurityException("A2A agent card missing tenantId: " + agentName));
        if (!tenantId.equals(cardTenantId)) {
            throw new SecurityException("A2A tenant mismatch: request=" + tenantId + ", card=" + cardTenantId);
        }
        return card;
    }

    private AgentCard firstMatchingTenantCard(String tenantId, String agentName) {
        AgentCard fallback = null;
        RuntimeException lastLookupFailure = null;
        for (String candidateName : candidateNames(tenantId, agentName)) {
            AgentCard candidate;
            try {
                candidate = resolver.getAgentCard(candidateName);
            } catch (RuntimeException ex) {
                lastLookupFailure = ex;
                continue;
            }
            if (candidate == null) {
                continue;
            }
            String candidateTenantId = A2ATenantMetadata.tenantId(candidate).orElse(null);
            if (tenantId.equals(candidateTenantId)) {
                return candidate;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        if (fallback == null && lastLookupFailure != null) {
            throw new IllegalStateException("A2A agent card lookup failed: " + agentName, lastLookupFailure);
        }
        return fallback;
    }

    private List<String> candidateNames(String tenantId, String agentName) {
        return List.of(tenantId + "/" + agentName, tenantId + ":" + agentName, agentName + "@" + tenantId, agentName);
    }

    private RemoteAgentCard toRemoteCard(AgentCard card, String tenantId) {
        return new RemoteAgentCard(
                tenantId,
                card.name(),
                Objects.requireNonNullElse(card.version(), ""),
                Objects.requireNonNullElse(card.description(), ""),
                Objects.requireNonNullElse(card.url(), ""),
                metadata(card, tenantId));
    }

    private Map<String, Object> metadata(AgentCard card, String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("preferredTransport", Objects.requireNonNullElse(card.preferredTransport(), ""));
        result.put("protocolVersion", Objects.requireNonNullElse(card.protocolVersion(), ""));
        return Map.copyOf(result);
    }
}
