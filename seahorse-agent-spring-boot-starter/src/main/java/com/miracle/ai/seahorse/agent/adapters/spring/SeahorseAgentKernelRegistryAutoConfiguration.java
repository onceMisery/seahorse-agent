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
import com.miracle.ai.seahorse.agent.kernel.application.agent.artifact.KernelAgentArtifactQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.artifact.KernelAgentArtifactUpdateService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentCheckpointQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunLeaseService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunSnapshotService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunWorkflowService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.approval.KernelApprovalManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.connector.KernelOpenApiConnectorImportService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.AclBackedResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.AuditedResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.DefaultResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.KernelAccessDecisionQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.KernelContextPackBuilderService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.KernelContextPackQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.context.KernelResourceAclManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.cost.KernelAgentRunCostSummaryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.cost.KernelCostUsageQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelAgentEvalQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.factory.KernelAgentFactoryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.gate.KernelProductionGateService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.DefaultMeshPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.KernelAgentHandoffService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.quota.KernelQuotaDecisionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.quota.KernelQuotaSummaryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.readiness.KernelEnterprisePilotReadinessService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.rollout.KernelAgentRolloutService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.DefaultSandboxPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.KernelSandboxRuntimeService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sre.KernelSreHealthQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.KernelAgentSkillBindingService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.KernelAgentSkillManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.task.KernelTaskTemplateQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelAgentToolBindingManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolCatalogManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolInvocationAuditQueryService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactUpdateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCheckpointQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentHandoffInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunCostSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflowInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillBindingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AccessDecisionQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.CostUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiConnectorInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ProductionGateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SreHealthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentVersionActivationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecisionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.EnterprisePilotReadinessRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.MeshPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAgentDefinitionEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessAuditEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessEvalEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessQuotaEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessResourceAclEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessRollbackEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ReadinessToolRiskEvidencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthContributorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthReportProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolProviderExposurePolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

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
    @ConditionalOnBean({AgentArtifactRepositoryPort.class, AgentRunRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentArtifactQueryInboundPort.class)
    public KernelAgentArtifactQueryService seahorseAgentArtifactQueryInboundPort(
            AgentArtifactRepositoryPort agentArtifactRepositoryPort,
            AgentRunRepositoryPort agentRunRepositoryPort,
            CurrentUserPort currentUserPort) {
        return new KernelAgentArtifactQueryService(
                agentArtifactRepositoryPort,
                agentRunRepositoryPort,
                currentUserPort);
    }

    @Bean
    @ConditionalOnBean({AgentArtifactRepositoryPort.class, ObjectStoragePort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentArtifactUpdateInboundPort.class)
    public KernelAgentArtifactUpdateService seahorseAgentArtifactUpdateInboundPort(
            AgentArtifactRepositoryPort agentArtifactRepositoryPort,
            ObjectStoragePort objectStoragePort,
            CurrentUserPort currentUserPort) {
        return new KernelAgentArtifactUpdateService(
                agentArtifactRepositoryPort,
                objectStoragePort,
                currentUserPort);
    }

    @Bean
    @ConditionalOnBean({AgentRunRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentRunSnapshotInboundPort.class)
    public KernelAgentRunSnapshotService seahorseAgentRunSnapshotInboundPort(
            AgentRunRepositoryPort agentRunRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<AgentCheckpointRepositoryPort> agentCheckpointRepositoryPort,
            ObjectProvider<ContextPackRepositoryPort> contextPackRepositoryPort,
            ObjectProvider<ApprovalRequestQueryPort> approvalRequestQueryPort,
            ObjectProvider<AgentArtifactRepositoryPort> agentArtifactRepositoryPort) {
        return new KernelAgentRunSnapshotService(
                agentRunRepositoryPort,
                agentCheckpointRepositoryPort.getIfAvailable(),
                contextPackRepositoryPort.getIfAvailable(),
                approvalRequestQueryPort.getIfAvailable(),
                agentArtifactRepositoryPort.getIfAvailable(),
                currentUserPort);
    }

    @Bean
    @ConditionalOnBean({AgentRunRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentRunWorkflowInboundPort.class)
    public KernelAgentRunWorkflowService seahorseAgentRunWorkflowInboundPort(
            AgentRunRepositoryPort agentRunRepositoryPort,
            CurrentUserPort currentUserPort) {
        return new KernelAgentRunWorkflowService(agentRunRepositoryPort, currentUserPort);
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
    public ResourceAccessPolicyPort seahorseResourceAccessPolicyPort(ObjectProvider<Clock> clockProvider,
                                                                     ObjectProvider<ResourceAclRepositoryPort> aclRepositoryPort,
                                                                     ObjectProvider<AccessDecisionLogPort> logPort,
                                                                     ObjectProvider<KernelAuditLedgerService> auditLedgerService) {
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        ResourceAccessPolicyPort policy = new DefaultResourceAccessPolicyPort(clock);
        ResourceAclRepositoryPort aclRepository = aclRepositoryPort.getIfAvailable();
        if (aclRepository != null) {
            policy = new AclBackedResourceAccessPolicyPort(aclRepository, policy, clock);
        }
        AccessDecisionLogPort auditLog = logPort.getIfAvailable();
        if (auditLog == null) {
            return policy;
        }
        return new AuditedResourceAccessPolicyPort(policy, auditLog, auditLedgerService.getIfAvailable());
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
    @ConditionalOnBean({AccessDecisionQueryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AccessDecisionQueryInboundPort.class)
    public KernelAccessDecisionQueryService seahorseAccessDecisionQueryInboundPort(
            AccessDecisionQueryPort accessDecisionQueryPort,
            CurrentUserPort currentUserPort) {
        return new KernelAccessDecisionQueryService(accessDecisionQueryPort, currentUserPort);
    }

    @Bean
    @ConditionalOnMissingBean(ToolProviderExposurePolicyPort.class)
    public ToolProviderExposurePolicyPort seahorseToolProviderExposurePolicyPort() {
        return ToolProviderExposurePolicyPort.consumerWebDefaults();
    }

    @Bean
    @ConditionalOnBean({ResourceAclRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(ResourceAclManagementInboundPort.class)
    public KernelResourceAclManagementService seahorseResourceAclManagementInboundPort(
            ResourceAclRepositoryPort resourceAclRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<KernelAuditLedgerService> auditLedgerService,
            ObjectProvider<Clock> clockProvider) {
        return new KernelResourceAclManagementService(
                resourceAclRepositoryPort,
                currentUserPort,
                auditLedgerService.getIfAvailable(),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({ToolCatalogRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(ToolCatalogManagementInboundPort.class)
    public KernelToolCatalogManagementService seahorseToolCatalogManagementInboundPort(
            ToolCatalogRepositoryPort toolCatalogRepositoryPort,
            CurrentUserPort currentUserPort,
            ToolProviderExposurePolicyPort toolProviderExposurePolicyPort) {
        return new KernelToolCatalogManagementService(
                toolCatalogRepositoryPort,
                currentUserPort,
                toolProviderExposurePolicyPort);
    }

    @Bean
    @ConditionalOnBean({AgentToolBindingRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentToolBindingManagementInboundPort.class)
    public KernelAgentToolBindingManagementService seahorseAgentToolBindingManagementInboundPort(
            AgentToolBindingRepositoryPort agentToolBindingRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider,
            ObjectProvider<ToolCatalogRepositoryPort> toolCatalogRepositoryPort,
            ToolProviderExposurePolicyPort toolProviderExposurePolicyPort) {
        return new KernelAgentToolBindingManagementService(
                agentToolBindingRepositoryPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC),
                toolCatalogRepositoryPort.getIfAvailable(),
                toolProviderExposurePolicyPort);
    }

    @Bean
    @ConditionalOnBean({AgentSkillRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentSkillManagementInboundPort.class)
    public KernelAgentSkillManagementService seahorseAgentSkillManagementInboundPort(
            AgentSkillRepositoryPort agentSkillRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentSkillManagementService(
                agentSkillRepositoryPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({AgentSkillRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentSkillBindingInboundPort.class)
    public KernelAgentSkillBindingService seahorseAgentSkillBindingInboundPort(
            AgentSkillRepositoryPort agentSkillRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentSkillBindingService(
                agentSkillRepositoryPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean(KernelAgentSkillManagementService.class)
    @ConditionalOnMissingBean
    public BuiltInAgentSkillRegistrar seahorseBuiltInAgentSkillRegistrar(
            KernelAgentSkillManagementService skillManagementService) {
        return new BuiltInAgentSkillRegistrar(skillManagementService);
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

    @Bean
    @ConditionalOnBean({
            ConnectorRepositoryPort.class,
            OpenApiSpecParserPort.class,
            ToolCatalogRepositoryPort.class,
            CurrentUserPort.class
    })
    @ConditionalOnMissingBean(OpenApiConnectorInboundPort.class)
    public KernelOpenApiConnectorImportService seahorseOpenApiConnectorInboundPort(
            ConnectorRepositoryPort connectorRepositoryPort,
            OpenApiSpecParserPort openApiSpecParserPort,
            ToolCatalogRepositoryPort toolCatalogRepositoryPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<ConnectorCredentialBindingRepositoryPort> connectorCredentialBindingRepositoryPort,
            ObjectProvider<KernelAuditLedgerService> auditLedgerService,
            ObjectProvider<Clock> clockProvider) {
        return new KernelOpenApiConnectorImportService(
                connectorRepositoryPort,
                openApiSpecParserPort,
                toolCatalogRepositoryPort,
                connectorCredentialBindingRepositoryPort.getIfAvailable(),
                auditLedgerService.getIfAvailable(),
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({
            AgentTemplateRepositoryPort.class,
            AgentDefinitionInboundPort.class,
            AgentPublishCheckRepositoryPort.class,
            ToolCatalogRepositoryPort.class,
            AgentDefinitionRepositoryPort.class,
            AgentVersionActivationRepositoryPort.class,
            AgentCatalogQueryPort.class
    })
    @ConditionalOnMissingBean(AgentFactoryInboundPort.class)
    public KernelAgentFactoryService seahorseAgentFactoryInboundPort(
            AgentTemplateRepositoryPort agentTemplateRepositoryPort,
            AgentDefinitionInboundPort agentDefinitionInboundPort,
            AgentPublishCheckRepositoryPort agentPublishCheckRepositoryPort,
            ToolCatalogRepositoryPort toolCatalogRepositoryPort,
            AgentDefinitionRepositoryPort agentDefinitionRepositoryPort,
            AgentVersionActivationRepositoryPort agentVersionActivationRepositoryPort,
            AgentCatalogQueryPort agentCatalogQueryPort,
            ToolProviderExposurePolicyPort toolProviderExposurePolicyPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentFactoryService(
                agentTemplateRepositoryPort,
                agentDefinitionInboundPort,
                agentPublishCheckRepositoryPort,
                toolCatalogRepositoryPort,
                agentDefinitionRepositoryPort,
                agentVersionActivationRepositoryPort,
                agentCatalogQueryPort,
                toolProviderExposurePolicyPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(MeshPolicyPort.class)
    public DefaultMeshPolicyPort seahorseMeshPolicyPort() {
        return new DefaultMeshPolicyPort();
    }

    @Bean
    @ConditionalOnBean({AgentHandoffRepositoryPort.class, AgentRunInboundPort.class, MeshPolicyPort.class})
    @ConditionalOnMissingBean(AgentHandoffInboundPort.class)
    public KernelAgentHandoffService seahorseAgentHandoffInboundPort(
            AgentHandoffRepositoryPort agentHandoffRepositoryPort,
            AgentRunInboundPort agentRunInboundPort,
            MeshPolicyPort meshPolicyPort,
            ObjectProvider<KernelAuditLedgerService> auditLedgerService,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentHandoffService(
                agentHandoffRepositoryPort,
                agentRunInboundPort,
                meshPolicyPort,
                auditLedgerService.getIfAvailable(),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean(AgentEvalSummaryRepositoryPort.class)
    @ConditionalOnMissingBean(AgentEvalInboundPort.class)
    public KernelAgentEvalQueryService seahorseAgentEvalInboundPort(
            AgentEvalSummaryRepositoryPort agentEvalSummaryRepositoryPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentEvalQueryService(
                agentEvalSummaryRepositoryPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean(QuotaPolicyRepositoryPort.class)
    @ConditionalOnMissingBean(QuotaManagementInboundPort.class)
    public KernelQuotaDecisionService seahorseQuotaManagementInboundPort(
            QuotaPolicyRepositoryPort quotaPolicyRepositoryPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelQuotaDecisionService(
                quotaPolicyRepositoryPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(QuotaSummaryInboundPort.class)
    public KernelQuotaSummaryService seahorseQuotaSummaryInboundPort(
            ObjectProvider<QuotaPolicyRepositoryPort> quotaPolicyRepositoryPort,
            ObjectProvider<CostUsageRepositoryPort> costUsageRepositoryPort) {
        return new KernelQuotaSummaryService(
                quotaPolicyRepositoryPort.getIfAvailable(),
                costUsageRepositoryPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(TaskTemplateQueryInboundPort.class)
    public KernelTaskTemplateQueryService seahorseTaskTemplateQueryInboundPort() {
        return new KernelTaskTemplateQueryService();
    }

    @Bean
    @ConditionalOnBean(CostUsageRepositoryPort.class)
    @ConditionalOnMissingBean(CostUsageInboundPort.class)
    public KernelCostUsageQueryService seahorseCostUsageInboundPort(
            CostUsageRepositoryPort costUsageRepositoryPort) {
        return new KernelCostUsageQueryService(costUsageRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({AgentRunRepositoryPort.class, CostUsageRepositoryPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(AgentRunCostSummaryInboundPort.class)
    public KernelAgentRunCostSummaryService seahorseAgentRunCostSummaryInboundPort(
            AgentRunRepositoryPort agentRunRepositoryPort,
            CostUsageRepositoryPort costUsageRepositoryPort,
            CurrentUserPort currentUserPort) {
        return new KernelAgentRunCostSummaryService(
                agentRunRepositoryPort,
                costUsageRepositoryPort,
                currentUserPort);
    }

    @Bean
    @ConditionalOnMissingBean(SreHealthInboundPort.class)
    public KernelSreHealthQueryService seahorseSreHealthInboundPort(
            ObjectProvider<SreHealthContributorPort> sreHealthContributorPorts,
            ObjectProvider<Clock> clockProvider) {
        return new KernelSreHealthQueryService(
                sreHealthContributorPorts.orderedStream().toList(),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(AuditRedactionPolicy.class)
    public AuditRedactionPolicy seahorseAuditRedactionPolicy() {
        return new AuditRedactionPolicy();
    }

    @Bean
    @ConditionalOnBean({AuditEventRepositoryPort.class, AuditRedactionPolicy.class})
    @ConditionalOnMissingBean(AuditQueryInboundPort.class)
    public KernelAuditLedgerService seahorseAuditQueryInboundPort(
            AuditEventRepositoryPort auditEventRepositoryPort,
            AuditRedactionPolicy auditRedactionPolicy) {
        return new KernelAuditLedgerService(
                auditEventRepositoryPort,
                auditRedactionPolicy,
                AuditWriteFailurePolicy.FAIL_CLOSED);
    }

    @Bean
    @ConditionalOnBean({ProductionGateRepositoryPort.class, AgentDefinitionRepositoryPort.class})
    @ConditionalOnMissingBean(ProductionGateInboundPort.class)
    public KernelProductionGateService seahorseProductionGateInboundPort(
            ProductionGateRepositoryPort productionGateRepositoryPort,
            AgentDefinitionRepositoryPort agentDefinitionRepositoryPort,
            ObjectProvider<AgentEvalSummaryRepositoryPort> agentEvalSummaryRepositoryPort,
            ObjectProvider<QuotaPolicyRepositoryPort> quotaPolicyRepositoryPort,
            ObjectProvider<SreHealthReportProviderPort> sreHealthReportProviderPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelProductionGateService(
                productionGateRepositoryPort,
                agentDefinitionRepositoryPort,
                agentEvalSummaryRepositoryPort.getIfAvailable(),
                quotaPolicyRepositoryPort.getIfAvailable(),
                sreHealthReportProviderPort.getIfAvailable(),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({
            AgentRolloutRepositoryPort.class,
            ProductionGateRepositoryPort.class,
            AgentFactoryInboundPort.class
    })
    @ConditionalOnMissingBean(AgentRolloutInboundPort.class)
    public KernelAgentRolloutService seahorseAgentRolloutInboundPort(
            AgentRolloutRepositoryPort agentRolloutRepositoryPort,
            ProductionGateRepositoryPort productionGateRepositoryPort,
            AgentFactoryInboundPort agentFactoryInboundPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentRolloutService(
                agentRolloutRepositoryPort,
                productionGateRepositoryPort,
                agentFactoryInboundPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean(AgentDefinitionRepositoryPort.class)
    @ConditionalOnMissingBean(ReadinessAgentDefinitionEvidencePort.class)
    public ReadinessAgentDefinitionEvidencePort seahorseReadinessAgentDefinitionEvidencePort(
            AgentDefinitionRepositoryPort agentDefinitionRepositoryPort) {
        return ConservativeReadinessEvidenceAdapters.agentDefinition(agentDefinitionRepositoryPort);
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessToolRiskEvidencePort.class)
    public ReadinessToolRiskEvidencePort seahorseReadinessToolRiskEvidencePort() {
        return ConservativeReadinessEvidenceAdapters.toolRisk();
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessResourceAclEvidencePort.class)
    public ReadinessResourceAclEvidencePort seahorseReadinessResourceAclEvidencePort() {
        return ConservativeReadinessEvidenceAdapters.resourceAcl();
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessEvalEvidencePort.class)
    public ReadinessEvalEvidencePort seahorseReadinessEvalEvidencePort() {
        return ConservativeReadinessEvidenceAdapters.eval();
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessQuotaEvidencePort.class)
    public ReadinessQuotaEvidencePort seahorseReadinessQuotaEvidencePort() {
        return ConservativeReadinessEvidenceAdapters.quota();
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessAuditEvidencePort.class)
    public ReadinessAuditEvidencePort seahorseReadinessAuditEvidencePort() {
        return ConservativeReadinessEvidenceAdapters.audit();
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessRollbackEvidencePort.class)
    public ReadinessRollbackEvidencePort seahorseReadinessRollbackEvidencePort() {
        return ConservativeReadinessEvidenceAdapters.rollback();
    }

    @Bean
    @ConditionalOnBean({
            EnterprisePilotReadinessRepositoryPort.class,
            ReadinessAgentDefinitionEvidencePort.class,
            ReadinessToolRiskEvidencePort.class,
            ReadinessResourceAclEvidencePort.class,
            ReadinessEvalEvidencePort.class,
            ReadinessQuotaEvidencePort.class,
            ReadinessAuditEvidencePort.class,
            ReadinessRollbackEvidencePort.class
    })
    @ConditionalOnMissingBean(EnterprisePilotReadinessInboundPort.class)
    public KernelEnterprisePilotReadinessService seahorseEnterprisePilotReadinessInboundPort(
            EnterprisePilotReadinessRepositoryPort enterprisePilotReadinessRepositoryPort,
            ReadinessAgentDefinitionEvidencePort readinessAgentDefinitionEvidencePort,
            ReadinessToolRiskEvidencePort readinessToolRiskEvidencePort,
            ReadinessResourceAclEvidencePort readinessResourceAclEvidencePort,
            ReadinessEvalEvidencePort readinessEvalEvidencePort,
            ReadinessQuotaEvidencePort readinessQuotaEvidencePort,
            ReadinessAuditEvidencePort readinessAuditEvidencePort,
            ReadinessRollbackEvidencePort readinessRollbackEvidencePort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelEnterprisePilotReadinessService(
                enterprisePilotReadinessRepositoryPort,
                readinessAgentDefinitionEvidencePort,
                readinessToolRiskEvidencePort,
                readinessResourceAclEvidencePort,
                readinessEvalEvidencePort,
                readinessQuotaEvidencePort,
                readinessAuditEvidencePort,
                readinessRollbackEvidencePort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(SandboxPolicyPort.class)
    public SandboxPolicyPort seahorseSandboxPolicyPort() {
        return new DefaultSandboxPolicyPort(SandboxNetworkPolicy.DENY_ALL, List.of());
    }

    @Bean
    @ConditionalOnMissingBean(SandboxRuntimePort.class)
    public SandboxRuntimePort seahorseSandboxRuntimePort() {
        return SandboxRuntimePort.unsupported();
    }

    @Bean
    @ConditionalOnBean({
            SandboxPolicyPort.class,
            SandboxRuntimePort.class,
            SandboxArtifactPort.class,
            SandboxSessionRepositoryPort.class,
            SandboxExecutionRepositoryPort.class,
            SandboxArtifactQueryPort.class
    })
    @ConditionalOnMissingBean(SandboxRuntimeInboundPort.class)
    public KernelSandboxRuntimeService seahorseSandboxRuntimeInboundPort(
            SandboxPolicyPort sandboxPolicyPort,
            SandboxRuntimePort sandboxRuntimePort,
            SandboxArtifactPort sandboxArtifactPort,
            SandboxSessionRepositoryPort sandboxSessionRepositoryPort,
            SandboxExecutionRepositoryPort sandboxExecutionRepositoryPort,
            SandboxArtifactQueryPort sandboxArtifactQueryPort,
            ObjectProvider<KernelAuditLedgerService> auditLedgerService,
            ObjectProvider<Clock> clockProvider) {
        return new KernelSandboxRuntimeService(
                sandboxPolicyPort,
                sandboxRuntimePort,
                sandboxArtifactPort,
                sandboxSessionRepositoryPort,
                sandboxExecutionRepositoryPort,
                sandboxArtifactQueryPort,
                auditLedgerService.getIfAvailable(),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }
}
