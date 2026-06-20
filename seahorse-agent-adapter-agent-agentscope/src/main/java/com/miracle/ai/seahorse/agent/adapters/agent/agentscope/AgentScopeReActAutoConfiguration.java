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

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentConnectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.transport.TransportProperties;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.Model;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistry;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.studio.StudioManager;
import io.agentscope.core.studio.StudioMessageHook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.net.URI;
import java.util.Properties;

@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentAiAdapterAutoConfiguration",
        "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAiModelConfigAutoConfiguration",
        "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelAgentAutoConfiguration"
})
@ConditionalOnClass(ReActAgent.class)
@EnableConfigurationProperties(AgentScopeProperties.class)
@SuppressWarnings("removal")
public class AgentScopeReActAutoConfiguration {

    private static final String PROP_EXECUTOR_ENGINE = "seahorse.agent.executor.engine";
    private static final String PROP_A2A_ENABLED = "seahorse.agentscope.a2a.enabled";
    private static final String PROP_CONFIG_CENTER_ENABLED = "seahorse.agentscope.config-center.enabled";
    private static final String PROP_A2A_NACOS_SERVER = "seahorse.agentscope.a2a.nacos-server";
    private static final String PROP_NACOS_SERVER = "seahorse.agentscope.nacos.server-addr";

    @Bean
    @ConditionalOnMissingBean
    public NacosPropertiesFactory seahorseAgentScopeNacosPropertiesFactory() {
        return new NacosPropertiesFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentScopeAgentCardFactory seahorseAgentScopeAgentCardFactory() {
        return new AgentScopeAgentCardFactory();
    }

    @Bean
    @ConditionalOnClass(StudioMessageHook.class)
    @ConditionalOnProperty(name = PROP_EXECUTOR_ENGINE, havingValue = "agentscope")
    @ConditionalOnProperty(prefix = "seahorse.agentscope.studio", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public StudioMessageHook seahorseAgentScopeStudioMessageHook() {
        return new StudioMessageHook(StudioManager.getClient());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnClass(StudioManager.class)
    @ConditionalOnProperty(name = PROP_EXECUTOR_ENGINE, havingValue = "agentscope")
    @ConditionalOnProperty(prefix = "seahorse.agentscope.studio", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeStudioLifecycle seahorseAgentScopeStudioLifecycle(AgentScopeProperties properties) {
        return new AgentScopeStudioLifecycle(properties);
    }

    @Bean
    @ConditionalOnBean(StreamingChatModelPort.class)
    @ConditionalOnProperty(name = PROP_EXECUTOR_ENGINE, havingValue = "agentscope")
    @ConditionalOnMissingBean(AgentScopeModelFactory.class)
    public AgentScopeModelFactory seahorseAgentScopeModelFactory(
            StreamingChatModelPort modelPort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            AgentScopeProperties properties) {
        AgentScopeModelBridge bridge = new AgentScopeModelBridge(
                modelPort,
                properties.getExecutor().getAgentName(),
                objectMapperProvider.getIfAvailable(ObjectMapper::new));
        return bridge::forRequest;
    }

    @Bean
    @ConditionalOnBean({ToolRegistryPort.class, ToolGatewayPort.class})
    @ConditionalOnProperty(name = PROP_EXECUTOR_ENGINE, havingValue = "agentscope")
    @ConditionalOnMissingBean
    public AgentScopeToolFactory seahorseAgentScopeToolFactory(
            ToolRegistryPort toolRegistry,
            ToolGatewayPort toolGateway,
            ObjectProvider<ToolPolicyPort> toolPolicy,
            ObjectProvider<ToolApprovalRequestRepositoryPort> approvalRequestRepository,
            ObjectProvider<ApprovalRequestQueryPort> approvalRequestQueryPort,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new AgentScopeToolFactory(
                toolRegistry,
                toolGateway,
                toolPolicy.getIfAvailable(ToolPolicyPort::defaults),
                approvalRequestRepository.getIfAvailable(ToolApprovalRequestRepositoryPort::noop),
                approvalRequestQueryPort.getIfAvailable(ApprovalRequestQueryPort::empty),
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = PROP_EXECUTOR_ENGINE, havingValue = "agentscope")
    @ConditionalOnMissingBean
    public AgentScopeAgentClient seahorseAgentScopeAgentClient(
            ObjectProvider<AgentScopeModelFactory> modelFactoryProvider,
            ObjectProvider<Model> modelProvider,
            ObjectProvider<AgentScopeToolFactory> toolFactoryProvider,
            ObjectProvider<AgentScopePromptProvider> promptProvider,
            ObjectProvider<Hook> hookProvider,
            AgentScopeProperties properties) {
        AgentScopeModelFactory modelFactory = modelFactoryProvider.getIfAvailable();
        AgentScopePromptProvider prompts = promptProvider.getIfAvailable(AgentScopePromptProvider::local);
        var hooks = hookProvider.orderedStream().toList();
        if (modelFactory != null) {
            return new ReActAgentScopeAgentClient(modelFactory, properties, toolFactoryProvider.getIfAvailable(),
                    prompts, hooks);
        }
        Model model = modelProvider.getIfAvailable();
        if (model != null) {
            return new ReActAgentScopeAgentClient(model, properties, toolFactoryProvider.getIfAvailable(), prompts,
                    hooks);
        }
        throw new IllegalStateException("AgentScope executor requires a StreamingChatModelPort or an AgentScope Model bean");
    }

    @Bean
    @ConditionalOnBean(AgentScopeAgentClient.class)
    @ConditionalOnProperty(name = PROP_EXECUTOR_ENGINE, havingValue = "agentscope")
    @ConditionalOnMissingBean(ReActExecutorPort.class)
    public AgentScopeReActExecutor seahorseAgentScopeReActExecutor(
            AgentScopeAgentClient client,
            ObjectProvider<ApprovalRequestQueryPort> approvalRequestQueryPort) {
        return new AgentScopeReActExecutor(
                client,
                java.util.concurrent.ForkJoinPool.commonPool(),
                approvalRequestQueryPort.getIfAvailable(ApprovalRequestQueryPort::empty));
    }

    @Bean
    @Conditional(NacosEnabledAndConfiguredCondition.class)
    @ConditionalOnMissingBean
    public AiService seahorseAgentScopeNacosAiService(
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeProperties properties) throws NacosException {
        return AiFactory.createAiService(nacosPropertiesFactory.nacosProperties(properties));
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentCardResolver seahorseAgentScopeNacosAgentCardResolver(AiService aiService) {
        return new NacosAgentCardResolver(aiService);
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public NacosA2aRegistry seahorseAgentScopeNacosA2aRegistry(AiService aiService) {
        return new NacosA2aRegistry(aiService);
    }

    @Bean
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeRemoteAgentInvoker seahorseAgentScopeRemoteAgentInvoker(AgentScopeProperties properties) {
        return new A2aAgentRemoteInvoker(properties.getExecutor().getTimeout());
    }

    @Bean
    @ConditionalOnBean(AgentCardResolver.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(A2AAgentConnectorPort.class)
    public AgentScopeA2AAgentConnector seahorseAgentScopeA2AAgentConnector(
            AgentCardResolver resolver,
            AgentScopeRemoteAgentInvoker invoker) {
        return new AgentScopeA2AAgentConnector(resolver, invoker);
    }

    @Bean
    @ConditionalOnBean(A2AAgentConnectorPort.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2AToolPortAdapter seahorseAgentScopeA2AToolPortAdapter(
            A2AAgentConnectorPort connector) {
        return new AgentScopeA2AToolPortAdapter(connector);
    }

    @Bean
    @ConditionalOnBean(ReActExecutorPort.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2aServerRunner seahorseAgentScopeA2aServerRunner(
            ReActExecutorPort executor,
            AgentScopeProperties properties) {
        return new AgentScopeA2aServerRunner(executor, properties);
    }

    @Bean
    @ConditionalOnBean(AgentScopeA2aServerRunner.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2aServer seahorseAgentScopeA2aServer(
            AgentScopeA2aServerRunner runner,
            AgentScopeProperties properties,
            AgentScopeAgentCardFactory cardFactory) {
        AgentScopeProperties.A2a a2a = properties.getA2a();
        ConfigurableAgentCard card = cardFactory.configurableAgentCard(properties);
        URI endpointUri = endpointUri(a2a);
        TransportProperties transport = TransportProperties.builder(TransportProtocol.JSONRPC.asString())
                .host(firstText(a2a.getHost(), endpointUri == null ? null : endpointUri.getHost()))
                .port(a2aPort(a2a, endpointUri))
                .path(firstText(a2a.getPath(), endpointUri == null ? null : endpointUri.getPath()))
                .supportTls(a2a.isSupportTls() || isHttps(endpointUri))
                .build();
        return AgentScopeA2aServer.builder(runner)
                .agentCard(card)
                .withTransport(transport)
                .build();
    }

    @Bean
    @ConditionalOnBean(AgentScopeA2aServer.class)
    @ConditionalOnProperty(name = PROP_A2A_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeA2aServerController seahorseAgentScopeA2aServerController(AgentScopeA2aServer server) {
        return new AgentScopeA2aServerController(server);
    }

    @Bean
    @ConditionalOnBean(NacosA2aRegistry.class)
    @ConditionalOnProperty(prefix = "seahorse.agentscope.a2a", name = "register-enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public AgentScopeAgentCardRegistrar seahorseAgentScopeAgentCardRegistrar(
            NacosA2aRegistry registry,
            NacosPropertiesFactory nacosPropertiesFactory,
            AgentScopeAgentCardFactory cardFactory,
            AgentScopeProperties properties) {
        return new AgentScopeAgentCardRegistrar(registry, nacosPropertiesFactory, cardFactory, properties);
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = PROP_CONFIG_CENTER_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean
    public NacosPromptListener seahorseAgentScopeNacosPromptListener(AiService aiService) {
        return new NacosPromptListener(aiService);
    }

    @Bean
    @ConditionalOnBean(NacosPromptListener.class)
    @ConditionalOnMissingBean
    public AgentScopePromptConfigCenter seahorseAgentScopePromptConfigCenter(
            NacosPromptListener promptListener,
            AgentScopeProperties properties) {
        return new AgentScopePromptConfigCenter(promptListener, properties);
    }

    @Bean
    @ConditionalOnBean(AiService.class)
    @ConditionalOnProperty(name = PROP_CONFIG_CENTER_ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(AgentSkillRepository.class)
    public AgentSkillRepository seahorseAgentScopeNacosSkillRepository(
            AiService aiService,
            AgentScopeProperties properties) {
        AgentScopeProperties.ConfigCenter configCenter = properties.getConfigCenter();
        Properties skillProperties = new Properties();
        putIfPresent(skillProperties, NacosSkillRepository.SKILL_VERSION_PATH, configCenter.getSkillVersion());
        putIfPresent(skillProperties, NacosSkillRepository.SKILL_LABEL_PATH, configCenter.getSkillLabel());
        return new NacosSkillRepository(
                aiService,
                firstText(configCenter.getSkillNamespace(), properties.getNacos().getNamespace()),
                skillProperties);
    }

    private static final class NacosEnabledAndConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean enabled = Boolean.TRUE.equals(environment.getProperty(PROP_A2A_ENABLED, Boolean.class, false))
                    || Boolean.TRUE.equals(environment.getProperty(PROP_CONFIG_CENTER_ENABLED, Boolean.class, false));
            return enabled && (!isBlank(environment.getProperty(PROP_A2A_NACOS_SERVER))
                    || !isBlank(environment.getProperty(PROP_NACOS_SERVER)));
        }

        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String firstText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? trimToNull(fallback) : trimmed;
    }

    private static void putIfPresent(Properties properties, String key, String value) {
        String safeKey = trimToNull(key);
        String safeValue = trimToNull(value);
        if (safeKey != null && safeValue != null) {
            properties.setProperty(safeKey, safeValue);
        }
    }

    private static Integer a2aPort(AgentScopeProperties.A2a a2a, URI endpointUri) {
        if (a2a.getPort() > 0) {
            return a2a.getPort();
        }
        if (endpointUri == null) {
            return null;
        }
        if (endpointUri.getPort() > 0) {
            return endpointUri.getPort();
        }
        return "https".equalsIgnoreCase(endpointUri.getScheme()) ? 443 : 80;
    }

    private static URI endpointUri(AgentScopeProperties.A2a a2a) {
        String url = trimToNull(a2a.getUrl());
        if (url == null) {
            return null;
        }
        return URI.create(url);
    }

    private static boolean isHttps(URI endpointUri) {
        return endpointUri != null && "https".equalsIgnoreCase(endpointUri.getScheme());
    }
}
