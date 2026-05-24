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
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentCheckpointQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunLeaseService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.approval.KernelApprovalManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.DefaultResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.KernelContextPackBuilderService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.KernelContextPackQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelAgentToolBindingManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolCatalogManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolInvocationAuditQueryService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCheckpointQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecisionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
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
    @ConditionalOnBean({AgentRunRepositoryPort.class, AgentRunLeaseRepositoryPort.class})
    @ConditionalOnMissingBean(AgentRunLeaseInboundPort.class)
    public KernelAgentRunLeaseService seahorseAgentRunLeaseInboundPort(
            AgentRunRepositoryPort agentRunRepositoryPort,
            AgentRunLeaseRepositoryPort agentRunLeaseRepositoryPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentRunLeaseService(
                agentRunRepositoryPort,
                agentRunLeaseRepositoryPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({AgentCheckpointRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentCheckpointQueryInboundPort.class)
    public KernelAgentCheckpointQueryService seahorseAgentCheckpointQueryInboundPort(
            AgentCheckpointRepositoryPort agentCheckpointRepositoryPort,
            CurrentUserPort currentUserPort) {
        return new KernelAgentCheckpointQueryService(agentCheckpointRepositoryPort, currentUserPort);
    }

    @Bean
    @ConditionalOnMissingBean(ResourceAccessPolicyPort.class)
    public DefaultResourceAccessPolicyPort seahorseResourceAccessPolicyPort(ObjectProvider<Clock> clockProvider) {
        return new DefaultResourceAccessPolicyPort(clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({ContextPackRepositoryPort.class, ResourceAccessPolicyPort.class})
    @ConditionalOnMissingBean(ContextPackBuilderInboundPort.class)
    public KernelContextPackBuilderService seahorseContextPackBuilderInboundPort(
            ContextPackRepositoryPort contextPackRepositoryPort,
            ResourceAccessPolicyPort resourceAccessPolicyPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelContextPackBuilderService(
                resourceAccessPolicyPort,
                contextPackRepositoryPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({ContextPackRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(ContextPackQueryInboundPort.class)
    public KernelContextPackQueryService seahorseContextPackQueryInboundPort(
            ContextPackRepositoryPort contextPackRepositoryPort,
            CurrentUserPort currentUserPort) {
        return new KernelContextPackQueryService(contextPackRepositoryPort, currentUserPort);
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

    @Bean
    @ConditionalOnBean({ToolInvocationAuditQueryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(ToolInvocationAuditQueryInboundPort.class)
    public KernelToolInvocationAuditQueryService seahorseToolInvocationAuditQueryInboundPort(
            ToolInvocationAuditQueryPort toolInvocationAuditQueryPort,
            CurrentUserPort currentUserPort) {
        return new KernelToolInvocationAuditQueryService(toolInvocationAuditQueryPort, currentUserPort);
    }

    @Bean
    @ConditionalOnBean({ApprovalRequestQueryPort.class, ApprovalRequestDecisionPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(ApprovalManagementInboundPort.class)
    public KernelApprovalManagementService seahorseApprovalManagementInboundPort(
            ApprovalRequestQueryPort approvalRequestQueryPort,
            ApprovalRequestDecisionPort approvalRequestDecisionPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelApprovalManagementService(
                approvalRequestQueryPort,
                approvalRequestDecisionPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }
}
