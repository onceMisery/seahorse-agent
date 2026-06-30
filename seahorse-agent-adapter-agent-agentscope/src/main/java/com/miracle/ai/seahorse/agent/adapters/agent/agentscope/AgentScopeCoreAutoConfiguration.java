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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolExecutionPort;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentAiAdapterAutoConfiguration",
        "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAiModelConfigAutoConfiguration",
        "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelAgentAutoConfiguration",
        "com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeObservationAutoConfiguration"
})
@ConditionalOnClass(ReActAgent.class)
@EnableConfigurationProperties(AgentScopeProperties.class)
@SuppressWarnings("removal")
public class AgentScopeCoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(StreamingChatModelPort.class)
    @Conditional(AgentScopeAutoConfigurationSupport.AgentScopeExecutorEnabledCondition.class)
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
    @ConditionalOnBean({ToolRegistryPort.class, GovernedToolExecutionPort.class})
    @Conditional(AgentScopeAutoConfigurationSupport.AgentScopeExecutorEnabledCondition.class)
    @ConditionalOnMissingBean
    public AgentScopeToolFactory seahorseAgentScopeToolFactory(
            ToolRegistryPort toolRegistry,
            GovernedToolExecutionPort toolExecution,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new AgentScopeToolFactory(
                toolRegistry,
                toolExecution,
                objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @Conditional(AgentScopeAutoConfigurationSupport.AgentScopeExecutorEnabledCondition.class)
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
    @Conditional(AgentScopeAutoConfigurationSupport.AgentScopeExecutorEnabledCondition.class)
    @ConditionalOnMissingBean(AgentScopeReActExecutor.class)
    public AgentScopeReActExecutor seahorseAgentScopeReActExecutor(
            AgentScopeAgentClient client,
            ObjectProvider<GovernedToolExecutionPort> toolExecutionPort,
            ObjectProvider<KernelRagTraceRecorder> traceRecorderProvider,
            AgentScopeObservationSupport observationSupport) {
        return new AgentScopeReActExecutor(
                client,
                java.util.concurrent.ForkJoinPool.commonPool(),
                toolExecutionPort.getIfAvailable(),
                observationSupport,
                traceRecorderProvider.getIfAvailable(KernelRagTraceRecorder::noop));
    }
}
