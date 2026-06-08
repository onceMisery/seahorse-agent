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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAdminRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentPublishReviewRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentRatingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentSubscriptionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAuditLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRevenueShareRepositoryAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAdminTenantService;
import com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAuditLogService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.KernelAgentMarketplaceService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.RevenueService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeBaseVersionService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeBasePermissionService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeBaseShareService;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.AdminRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.AuditLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentPublishReviewRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentRatingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentSubscriptionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.RevenueShareRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePermissionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseShareRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseVersionRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Auto-configuration for Marketplace and Admin modules (Sprint 5-6 Modules 06, 07, 10).
 *
 * <p>Registers the following kernel services when required outbound ports are available:
 * <ul>
 *   <li>{@link KernelKnowledgeBaseVersionService} — knowledge base versioning</li>
 *   <li>{@link KnowledgeBasePermissionService} — knowledge base permission control</li>
 *   <li>{@link KnowledgeBaseShareService} — knowledge base external sharing</li>
 *   <li>{@link KernelAgentMarketplaceService} — agent marketplace (review, subscription, rating)</li>
 *   <li>{@link KernelAdminTenantService} — admin tenant management</li>
 *   <li>{@link KernelAuditLogService} — system audit log</li>
 * </ul>
 *
 * <p>Enabled by default; disable via {@code seahorse-agent.marketplace-admin.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.marketplace-admin", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMarketplaceAdminAutoConfiguration {

    // ==================== Admin Repository Adapters ====================

    /**
     * JDBC admin repository adapter — cross-tenant admin queries.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AdminRepositoryPort.class)
    public JdbcAdminRepositoryAdapter seahorseJdbcAdminRepositoryAdapter(DataSource dataSource) {
        return new JdbcAdminRepositoryAdapter(dataSource);
    }

    /**
     * JDBC audit log repository adapter — audit log persistence.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AuditLogRepositoryPort.class)
    public JdbcAuditLogRepositoryAdapter seahorseJdbcAuditLogRepositoryAdapter(DataSource dataSource) {
        return new JdbcAuditLogRepositoryAdapter(dataSource);
    }

    // ==================== Marketplace Repository Adapters ====================

    /**
     * JDBC agent publish review repository adapter.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AgentPublishReviewRepositoryPort.class)
    public JdbcAgentPublishReviewRepositoryAdapter seahorseJdbcAgentPublishReviewRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcAgentPublishReviewRepositoryAdapter(dataSource);
    }

    /**
     * JDBC agent subscription repository adapter.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AgentSubscriptionRepositoryPort.class)
    public JdbcAgentSubscriptionRepositoryAdapter seahorseJdbcAgentSubscriptionRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcAgentSubscriptionRepositoryAdapter(dataSource);
    }

    /**
     * JDBC agent rating repository adapter.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AgentRatingRepositoryPort.class)
    public JdbcAgentRatingRepositoryAdapter seahorseJdbcAgentRatingRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcAgentRatingRepositoryAdapter(dataSource);
    }

    /**
     * JDBC revenue share repository adapter.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(RevenueShareRepositoryPort.class)
    public JdbcRevenueShareRepositoryAdapter seahorseJdbcRevenueShareRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcRevenueShareRepositoryAdapter(dataSource);
    }

    // ==================== Knowledge Base Enhancement (Module 06) ====================

    /**
     * Knowledge base version management service.
     */
    @Bean
    @ConditionalOnBean(KnowledgeBaseVersionRepositoryPort.class)
    @ConditionalOnMissingBean(KernelKnowledgeBaseVersionService.class)
    public KernelKnowledgeBaseVersionService seahorseKnowledgeBaseVersionService(
            KnowledgeBaseVersionRepositoryPort versionRepository) {
        return new KernelKnowledgeBaseVersionService(versionRepository);
    }

    /**
     * Knowledge base permission management service.
     */
    @Bean
    @ConditionalOnBean(KnowledgeBasePermissionRepositoryPort.class)
    @ConditionalOnMissingBean(KnowledgeBasePermissionService.class)
    public KnowledgeBasePermissionService seahorseKnowledgeBasePermissionService(
            KnowledgeBasePermissionRepositoryPort permissionRepository) {
        return new KnowledgeBasePermissionService(permissionRepository);
    }

    /**
     * Knowledge base sharing service.
     */
    @Bean
    @ConditionalOnBean(KnowledgeBaseShareRepositoryPort.class)
    @ConditionalOnMissingBean(KnowledgeBaseShareService.class)
    public KnowledgeBaseShareService seahorseKnowledgeBaseShareService(
            KnowledgeBaseShareRepositoryPort shareRepository) {
        return new KnowledgeBaseShareService(shareRepository);
    }

    // ==================== Agent Marketplace (Module 07) ====================

    /**
     * Agent marketplace service — publish review, subscription, rating.
     */
    @Bean
    @ConditionalOnBean({
            AgentPublishReviewRepositoryPort.class,
            AgentSubscriptionRepositoryPort.class,
            AgentRatingRepositoryPort.class
    })
    @ConditionalOnMissingBean(KernelAgentMarketplaceService.class)
    public KernelAgentMarketplaceService seahorseAgentMarketplaceService(
            AgentPublishReviewRepositoryPort reviewRepository,
            AgentSubscriptionRepositoryPort subscriptionRepository,
            AgentRatingRepositoryPort ratingRepository) {
        return new KernelAgentMarketplaceService(reviewRepository, subscriptionRepository, ratingRepository);
    }

    /**
     * Revenue share service — creator earnings calculation and monthly settlement.
     */
    @Bean
    @ConditionalOnBean(RevenueShareRepositoryPort.class)
    @ConditionalOnMissingBean(RevenueService.class)
    public RevenueService seahorseRevenueService(
            RevenueShareRepositoryPort revenueShareRepository) {
        return new RevenueService(revenueShareRepository);
    }

    // ==================== Admin Operations (Module 10) ====================

    /**
     * Admin tenant management service.
     */
    @Bean
    @ConditionalOnBean({AdminRepositoryPort.class, AuditLogRepositoryPort.class})
    @ConditionalOnMissingBean(KernelAdminTenantService.class)
    public KernelAdminTenantService seahorseAdminTenantService(
            AdminRepositoryPort adminRepository,
            AuditLogRepositoryPort auditLogRepository) {
        return new KernelAdminTenantService(adminRepository, auditLogRepository);
    }

    /**
     * Audit log management service.
     */
    @Bean
    @ConditionalOnBean(AuditLogRepositoryPort.class)
    @ConditionalOnMissingBean(KernelAuditLogService.class)
    public KernelAuditLogService seahorseAuditLogService(
            AuditLogRepositoryPort auditLogRepository) {
        return new KernelAuditLogService(auditLogRepository);
    }
}
