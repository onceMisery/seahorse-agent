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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcBillLineItemRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcBillRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcPaymentCallbackLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcPaymentOrderRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSubscriptionPlanRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSubscriptionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTransactionRunnerAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.StubPaymentGatewayAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.billing.KernelBillingService;
import com.miracle.ai.seahorse.agent.kernel.application.billing.KernelPaymentService;
import com.miracle.ai.seahorse.agent.kernel.application.billing.KernelSubscriptionService;
import com.miracle.ai.seahorse.agent.kernel.application.billing.QuotaEnforcementService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.RevenueService;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.BillingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.PaymentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.billing.SubscriptionInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillLineItemRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.BillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentCallbackLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.PaymentOrderRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionPlanRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.TransactionRunnerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Auto-configuration for the billing system module (SaaS MVP Tasks 3.1-3.3).
 *
 * <p>Registers JDBC adapter beans and kernel services when required outbound ports are available:
 * <ul>
 *   <li>{@link KernelSubscriptionService} — plan listing and subscription management</li>
 *   <li>{@link KernelPaymentService} — payment order creation and callback processing</li>
 *   <li>{@link KernelBillingService} — bill generation and invoice queries</li>
 * </ul>
 *
 * <p>Enabled by default; disable via {@code seahorse.agent.billing.enabled=false}.
 */
@AutoConfiguration
@AutoConfigureAfter({
        SeahorseAgentKernelAutoConfiguration.class,
        SeahorseAgentKernelAgentAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse.agent.billing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentBillingAutoConfiguration {

    // ─── JDBC Adapter Beans ──────────────────────────────────────────────────

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(SubscriptionPlanRepositoryPort.class)
    public JdbcSubscriptionPlanRepositoryAdapter seahorseJdbcSubscriptionPlanRepositoryAdapter(DataSource dataSource) {
        return new JdbcSubscriptionPlanRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(SubscriptionRepositoryPort.class)
    public JdbcSubscriptionRepositoryAdapter seahorseJdbcSubscriptionRepositoryAdapter(DataSource dataSource) {
        return new JdbcSubscriptionRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(BillRepositoryPort.class)
    public JdbcBillRepositoryAdapter seahorseJdbcBillRepositoryAdapter(DataSource dataSource) {
        return new JdbcBillRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(BillLineItemRepositoryPort.class)
    public JdbcBillLineItemRepositoryAdapter seahorseJdbcBillLineItemRepositoryAdapter(DataSource dataSource) {
        return new JdbcBillLineItemRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(PaymentOrderRepositoryPort.class)
    public JdbcPaymentOrderRepositoryAdapter seahorseJdbcPaymentOrderRepositoryAdapter(DataSource dataSource) {
        return new JdbcPaymentOrderRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(PaymentCallbackLogRepositoryPort.class)
    public JdbcPaymentCallbackLogRepositoryAdapter seahorseJdbcPaymentCallbackLogRepositoryAdapter(DataSource dataSource) {
        return new JdbcPaymentCallbackLogRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    @ConditionalOnMissingBean(TransactionRunnerPort.class)
    public JdbcTransactionRunnerAdapter seahorseJdbcTransactionRunnerAdapter(
            PlatformTransactionManager transactionManager) {
        return new JdbcTransactionRunnerAdapter(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(PaymentGatewayPort.class)
    public StubPaymentGatewayAdapter seahorseStubPaymentGatewayAdapter() {
        return new StubPaymentGatewayAdapter();
    }

    // ─── Kernel Service Beans ────────────────────────────────────────────────

    /**
     * Subscription management service — plan listing, active subscription lookup,
     * and new subscription creation.
     */
    @Bean
    @ConditionalOnBean({
            SubscriptionPlanRepositoryPort.class,
            SubscriptionRepositoryPort.class
    })
    @ConditionalOnMissingBean(SubscriptionInboundPort.class)
    public KernelSubscriptionService seahorseSubscriptionService(
            SubscriptionPlanRepositoryPort planRepository,
            SubscriptionRepositoryPort subscriptionRepository) {
        return new KernelSubscriptionService(planRepository, subscriptionRepository);
    }

    /**
     * Payment processing service — order creation, gateway integration,
     * and triple-idempotent callback handling.
     *
     * <p>Optionally integrates with {@link RevenueService} for marketplace revenue tracking.
     */
    @Bean
    @ConditionalOnBean({
            PaymentOrderRepositoryPort.class,
            SubscriptionPlanRepositoryPort.class,
            PaymentGatewayPort.class,
            PaymentCallbackLogRepositoryPort.class,
            TransactionRunnerPort.class
    })
    @ConditionalOnMissingBean(PaymentInboundPort.class)
    public KernelPaymentService seahorsePaymentService(
            PaymentOrderRepositoryPort orderRepository,
            SubscriptionPlanRepositoryPort planRepository,
            PaymentGatewayPort paymentGateway,
            PaymentCallbackLogRepositoryPort callbackLogRepository,
            TransactionRunnerPort transactionRunner,
            ObjectProvider<RevenueService> revenueServiceProvider) {
        return new KernelPaymentService(orderRepository, planRepository,
                paymentGateway, callbackLogRepository, transactionRunner,
                revenueServiceProvider.getIfAvailable());
    }

    /**
     * Billing and invoice service — bill listing, detail retrieval,
     * and monthly bill generation.
     */
    @Bean
    @ConditionalOnBean({
            BillRepositoryPort.class,
            BillLineItemRepositoryPort.class,
            SubscriptionRepositoryPort.class,
            SubscriptionPlanRepositoryPort.class
    })
    @ConditionalOnMissingBean(BillingInboundPort.class)
    public KernelBillingService seahorseBillingService(
            BillRepositoryPort billRepository,
            BillLineItemRepositoryPort lineItemRepository,
            SubscriptionRepositoryPort subscriptionRepository,
            SubscriptionPlanRepositoryPort planRepository) {
        return new KernelBillingService(billRepository, lineItemRepository,
                subscriptionRepository, planRepository);
    }

    /**
     * Quota enforcement service — checks storage, token, and concurrency limits
     * before resource-consuming operations.
     *
     * <p>Token quota enforcement is enabled only when {@link CostUsageRepositoryPort}
     * is available; otherwise token checks are silently skipped (fail-open).
     */
    @Bean
    @ConditionalOnBean({
            SubscriptionRepositoryPort.class,
            KnowledgeDocumentRepositoryPort.class,
            AgentRunRepositoryPort.class
    })
    @ConditionalOnMissingBean(QuotaEnforcementService.class)
    public QuotaEnforcementService seahorseQuotaEnforcementService(
            SubscriptionRepositoryPort subscriptionRepository,
            KnowledgeDocumentRepositoryPort documentRepository,
            AgentRunRepositoryPort agentRunRepository,
            ObjectProvider<CostUsageRepositoryPort> costUsageRepositoryProvider) {
        return new QuotaEnforcementService(subscriptionRepository, documentRepository,
                agentRunRepository, costUsageRepositoryProvider.getIfAvailable());
    }
}
