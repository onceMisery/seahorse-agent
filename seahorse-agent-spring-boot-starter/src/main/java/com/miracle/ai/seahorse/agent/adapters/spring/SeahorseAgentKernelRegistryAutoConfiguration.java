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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.registry.KernelAgentDefinitionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelAgentToolBindingManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolCatalogManagementService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentRegistryRepositoryAutoConfiguration.class,
        SeahorseAgentKernelAuthAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelRegistryAutoConfiguration {

    @Bean
    @ConditionalOnBean({AgentDefinitionRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentDefinitionInboundPort.class)
    public KernelAgentDefinitionService seahorseAgentDefinitionInboundPort(
            AgentDefinitionRepositoryPort agentDefinitionRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentDefinitionService(
                agentDefinitionRepositoryPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({AgentDefinitionRepositoryPort.class, AgentRunRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentRunInboundPort.class)
    public KernelAgentRunService seahorseAgentRunInboundPort(
            AgentDefinitionRepositoryPort agentDefinitionRepositoryPort,
            AgentRunRepositoryPort agentRunRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentRunService(
                agentDefinitionRepositoryPort,
                agentRunRepositoryPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({ToolCatalogRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(ToolCatalogManagementInboundPort.class)
    public KernelToolCatalogManagementService seahorseToolCatalogManagementInboundPort(
            ToolCatalogRepositoryPort toolCatalogRepositoryPort,
            CurrentUserPort currentUserPort) {
        return new KernelToolCatalogManagementService(toolCatalogRepositoryPort, currentUserPort);
    }

    @Bean
    @ConditionalOnBean({AgentToolBindingRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentToolBindingManagementInboundPort.class)
    public KernelAgentToolBindingManagementService seahorseAgentToolBindingManagementInboundPort(
            AgentToolBindingRepositoryPort agentToolBindingRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentToolBindingManagementService(
                agentToolBindingRepositoryPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }
}
