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
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScopeAgentCardRegistrarTests {

    @Test
    void rejectsSameTenantAgentVersionRegisteredWithDifferentUrlByDefault() {
        NacosA2aRegistry registry = mock(NacosA2aRegistry.class);
        AgentCardResolver resolver = mock(AgentCardResolver.class);
        AgentScopeProperties properties = properties("http://new-runtime/a2a");
        when(resolver.getAgentCard("tenant-a/planner"))
                .thenReturn(tenantCardWithVersionUrl("tenant-a", "1.0.0", "http://old-runtime/a2a"));
        AgentScopeAgentCardRegistrar registrar = new AgentScopeAgentCardRegistrar(
                registry,
                new NacosPropertiesFactory(),
                new AgentScopeAgentCardFactory(),
                properties,
                resolver);

        assertThrows(IllegalStateException.class, () -> registrar.run(null));
        verify(registry, never()).registerAgent(any(AgentCard.class), any(NacosA2aRegistryProperties.class));
    }

    @Test
    void allowsSameTenantAgentVersionRegisteredWithDifferentUrlWhenReplacePolicyIsConfigured() {
        NacosA2aRegistry registry = mock(NacosA2aRegistry.class);
        AgentCardResolver resolver = mock(AgentCardResolver.class);
        AgentScopeProperties properties = properties("http://new-runtime/a2a");
        properties.getA2a().setDuplicateRegistrationPolicy(A2aDuplicateRegistrationPolicy.REPLACE);
        when(resolver.getAgentCard("tenant-a/planner"))
                .thenReturn(tenantCardWithVersionUrl("tenant-a", "1.0.0", "http://old-runtime/a2a"));
        AgentScopeAgentCardRegistrar registrar = new AgentScopeAgentCardRegistrar(
                registry,
                new NacosPropertiesFactory(),
                new AgentScopeAgentCardFactory(),
                properties,
                resolver);

        registrar.run(null);

        verify(registry).registerAgent(any(AgentCard.class), any(NacosA2aRegistryProperties.class));
    }

    @Test
    void refreshesSameTenantAgentVersionRegisteredWithSameUrl() {
        NacosA2aRegistry registry = mock(NacosA2aRegistry.class);
        AgentCardResolver resolver = mock(AgentCardResolver.class);
        AgentScopeProperties properties = properties("http://same-runtime/a2a");
        when(resolver.getAgentCard("tenant-a/planner"))
                .thenReturn(tenantCardWithVersionUrl("tenant-a", "1.0.0", "http://same-runtime/a2a"));
        AgentScopeAgentCardRegistrar registrar = new AgentScopeAgentCardRegistrar(
                registry,
                new NacosPropertiesFactory(),
                new AgentScopeAgentCardFactory(),
                properties,
                resolver);

        registrar.run(null);

        verify(registry).registerAgent(any(AgentCard.class), any(NacosA2aRegistryProperties.class));
    }

    @Test
    void deregistersRegisteredEndpointOnDestroyWhenA2aServiceIsAvailable() throws Exception {
        NacosA2aRegistry registry = mock(NacosA2aRegistry.class);
        A2aService a2aService = mock(A2aService.class);
        AgentScopeProperties properties = properties("https://runtime.example:9443/a2a?slot=blue");
        properties.getA2a().setProtocol("https");
        properties.getA2a().setHost("runtime.example");
        properties.getA2a().setPort(9443);
        properties.getA2a().setPath("/a2a");
        properties.getA2a().setQuery("slot=blue");
        properties.getA2a().setSupportTls(true);
        properties.getA2a().setTransport("jsonrpc");
        AgentScopeAgentCardRegistrar registrar = new AgentScopeAgentCardRegistrar(
                registry,
                new NacosPropertiesFactory(),
                new AgentScopeAgentCardFactory(),
                properties,
                null,
                a2aService);
        registrar.run(null);

        registrar.destroy();

        verify(a2aService).deregisterAgentEndpoint(eq("tenant-a/planner"), argThat(endpoint ->
                endpointMatches(endpoint, "JSONRPC", "runtime.example", 9443, "/a2a", true, "1.0.0", "https", "slot=blue")));
    }

    @Test
    void skipsDeregisterWhenNoEndpointWasRegistered() throws Exception {
        NacosA2aRegistry registry = mock(NacosA2aRegistry.class);
        A2aService a2aService = mock(A2aService.class);
        AgentScopeProperties properties = properties("http://runtime.example/a2a");
        properties.getA2a().setRegisterEndpoint(false);
        AgentScopeAgentCardRegistrar registrar = new AgentScopeAgentCardRegistrar(
                registry,
                new NacosPropertiesFactory(),
                new AgentScopeAgentCardFactory(),
                properties,
                null,
                a2aService);
        registrar.run(null);

        registrar.destroy();

        verify(a2aService, never()).deregisterAgentEndpoint(any(), any(AgentEndpoint.class));
    }

    private AgentScopeProperties properties(String url) {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setTenantId("tenant-a");
        properties.getA2a().setAgentName("planner");
        properties.getA2a().setVersion("1.0.0");
        properties.getA2a().setUrl(url);
        return properties;
    }

    private AgentCard tenantCardWithVersionUrl(String tenantId, String version, String url) {
        return A2ATenantMetadata.withTenant(new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("tenant-a/planner")
                .description("Planner")
                .version(version)
                .url(url)
                .additionalInterfaces(List.of(new AgentInterface("JSONRPC", url)))
                .capabilities(A2ATenantMetadataTests.baseCard().capabilities())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(A2ATenantMetadataTests.baseCard().skills())
                .build(), tenantId, Map.of());
    }

    private boolean endpointMatches(
            AgentEndpoint endpoint,
            String transport,
            String address,
            int port,
            String path,
            boolean supportTls,
            String version,
            String protocol,
            String query) {
        return endpoint != null
                && transport.equals(endpoint.getTransport())
                && address.equals(endpoint.getAddress())
                && port == endpoint.getPort()
                && path.equals(endpoint.getPath())
                && supportTls == endpoint.isSupportTls()
                && version.equals(endpoint.getVersion())
                && protocol.equals(endpoint.getProtocol())
                && query.equals(endpoint.getQuery());
    }
}
