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
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentScopeA2AAgentConnector implements A2AAgentConnectorPort {

    private final AgentCardResolver resolver;
    private final AgentScopeRemoteAgentInvoker invoker;
    private final A2aDiscoveryPolicy discoveryPolicy;
    private static final String JSONRPC_TRANSPORT = "JSONRPC";

    public AgentScopeA2AAgentConnector(AgentCardResolver resolver, AgentScopeRemoteAgentInvoker invoker) {
        this(resolver, invoker, A2aDiscoveryPolicy.none());
    }

    public AgentScopeA2AAgentConnector(
            AgentCardResolver resolver,
            AgentScopeRemoteAgentInvoker invoker,
            A2aDiscoveryPolicy discoveryPolicy) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker must not be null");
        this.discoveryPolicy = Objects.requireNonNullElseGet(discoveryPolicy, A2aDiscoveryPolicy::none);
    }

    @Override
    public RemoteAgentCard resolve(A2AAgentResolveRequest request) {
        AgentCard card = resolveTenantCard(request.tenantId(), request.agentName(), request.version());
        return toRemoteCard(card, request.tenantId());
    }

    @Override
    public A2AAgentResult invoke(A2AAgentRequest request) {
        AgentCard card = resolveTenantCard(request.tenantId(), request.agentName(), request.metadata().get("version"));
        String content = invoker.invoke(card, request);
        return new A2AAgentResult(request.tenantId(), request.agentName(), content, metadata(card, request.tenantId()));
    }

    private AgentCard resolveTenantCard(String tenantId, String agentName, String requestedVersion) {
        AgentCard card = firstMatchingTenantCard(tenantId, agentName, requestedVersion);
        if (card == null) {
            throw new IllegalStateException("A2A agent card not found: " + agentName + versionSuffix(requestedVersion));
        }
        String cardTenantId = A2ATenantMetadata.tenantId(card)
                .orElseThrow(() -> new SecurityException("A2A agent card missing tenantId: " + agentName));
        if (!tenantId.equals(cardTenantId)) {
            throw new SecurityException("A2A tenant mismatch: request=" + tenantId + ", card=" + cardTenantId);
        }
        if (hasText(requestedVersion) && !requestedVersion.trim().equals(card.version())) {
            throw new IllegalStateException("A2A agent card version mismatch: request="
                    + requestedVersion.trim() + ", card=" + Objects.requireNonNullElse(card.version(), ""));
        }
        return card;
    }

    private AgentCard firstMatchingTenantCard(String tenantId, String agentName, String requestedVersion) {
        AgentCard fallback = null;
        List<AgentCard> tenantMatches = new ArrayList<>();
        RuntimeException lastLookupFailure = null;
        for (String candidateName : candidateNames(tenantId, agentName, requestedVersion)) {
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
                if (hasText(requestedVersion) && !requestedVersion.trim().equals(candidate.version())) {
                    continue;
                }
                tenantMatches.add(candidate);
                continue;
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        if (!tenantMatches.isEmpty()) {
            return discoveryPolicy.select(tenantMatches);
        }
        if (fallback == null && lastLookupFailure != null) {
            throw new IllegalStateException("A2A agent card lookup failed: " + agentName, lastLookupFailure);
        }
        return fallback;
    }

    private List<String> candidateNames(String tenantId, String agentName, String requestedVersion) {
        if (hasText(requestedVersion)) {
            String version = requestedVersion.trim();
            return List.of(
                    tenantId + "/" + agentName + "@" + version,
                    tenantId + "/" + agentName + ":" + version,
                    tenantId + ":" + agentName + "@" + version,
                    tenantId + ":" + agentName + ":" + version,
                    agentName + "@" + tenantId + "@" + version,
                    agentName + "@" + version + "@" + tenantId,
                    agentName + "@" + version,
                    agentName + ":" + version);
        }
        return List.of(tenantId + "/" + agentName, tenantId + ":" + agentName, agentName + "@" + tenantId, agentName);
    }

    private String versionSuffix(String requestedVersion) {
        return hasText(requestedVersion) ? "@" + requestedVersion.trim() : "";
    }

    private RemoteAgentCard toRemoteCard(AgentCard card, String tenantId) {
        return new RemoteAgentCard(
                tenantId,
                card.name(),
                Objects.requireNonNullElse(card.version(), ""),
                Objects.requireNonNullElse(card.description(), ""),
                effectiveUrl(card),
                metadata(card, tenantId));
    }

    private String effectiveUrl(AgentCard card) {
        if (card.additionalInterfaces() != null) {
            for (AgentInterface agentInterface : card.additionalInterfaces()) {
                if (agentInterface != null
                        && JSONRPC_TRANSPORT.equalsIgnoreCase(agentInterface.transport())
                        && hasText(agentInterface.url())) {
                    return agentInterface.url().trim();
                }
            }
        }
        return Objects.requireNonNullElse(card.url(), "");
    }

    private Map<String, Object> metadata(AgentCard card, String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("agentName", Objects.requireNonNullElse(card.name(), ""));
        result.put("version", Objects.requireNonNullElse(card.version(), ""));
        result.put("jsonrpcUrl", effectiveUrl(card));
        result.put("preferredTransport", Objects.requireNonNullElse(card.preferredTransport(), ""));
        result.put("protocolVersion", Objects.requireNonNullElse(card.protocolVersion(), ""));
        result.putAll(a2aMetadata(card));
        result.putAll(m3Metadata(card));
        return Map.copyOf(result);
    }

    private Map<String, Object> a2aMetadata(AgentCard card) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (card.skills() == null) {
            return result;
        }
        for (AgentSkill skill : card.skills()) {
            if (skill == null || skill.tags() == null) {
                continue;
            }
            for (String tag : skill.tags()) {
                if (tag == null || !tag.startsWith(A2ATenantMetadata.A2A_TAG_PREFIX)) {
                    continue;
                }
                String pair = tag.substring(A2ATenantMetadata.A2A_TAG_PREFIX.length());
                int separator = pair.indexOf('=');
                if (separator <= 0 || separator == pair.length() - 1) {
                    continue;
                }
                result.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return result;
    }

    private Map<String, Object> m3Metadata(AgentCard card) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (card.skills() == null) {
            return result;
        }
        for (AgentSkill skill : card.skills()) {
            if (skill == null || !A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()) || skill.tags() == null) {
                continue;
            }
            for (String tag : skill.tags()) {
                if (tag == null || !tag.startsWith(A2ATenantMetadata.M3_TAG_PREFIX)) {
                    continue;
                }
                String pair = tag.substring(A2ATenantMetadata.M3_TAG_PREFIX.length());
                int separator = pair.indexOf('=');
                if (separator <= 0 || separator == pair.length() - 1) {
                    continue;
                }
                result.put("m3." + pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
