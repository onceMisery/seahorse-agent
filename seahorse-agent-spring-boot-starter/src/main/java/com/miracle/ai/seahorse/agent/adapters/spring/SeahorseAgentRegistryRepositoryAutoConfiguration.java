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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDurableTaskQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcEvalCandidateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcEvalDatasetRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentDefinitionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentArtifactRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentCatalogQueryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentCheckpointRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentEvalSummaryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentHandoffRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRolloutRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRunEventBufferAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRunLeaseRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRunQueueRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRunRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentToolBindingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentVersionActivationRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConnectorCredentialBindingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConnectorRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcCostUsageRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcEnterprisePilotReadinessRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAuditEventRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAccessDecisionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcContextPackRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentPublishCheckRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentTemplateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcProductionGateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcQuotaPolicyRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcResourceAclRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSandboxRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcToolApprovalRequestRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcToolCatalogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcToolInvocationAuditRepositoryAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTaskQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQueueRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentVersionActivationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.EnterprisePilotReadinessRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalCandidateRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetQueryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentRegistryRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentDefinitionRepositoryPort.class)
    public JdbcAgentDefinitionRepositoryAdapter seahorseJdbcAgentDefinitionRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentDefinitionRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentRunRepositoryPort.class)
    public JdbcAgentRunRepositoryAdapter seahorseJdbcAgentRunRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentRunRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentArtifactRepositoryPort.class)
    public JdbcAgentArtifactRepositoryAdapter seahorseJdbcAgentArtifactRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentArtifactRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentCheckpointRepositoryPort.class)
    public JdbcAgentCheckpointRepositoryAdapter seahorseJdbcAgentCheckpointRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentCheckpointRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ContextPackRepositoryPort.class)
    public JdbcContextPackRepositoryAdapter seahorseJdbcContextPackRepositoryAdapter(DataSource dataSource) {
        return new JdbcContextPackRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AccessDecisionLogPort.class)
    public JdbcAccessDecisionRepositoryAdapter seahorseJdbcAccessDecisionRepositoryAdapter(DataSource dataSource) {
        return new JdbcAccessDecisionRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ResourceAclRepositoryPort.class)
    public JdbcResourceAclRepositoryAdapter seahorseJdbcResourceAclRepositoryAdapter(DataSource dataSource) {
        return new JdbcResourceAclRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentRunLeaseRepositoryPort.class)
    public JdbcAgentRunLeaseRepositoryAdapter seahorseJdbcAgentRunLeaseRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentRunLeaseRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentRunQueueRepositoryPort.class)
    public JdbcAgentRunQueueRepositoryAdapter seahorseJdbcAgentRunQueueRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentRunQueueRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ToolCatalogRepositoryPort.class)
    public JdbcToolCatalogRepositoryAdapter seahorseJdbcToolCatalogRepositoryAdapter(DataSource dataSource) {
        return new JdbcToolCatalogRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentToolBindingRepositoryPort.class)
    public JdbcAgentToolBindingRepositoryAdapter seahorseJdbcAgentToolBindingRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentToolBindingRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ToolInvocationAuditPort.class)
    public JdbcToolInvocationAuditRepositoryAdapter seahorseJdbcToolInvocationAuditRepositoryAdapter(DataSource dataSource) {
        return new JdbcToolInvocationAuditRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ToolApprovalRequestRepositoryPort.class)
    public JdbcToolApprovalRequestRepositoryAdapter seahorseJdbcToolApprovalRequestRepositoryAdapter(DataSource dataSource) {
        return new JdbcToolApprovalRequestRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ConnectorRepositoryPort.class)
    public JdbcConnectorRepositoryAdapter seahorseJdbcConnectorRepositoryAdapter(DataSource dataSource) {
        return new JdbcConnectorRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ConnectorCredentialBindingRepositoryPort.class)
    public JdbcConnectorCredentialBindingRepositoryAdapter seahorseJdbcConnectorCredentialBindingRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcConnectorCredentialBindingRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentTemplateRepositoryPort.class)
    public JdbcAgentTemplateRepositoryAdapter seahorseJdbcAgentTemplateRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentTemplateRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentPublishCheckRepositoryPort.class)
    public JdbcAgentPublishCheckRepositoryAdapter seahorseJdbcAgentPublishCheckRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentPublishCheckRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentVersionActivationRepositoryPort.class)
    public JdbcAgentVersionActivationRepositoryAdapter seahorseJdbcAgentVersionActivationRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcAgentVersionActivationRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentCatalogQueryPort.class)
    public JdbcAgentCatalogQueryAdapter seahorseJdbcAgentCatalogQueryAdapter(DataSource dataSource) {
        return new JdbcAgentCatalogQueryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentHandoffRepositoryPort.class)
    public JdbcAgentHandoffRepositoryAdapter seahorseJdbcAgentHandoffRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentHandoffRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentEvalSummaryRepositoryPort.class)
    public JdbcAgentEvalSummaryRepositoryAdapter seahorseJdbcAgentEvalSummaryRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentEvalSummaryRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentRolloutRepositoryPort.class)
    public JdbcAgentRolloutRepositoryAdapter seahorseJdbcAgentRolloutRepositoryAdapter(DataSource dataSource) {
        return new JdbcAgentRolloutRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(EnterprisePilotReadinessRepositoryPort.class)
    public JdbcEnterprisePilotReadinessRepositoryAdapter seahorseJdbcEnterprisePilotReadinessRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcEnterprisePilotReadinessRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AuditEventRepositoryPort.class)
    public JdbcAuditEventRepositoryAdapter seahorseJdbcAuditEventRepositoryAdapter(DataSource dataSource) {
        return new JdbcAuditEventRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ProductionGateRepositoryPort.class)
    public JdbcProductionGateRepositoryAdapter seahorseJdbcProductionGateRepositoryAdapter(DataSource dataSource) {
        return new JdbcProductionGateRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(QuotaPolicyRepositoryPort.class)
    public JdbcQuotaPolicyRepositoryAdapter seahorseJdbcQuotaPolicyRepositoryAdapter(DataSource dataSource) {
        return new JdbcQuotaPolicyRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(CostUsageRepositoryPort.class)
    public JdbcCostUsageRepositoryAdapter seahorseJdbcCostUsageRepositoryAdapter(DataSource dataSource) {
        return new JdbcCostUsageRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(SandboxSessionRepositoryPort.class)
    public JdbcSandboxRepositoryAdapter seahorseJdbcSandboxRepositoryAdapter(DataSource dataSource) {
        return new JdbcSandboxRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentRunEventBufferPort.class)
    public JdbcAgentRunEventBufferAdapter seahorseJdbcAgentRunEventBufferAdapter(DataSource dataSource,
                                                                                 ObjectMapper objectMapper) {
        return new JdbcAgentRunEventBufferAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(DurableTaskQueuePort.class)
    public JdbcDurableTaskQueueAdapter seahorseJdbcDurableTaskQueueAdapter(DataSource dataSource) {
        return new JdbcDurableTaskQueueAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(EvalCandidateRepositoryPort.class)
    public JdbcEvalCandidateRepositoryAdapter seahorseJdbcEvalCandidateRepositoryAdapter(DataSource dataSource) {
        return new JdbcEvalCandidateRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({EvalDatasetRepositoryPort.class, EvalDatasetQueryPort.class})
    public JdbcEvalDatasetRepositoryAdapter seahorseJdbcEvalDatasetRepositoryAdapter(DataSource dataSource) {
        return new JdbcEvalDatasetRepositoryAdapter(dataSource);
    }
}
