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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAuditEventRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcProductionGateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.gate.KernelProductionGateService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ProductionGateInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentAuditAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeahorseAgentRegistryRepositoryAutoConfiguration.class,
                    SeahorseAgentKernelRegistryAutoConfiguration.class));

    @Test
    void shouldWireAuditLedgerAndProductionGateByDefault() {
        contextRunner.withUserConfiguration(TestInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcAuditEventRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(AuditEventRepositoryPort.class);
                    assertThat(context).hasSingleBean(JdbcProductionGateRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(ProductionGateRepositoryPort.class);
                    assertThat(context).hasSingleBean(AuditRedactionPolicy.class);
                    assertThat(context).hasSingleBean(AuditQueryInboundPort.class);
                    assertThat(context).hasSingleBean(KernelAuditLedgerService.class);
                    assertThat(context).hasSingleBean(ProductionGateInboundPort.class);
                    assertThat(context).hasSingleBean(KernelProductionGateService.class);
                });
    }

    @Test
    void shouldKeepCustomAuditAndGateServicesReplaceable() {
        contextRunner.withUserConfiguration(TestInfrastructureConfiguration.class, CustomServiceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AuditQueryInboundPort.class))
                            .isSameAs(context.getBean("customAuditQueryInboundPort"));
                    assertThat(context.getBean(ProductionGateInboundPort.class))
                            .isSameAs(context.getBean("customProductionGateInboundPort"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:audit-autoconfig-" + System.nanoTime()
                            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomServiceConfiguration {

        @Bean
        AuditQueryInboundPort customAuditQueryInboundPort() {
            return new AuditQueryInboundPort() {
                @Override
                public Optional<AuditEvent> findById(String auditId) {
                    return Optional.empty();
                }

                @Override
                public AuditEventPage page(String tenantId,
                                           String runId,
                                           String agentId,
                                           String resourceType,
                                           String resourceId,
                                           AuditEventType eventType,
                                           Instant occurredFrom,
                                           Instant occurredTo,
                                           long current,
                                           long size) {
                    return new AuditEventPage(java.util.List.of(), 0L, size, current, 0L);
                }
            };
        }

        @Bean
        ProductionGateInboundPort customProductionGateInboundPort() {
            return new ProductionGateInboundPort() {
                @Override
                public ProductionGateReport generate(String agentId) {
                    throw new UnsupportedOperationException("custom");
                }

                @Override
                public Optional<ProductionGateReport> latest(String agentId) {
                    return Optional.empty();
                }
            };
        }
    }
}
