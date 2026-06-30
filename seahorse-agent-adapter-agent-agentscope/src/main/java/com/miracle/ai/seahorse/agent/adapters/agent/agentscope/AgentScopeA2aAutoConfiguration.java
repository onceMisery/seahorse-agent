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

import com.alibaba.nacos.api.ai.AiService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.transport.TransportProperties;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

@AutoConfiguration
@AutoConfigureAfter(
        value = {
                AgentScopeNacosAutoConfiguration.class,
                AgentScopeObservationAutoConfiguration.class
        },
        name = "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelChatAutoConfiguration")
@ConditionalOnClass({ReActAgent.class, AgentScopeA2aServer.class})
@EnableConfigurationProperties(AgentScopeProperties.class)
public class AgentScopeA2aAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public NacosPropertiesFactory seahorseAgentScopeA2aNacosPropertiesFactory() {
        return new NacosPropertiesFactory();
    }

    @Bean
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeAgentCardFactory seahorseAgentScopeAgentCardFactory() {
        return new AgentScopeAgentCardFactory();
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentCardResolver seahorseAgentScopeNacosAgentCardResolver(AiService aiService) {
        return new NacosAgentCardResolver(aiService);
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public NacosA2aRegistry seahorseAgentScopeNacosA2aRegistry(AiService aiService) {
        return new NacosA2aRegistry(aiService);
    }

    @Bean
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeRemoteAgentInvoker seahorseAgentScopeRemoteAgentInvoker(
            AgentScopeProperties properties,
            AgentScopeObservationSupport observationSupport) {
        AgentScopeProperties.A2a a2a = properties.getA2a();
        return new A2aAgentRemoteInvoker(
                properties.getExecutor().getTimeout(),
                a2a.getAuthMode(),
                a2a.getAuthHeaderName(),
                a2a.getSharedSecret(),
                observationSupport);
    }

    @Bean
    @ConditionalOnBean(AgentCardResolver.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(A2AAgentConnectorPort.class)
    public AgentScopeA2AAgentConnector seahorseAgentScopeA2AAgentConnector(
            AgentCardResolver resolver,
            AgentScopeRemoteAgentInvoker invoker,
            AgentScopeProperties properties) {
        return new AgentScopeA2AAgentConnector(resolver, invoker, A2aDiscoveryPolicy.fromProperties(properties));
    }

    @Bean
    @ConditionalOnBean(A2AAgentConnectorPort.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2AToolPortAdapter seahorseAgentScopeA2AToolPortAdapter(
            A2AAgentConnectorPort connector) {
        return new AgentScopeA2AToolPortAdapter(connector);
    }

    @Bean
    @ConditionalOnBean(AgentExternalInvocationInboundPort.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2aServerRunner seahorseAgentScopeA2aServerRunner(
            AgentExternalInvocationInboundPort inboundPort,
            AgentScopeProperties properties) {
        return new AgentScopeA2aServerRunner(inboundPort, properties);
    }

    @Bean
    @ConditionalOnBean(AgentScopeA2aServerRunner.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2aServer seahorseAgentScopeA2aServer(
            AgentScopeA2aServerRunner runner,
            AgentScopeProperties properties,
            AgentScopeAgentCardFactory cardFactory) {
        AgentScopeProperties.A2a a2a = properties.getA2a();
        ConfigurableAgentCard card = cardFactory.configurableAgentCard(properties);
        URI endpointUri = AgentScopeAutoConfigurationSupport.endpointUri(a2a);
        TransportProperties transport = TransportProperties.builder(TransportProtocol.JSONRPC.asString())
                .host(AgentScopeAutoConfigurationSupport.firstText(
                        a2a.getHost(),
                        endpointUri == null ? null : endpointUri.getHost()))
                .port(AgentScopeAutoConfigurationSupport.a2aPort(a2a, endpointUri))
                .path(AgentScopeAutoConfigurationSupport.firstText(
                        a2a.getPath(),
                        endpointUri == null ? null : endpointUri.getPath()))
                .supportTls(a2a.isSupportTls() || AgentScopeAutoConfigurationSupport.isHttps(endpointUri))
                .build();
        return AgentScopeA2aServer.builder(runner)
                .agentCard(card)
                .withTransport(transport)
                .build();
    }

    @Bean
    @ConditionalOnBean(AgentScopeA2aServer.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(AgentScopeA2aServerController.class)
    public AgentScopeA2aServerController seahorseAgentScopeA2aServerController(
            AgentScopeA2aServer server,
            AgentScopeProperties properties,
            AgentScopeObservationSupport observationSupport) {
        return new AgentScopeA2aServerController(
                server,
                properties,
                new A2aRequestAuthenticator(properties),
                observationSupport);
    }

    @Bean
    @ConditionalOnBean(AgentScopeA2aServerController.class)
    @ConditionalOnProperty(name = AgentScopeAutoConfigurationSupport.PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(name = "seahorseAgentScopeA2aRouterFunction")
    public RouterFunction<ServerResponse> seahorseAgentScopeA2aRouterFunction(
            AgentScopeA2aServerController controller,
            AgentScopeProperties properties) {
        String path = AgentScopeAutoConfigurationSupport.firstText(properties.getA2a().getPath(), "/a2a");
        return RouterFunctions.route(RequestPredicates.GET(path),
                        request -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(controller.agentCard()))
                .andRoute(
                        RequestPredicates.POST(path).and(RequestPredicates.contentType(MediaType.APPLICATION_JSON)),
                        request -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(controller.handleJsonRpc(
                                        request.body(String.class),
                                        request.headers().asHttpHeaders().toSingleValueMap())));
    }

    @Bean
    @ConditionalOnBean(NacosA2aRegistry.class)
    @ConditionalOnProperty(prefix = "seahorse.agentscope.a2a", name = "register-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeAgentCardRegistrar seahorseAgentScopeAgentCardRegistrar(
            NacosA2aRegistry registry,
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeAgentCardFactory cardFactory,
            AgentScopeProperties properties,
            ObjectProvider<AgentCardResolver> resolverProvider,
            ObjectProvider<AiService> aiServiceProvider) {
        return new AgentScopeAgentCardRegistrar(
                registry,
                nacosPropertiesFactory,
                cardFactory,
                properties,
                resolverProvider.getIfAvailable(),
                aiServiceProvider.getIfAvailable());
    }
}
