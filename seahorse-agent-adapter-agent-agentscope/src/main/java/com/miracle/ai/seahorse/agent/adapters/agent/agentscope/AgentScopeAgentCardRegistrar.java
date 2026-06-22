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

import com.alibaba.nacos.api.ai.A2aService;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.exception.NacosException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryTransportProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.DisposableBean;

import java.util.Objects;

public class AgentScopeAgentCardRegistrar implements ApplicationRunner, DisposableBean {

    private final NacosA2aRegistry registry;
    private final NacosPropertiesFactory nacosPropertiesFactory;
    private final AgentScopeAgentCardFactory cardFactory;
    private final AgentScopeProperties properties;
    private final AgentCardResolver resolver;
    private final A2aService a2aService;
    private AgentCard registeredCard;
    private NacosA2aRegistryProperties registeredRegistryProperties;
    private static final String JSONRPC_TRANSPORT = "JSONRPC";

    public AgentScopeAgentCardRegistrar(
            NacosA2aRegistry registry,
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeAgentCardFactory cardFactory,
            AgentScopeProperties properties) {
        this(registry, nacosPropertiesFactory, cardFactory, properties, null);
    }

    public AgentScopeAgentCardRegistrar(
            NacosA2aRegistry registry,
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeAgentCardFactory cardFactory,
            AgentScopeProperties properties,
            AgentCardResolver resolver) {
        this(registry, nacosPropertiesFactory, cardFactory, properties, resolver, null);
    }

    public AgentScopeAgentCardRegistrar(
            NacosA2aRegistry registry,
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeAgentCardFactory cardFactory,
            AgentScopeProperties properties,
            AgentCardResolver resolver,
            A2aService a2aService) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.nacosPropertiesFactory = Objects.requireNonNull(nacosPropertiesFactory,
                "nacosPropertiesFactory must not be null");
        this.cardFactory = Objects.requireNonNull(cardFactory, "cardFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.resolver = resolver;
        this.a2aService = a2aService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AgentCard card = cardFactory.agentCard(properties);
        assertDuplicatePolicy(card);
        NacosA2aRegistryProperties registryProperties = nacosPropertiesFactory.a2aRegistryProperties(properties);
        registry.registerAgent(card, registryProperties);
        this.registeredCard = card;
        this.registeredRegistryProperties = registryProperties;
    }

    @Override
    public void destroy() throws Exception {
        if (a2aService == null
                || registeredCard == null
                || registeredRegistryProperties == null
                || !registeredRegistryProperties.enabledRegisterEndpoint()
                || registeredRegistryProperties.transportProperties().isEmpty()) {
            return;
        }
        for (NacosA2aRegistryTransportProperties transportProperties
                : registeredRegistryProperties.transportProperties().values()) {
            deregisterEndpoint(registeredCard, transportProperties);
        }
    }

    private void deregisterEndpoint(
            AgentCard card,
            NacosA2aRegistryTransportProperties transportProperties) throws NacosException {
        AgentEndpoint endpoint = new AgentEndpoint();
        endpoint.setTransport(transportProperties.transport());
        endpoint.setAddress(transportProperties.host());
        endpoint.setPort(transportProperties.port());
        endpoint.setPath(transportProperties.path());
        endpoint.setSupportTls(transportProperties.supportTls());
        endpoint.setVersion(card.version());
        endpoint.setProtocol(transportProperties.protocol());
        endpoint.setQuery(transportProperties.query());
        a2aService.deregisterAgentEndpoint(card.name(), endpoint);
    }

    private void assertDuplicatePolicy(AgentCard card) {
        if (resolver == null || card == null || isBlank(card.name())) {
            return;
        }
        AgentCard existing;
        try {
            existing = resolver.getAgentCard(card.name());
        } catch (RuntimeException ex) {
            return;
        }
        if (existing == null || !sameTenant(existing, card) || !sameVersion(existing, card)) {
            return;
        }
        if (sameUrl(existing, card)) {
            return;
        }
        if (properties.getA2a().getDuplicateRegistrationPolicy() == A2aDuplicateRegistrationPolicy.REPLACE) {
            return;
        }
        throw new IllegalStateException("A2A duplicate registration conflict for " + card.name()
                + " version " + Objects.requireNonNullElse(card.version(), "")
                + ": existingUrl=" + effectiveUrl(existing) + ", requestedUrl=" + effectiveUrl(card));
    }

    private boolean sameTenant(AgentCard first, AgentCard second) {
        return Objects.equals(A2ATenantMetadata.tenantId(first).orElse(""),
                A2ATenantMetadata.tenantId(second).orElse(""));
    }

    private boolean sameVersion(AgentCard first, AgentCard second) {
        return Objects.equals(Objects.requireNonNullElse(first.version(), ""),
                Objects.requireNonNullElse(second.version(), ""));
    }

    private boolean sameUrl(AgentCard first, AgentCard second) {
        return Objects.equals(effectiveUrl(first), effectiveUrl(second));
    }

    private String effectiveUrl(AgentCard card) {
        if (card == null) {
            return "";
        }
        if (card.additionalInterfaces() != null) {
            for (AgentInterface agentInterface : card.additionalInterfaces()) {
                if (agentInterface != null
                        && JSONRPC_TRANSPORT.equalsIgnoreCase(agentInterface.transport())
                        && !isBlank(agentInterface.url())) {
                    return agentInterface.url().trim();
                }
            }
        }
        return Objects.requireNonNullElse(card.url(), "").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
