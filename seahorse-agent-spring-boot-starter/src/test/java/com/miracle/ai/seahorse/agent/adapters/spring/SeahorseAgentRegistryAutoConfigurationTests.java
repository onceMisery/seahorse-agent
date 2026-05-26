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
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentCheckpointQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunSnapshotService;
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
import com.miracle.ai.seahorse.agent.kernel.application.agent.cost.KernelCostUsageQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelAgentEvalQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.factory.KernelAgentFactoryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.gate.KernelProductionGateService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.KernelAgentHandoffService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.quota.KernelQuotaDecisionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.quota.KernelQuotaSummaryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.readiness.KernelEnterprisePilotReadinessService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.rollout.KernelAgentRolloutService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sre.KernelSreHealthQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.task.KernelTaskTemplateQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelAgentToolBindingManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolCatalogManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolInvocationAuditQueryService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCheckpointQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentHandoffInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AccessDecisionQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.CostUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiConnectorInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ProductionGateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SreHealthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQueueRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentVersionActivationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecisionPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthReportProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolProviderExposurePolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentRegistryAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeahorseAgentRegistryRepositoryAutoConfiguration.class,
                    SeahorseAgentKernelRegistryAutoConfiguration.class));

    @Test
    void shouldCreatePhaseOneRegistryAndRunStoreBeans() {
        contextRunner.withUserConfiguration(TestInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AgentDefinitionRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentArtifactRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentRunRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentCheckpointRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentRunLeaseRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentRunQueueRepositoryPort.class);
                    assertThat(context).hasSingleBean(ToolCatalogRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentToolBindingRepositoryPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationAuditPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationAuditQueryPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationUsagePort.class);
                    assertThat(context).hasSingleBean(ToolApprovalRequestRepositoryPort.class);
                    assertThat(context).hasSingleBean(ApprovalRequestQueryPort.class);
                    assertThat(context).hasSingleBean(ApprovalRequestDecisionPort.class);
                    assertThat(context).hasSingleBean(ConnectorRepositoryPort.class);
                    assertThat(context).hasSingleBean(ConnectorCredentialBindingRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentTemplateRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentPublishCheckRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentVersionActivationRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentCatalogQueryPort.class);
                    assertThat(context).hasSingleBean(AgentHandoffRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentEvalSummaryRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentRolloutRepositoryPort.class);
                    assertThat(context).hasSingleBean(EnterprisePilotReadinessRepositoryPort.class);
                    assertThat(context).hasSingleBean(QuotaPolicyRepositoryPort.class);
                    assertThat(context).hasSingleBean(CostUsageRepositoryPort.class);
                    assertThat(context).hasSingleBean(ContextPackRepositoryPort.class);
                    assertThat(context).hasSingleBean(AccessDecisionLogPort.class);
                    assertThat(context).hasSingleBean(AccessDecisionQueryPort.class);
                    assertThat(context).hasSingleBean(ResourceAclRepositoryPort.class);
                    assertThat(context).hasSingleBean(ResourceAccessPolicyPort.class);
                    assertThat(context.getBean(ResourceAccessPolicyPort.class))
                            .isInstanceOf(AuditedResourceAccessPolicyPort.class)
                            .isNotInstanceOf(DefaultResourceAccessPolicyPort.class);
                    assertThat(field(context.getBean(ResourceAccessPolicyPort.class), "delegate"))
                            .isInstanceOf(AclBackedResourceAccessPolicyPort.class);
                    assertThat(field(field(context.getBean(ResourceAccessPolicyPort.class), "delegate"), "delegate"))
                            .isInstanceOf(DefaultResourceAccessPolicyPort.class);
                    assertThat(field(context.getBean(ResourceAccessPolicyPort.class), "auditLedger"))
                            .isSameAs(context.getBean(KernelAuditLedgerService.class));
                    assertThat(context).hasSingleBean(AgentDefinitionInboundPort.class);
                    assertThat(context).hasSingleBean(AgentArtifactQueryInboundPort.class);
                    assertThat(context).hasSingleBean(AgentRunInboundPort.class);
                    assertThat(context).hasSingleBean(AgentRunLeaseInboundPort.class);
                    assertThat(context).hasSingleBean(AgentRunSnapshotInboundPort.class);
                    assertThat(context).hasSingleBean(AgentCheckpointQueryInboundPort.class);
                    assertThat(context).hasSingleBean(ContextPackBuilderInboundPort.class);
                    assertThat(context).hasSingleBean(ContextPackQueryInboundPort.class);
                    assertThat(context).hasSingleBean(AccessDecisionQueryInboundPort.class);
                    assertThat(context).hasSingleBean(MeshPolicyPort.class);
                    assertThat(context).hasSingleBean(AgentHandoffInboundPort.class);
                    assertThat(context).hasSingleBean(AgentEvalInboundPort.class);
                    assertThat(context).hasSingleBean(QuotaManagementInboundPort.class);
                    assertThat(context).hasSingleBean(QuotaSummaryInboundPort.class);
                    assertThat(context).hasSingleBean(TaskTemplateQueryInboundPort.class);
                    assertThat(context).hasSingleBean(CostUsageInboundPort.class);
                    assertThat(context).hasSingleBean(SreHealthInboundPort.class);
                    assertThat(context).hasSingleBean(SreHealthReportProviderPort.class);
                    assertThat(context).hasSingleBean(AgentRolloutInboundPort.class);
                    assertThat(context).hasSingleBean(EnterprisePilotReadinessInboundPort.class);
                    assertThat(context).hasSingleBean(ReadinessAgentDefinitionEvidencePort.class);
                    assertThat(context).hasSingleBean(ReadinessToolRiskEvidencePort.class);
                    assertThat(context).hasSingleBean(ReadinessResourceAclEvidencePort.class);
                    assertThat(context).hasSingleBean(ReadinessEvalEvidencePort.class);
                    assertThat(context).hasSingleBean(ReadinessQuotaEvidencePort.class);
                    assertThat(context).hasSingleBean(ReadinessAuditEvidencePort.class);
                    assertThat(context).hasSingleBean(ReadinessRollbackEvidencePort.class);
                    assertThat(context).hasSingleBean(ResourceAclManagementInboundPort.class);
                    assertThat(context).hasSingleBean(OpenApiConnectorInboundPort.class);
                    assertThat(context).hasSingleBean(AgentFactoryInboundPort.class);
                    assertThat(context).hasSingleBean(ToolCatalogManagementInboundPort.class);
                    assertThat(context).hasSingleBean(AgentToolBindingManagementInboundPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationAuditQueryInboundPort.class);
                    assertThat(context).hasSingleBean(ApprovalManagementInboundPort.class);
                    assertThat(context).hasSingleBean(KernelAgentDefinitionService.class);
                    assertThat(context).hasSingleBean(KernelAgentArtifactQueryService.class);
                    assertThat(context).hasSingleBean(KernelAgentRunService.class);
                    assertThat(context).hasSingleBean(KernelAgentRunSnapshotService.class);
                    assertThat(context).hasSingleBean(KernelAgentCheckpointQueryService.class);
                    assertThat(context).hasSingleBean(KernelContextPackBuilderService.class);
                    assertThat(context).hasSingleBean(KernelContextPackQueryService.class);
                    assertThat(context).hasSingleBean(KernelAccessDecisionQueryService.class);
                    assertThat(context).hasSingleBean(KernelResourceAclManagementService.class);
                    assertThat(field(context.getBean(KernelResourceAclManagementService.class), "auditLedger"))
                            .isSameAs(context.getBean(KernelAuditLedgerService.class));
                    assertThat(context).hasSingleBean(KernelOpenApiConnectorImportService.class);
                    assertThat(context).hasSingleBean(AuditEventRepositoryPort.class);
                    assertThat(context).hasSingleBean(KernelAuditLedgerService.class);
                    assertThat(field(context.getBean(KernelOpenApiConnectorImportService.class),
                            "credentialBindingRepository"))
                            .isSameAs(context.getBean(ConnectorCredentialBindingRepositoryPort.class));
                    assertThat(field(context.getBean(KernelOpenApiConnectorImportService.class), "auditLedger"))
                            .isSameAs(context.getBean(KernelAuditLedgerService.class));
                    assertThat(context).hasSingleBean(KernelAgentFactoryService.class);
                    assertThat(context).hasSingleBean(KernelAgentHandoffService.class);
                    assertThat(context).hasSingleBean(KernelAgentEvalQueryService.class);
                    assertThat(context).hasSingleBean(KernelQuotaDecisionService.class);
                    assertThat(context).hasSingleBean(KernelQuotaSummaryService.class);
                    assertThat(context).hasSingleBean(KernelTaskTemplateQueryService.class);
                    assertThat(context).hasSingleBean(KernelCostUsageQueryService.class);
                    assertThat(context).hasSingleBean(KernelSreHealthQueryService.class);
                    assertThat(context).hasSingleBean(KernelProductionGateService.class);
                    assertThat(context).hasSingleBean(KernelAgentRolloutService.class);
                    assertThat(context).hasSingleBean(KernelEnterprisePilotReadinessService.class);
                    assertThat(context).hasSingleBean(ProductionGateInboundPort.class);
                    assertThat(context).hasSingleBean(ProductionGateRepositoryPort.class);
                    assertThat(field(context.getBean(KernelProductionGateService.class), "evalSummaryRepository"))
                            .isSameAs(context.getBean(AgentEvalSummaryRepositoryPort.class));
                    assertThat(field(context.getBean(KernelProductionGateService.class), "quotaPolicyRepository"))
                            .isSameAs(context.getBean(QuotaPolicyRepositoryPort.class));
                    assertThat(field(context.getBean(KernelProductionGateService.class), "sreHealthReportProvider"))
                            .isSameAs(context.getBean(SreHealthReportProviderPort.class));
                    assertThat(field(context.getBean(KernelAgentHandoffService.class), "handoffRepository"))
                            .isSameAs(context.getBean(AgentHandoffRepositoryPort.class));
                    assertThat(field(context.getBean(KernelAgentHandoffService.class), "auditLedger"))
                            .isSameAs(context.getBean(KernelAuditLedgerService.class));
                    assertThat(field(context.getBean(KernelAgentFactoryService.class), "definitionRepository"))
                            .isSameAs(context.getBean(AgentDefinitionRepositoryPort.class));
                    assertThat(field(context.getBean(KernelAgentFactoryService.class), "activationRepository"))
                            .isSameAs(context.getBean(AgentVersionActivationRepositoryPort.class));
                    assertThat(field(context.getBean(KernelAgentFactoryService.class), "catalogQueryPort"))
                            .isSameAs(context.getBean(AgentCatalogQueryPort.class));
                    assertThat(field(context.getBean(KernelAgentRolloutService.class), "rolloutRepository"))
                            .isSameAs(context.getBean(AgentRolloutRepositoryPort.class));
                    assertThat(field(context.getBean(KernelAgentRolloutService.class), "productionGateRepository"))
                            .isSameAs(context.getBean(ProductionGateRepositoryPort.class));
                    assertThat(field(context.getBean(KernelAgentRolloutService.class), "agentFactoryPort"))
                            .isSameAs(context.getBean(AgentFactoryInboundPort.class));
                    assertThat(field(context.getBean(KernelEnterprisePilotReadinessService.class), "repository"))
                            .isSameAs(context.getBean(EnterprisePilotReadinessRepositoryPort.class));
                    assertThat(field(context.getBean(KernelEnterprisePilotReadinessService.class),
                            "agentDefinitionEvidencePort"))
                            .isSameAs(context.getBean(ReadinessAgentDefinitionEvidencePort.class));
                    assertThat(context).hasSingleBean(KernelToolCatalogManagementService.class);
                    assertThat(context).hasSingleBean(KernelAgentToolBindingManagementService.class);
                    assertThat(context).hasSingleBean(KernelToolInvocationAuditQueryService.class);
                    assertThat(context).hasSingleBean(KernelApprovalManagementService.class);
                    assertThat(context).hasSingleBean(ToolProviderExposurePolicyPort.class);
                    assertThat(field(context.getBean(KernelToolCatalogManagementService.class),
                            "providerExposurePolicy"))
                            .isSameAs(context.getBean(ToolProviderExposurePolicyPort.class));
                    assertThat(field(context.getBean(KernelAgentToolBindingManagementService.class),
                            "providerExposurePolicy"))
                            .isSameAs(context.getBean(ToolProviderExposurePolicyPort.class));
                    assertThat(field(context.getBean(KernelAgentFactoryService.class), "providerExposurePolicy"))
                            .isSameAs(context.getBean(ToolProviderExposurePolicyPort.class));
                    assertThat(field(context.getBean(KernelAgentArtifactQueryService.class), "artifactRepository"))
                            .isSameAs(context.getBean(AgentArtifactRepositoryPort.class));
                    assertThat(field(context.getBean(KernelAgentRunSnapshotService.class), "artifactRepository"))
                            .isSameAs(context.getBean(AgentArtifactRepositoryPort.class));
                });
    }

    @Test
    void shouldKeepCustomResourceAccessPolicyPortReplaceable() {
        contextRunner.withUserConfiguration(
                        TestInfrastructureConfiguration.class,
                        CustomResourceAccessPolicyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ResourceAccessPolicyPort.class);
                    assertThat(context.getBean(ResourceAccessPolicyPort.class))
                            .isSameAs(context.getBean("customResourceAccessPolicyPort"));
                });
    }

    private static Object field(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot read field " + fieldName + " from " + target.getClass(), ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:agent-registry-autoconfig-" + System.nanoTime()
                            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
        }

        @Bean
        CurrentUserPort currentUserPort() {
            return () -> Optional.of(new CurrentUser("admin-1", "admin", "admin", null));
        }

        @Bean
        OpenApiSpecParserPort openApiSpecParserPort() {
            return request -> new com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecDocument(
                    "Test API",
                    "Test API",
                    java.util.List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomResourceAccessPolicyConfiguration {

        @Bean
        ResourceAccessPolicyPort customResourceAccessPolicyPort() {
            return ResourceAccessPolicyPort.denyAll();
        }
    }
}
