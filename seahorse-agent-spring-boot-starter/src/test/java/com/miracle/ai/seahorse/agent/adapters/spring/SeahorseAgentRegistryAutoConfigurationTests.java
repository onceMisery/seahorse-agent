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
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.KernelToolInvocationAuditQueryService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolCatalogManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
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
                    assertThat(context).hasSingleBean(AgentRunRepositoryPort.class);
                    assertThat(context).hasSingleBean(ToolCatalogRepositoryPort.class);
                    assertThat(context).hasSingleBean(AgentToolBindingRepositoryPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationAuditPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationAuditQueryPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationUsagePort.class);
                    assertThat(context).hasSingleBean(AgentDefinitionInboundPort.class);
                    assertThat(context).hasSingleBean(AgentRunInboundPort.class);
                    assertThat(context).hasSingleBean(ToolCatalogManagementInboundPort.class);
                    assertThat(context).hasSingleBean(AgentToolBindingManagementInboundPort.class);
                    assertThat(context).hasSingleBean(ToolInvocationAuditQueryInboundPort.class);
                    assertThat(context).hasSingleBean(KernelAgentDefinitionService.class);
                    assertThat(context).hasSingleBean(KernelAgentRunService.class);
                    assertThat(context).hasSingleBean(KernelToolCatalogManagementService.class);
                    assertThat(context).hasSingleBean(KernelAgentToolBindingManagementService.class);
                    assertThat(context).hasSingleBean(KernelToolInvocationAuditQueryService.class);
                });
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
    }
}
