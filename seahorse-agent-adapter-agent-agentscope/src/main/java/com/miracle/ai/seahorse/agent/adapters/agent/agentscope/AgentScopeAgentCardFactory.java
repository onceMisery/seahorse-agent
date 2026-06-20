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

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentScopeAgentCardFactory {

    public AgentCard agentCard(AgentScopeProperties properties) {
        AgentScopeProperties safeProperties = Objects.requireNonNull(properties, "properties must not be null");
        AgentScopeProperties.A2a a2a = safeProperties.getA2a();
        String endpointUrl = endpointUrl(a2a);
        String registeredName = registeredAgentName(a2a);
        AgentCard card = new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name(registeredName)
                .description(textOrDefault(a2a.getDescription(), "Seahorse Agent"))
                .version(textOrDefault(a2a.getVersion(), "1.0.0"))
                .url(endpointUrl)
                .preferredTransport(preferredTransport(a2a))
                .capabilities(new AgentCapabilities.Builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill.Builder()
                        .id("seahorse.agent")
                        .name(textOrDefault(a2a.getAgentName(), "seahorse-agent"))
                        .description(textOrDefault(a2a.getDescription(), "Seahorse Agent"))
                        .tags(List.of("seahorse", "a2a"))
                        .build()))
                .build();
        return A2ATenantMetadata.withTenant(card, a2a.getTenantId(), m3Metadata(safeProperties.getNacos().getM3()));
    }

    public ConfigurableAgentCard configurableAgentCard(AgentScopeProperties properties) {
        AgentScopeProperties safeProperties = Objects.requireNonNull(properties, "properties must not be null");
        AgentScopeProperties.A2a a2a = safeProperties.getA2a();
        return new ConfigurableAgentCard.Builder()
                .name(registeredAgentName(a2a))
                .description(textOrDefault(a2a.getDescription(), "Seahorse Agent"))
                .version(textOrDefault(a2a.getVersion(), "1.0.0"))
                .url(endpointUrl(a2a))
                .preferredTransport(preferredTransport(a2a))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(
                        new AgentSkill.Builder()
                                .id("seahorse.agent")
                                .name(textOrDefault(a2a.getAgentName(), "seahorse-agent"))
                                .description(textOrDefault(a2a.getDescription(), "Seahorse Agent"))
                                .tags(List.of("seahorse", "a2a"))
                                .build(),
                        A2ATenantMetadata.boundarySkill(a2a.getTenantId(),
                                m3Metadata(safeProperties.getNacos().getM3()))))
                .build();
    }

    private Map<String, String> m3Metadata(AgentScopeProperties.M3 m3) {
        if (m3 == null || !m3.isEnabled()) {
            return m3 == null ? Map.of() : m3.getMetadata();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "mode", textOrDefault(m3.getMode(), "M3"));
        putIfPresent(metadata, "namespace", m3.getNamespace());
        putIfPresent(metadata, "group", m3.getGroup());
        putIfPresent(metadata, "clusterName", m3.getClusterName());
        m3.getMetadata().forEach((key, value) -> putIfPresent(metadata, key, value));
        return Map.copyOf(metadata);
    }

    private void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (!isBlank(key) && !isBlank(value)) {
            metadata.put(key.trim(), value.trim());
        }
    }

    private String registeredAgentName(AgentScopeProperties.A2a a2a) {
        return A2ATenantMetadata.tenantQualifiedAgentName(
                textOrDefault(a2a.getTenantId(), "default"),
                textOrDefault(a2a.getAgentName(), "seahorse-agent"));
    }

    private String endpointUrl(AgentScopeProperties.A2a a2a) {
        if (!isBlank(a2a.getUrl())) {
            return a2a.getUrl().trim();
        }
        if (isBlank(a2a.getHost()) || a2a.getPort() <= 0) {
            throw new IllegalStateException("AgentScope A2A endpoint requires seahorse.agentscope.a2a.url "
                    + "or host and port");
        }
        String protocol = textOrDefault(a2a.getProtocol(), a2a.isSupportTls() ? "https" : "http");
        String path = textOrDefault(a2a.getPath(), "/a2a");
        String query = isBlank(a2a.getQuery()) ? "" : "?" + a2a.getQuery().trim();
        return protocol + "://" + a2a.getHost().trim() + ":" + a2a.getPort() + path + query;
    }

    private String preferredTransport(AgentScopeProperties.A2a a2a) {
        String preferred = textOrDefault(a2a.getPreferredTransport(), a2a.getTransport());
        return "jsonrpc".equalsIgnoreCase(preferred) ? TransportProtocol.JSONRPC.asString() : preferred;
    }

    private static String textOrDefault(String value, String fallback) {
        return isBlank(value) ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
