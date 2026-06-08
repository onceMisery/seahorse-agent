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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSandboxRepositoryAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.DefaultSandboxPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.KernelSandboxRuntimeService;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentSandboxAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeahorseAgentRegistryRepositoryAutoConfiguration.class,
                    SeahorseAgentKernelRegistryAutoConfiguration.class));

    @Test
    void shouldWireFailClosedSandboxRuntimeByDefault() {
        contextRunner.withUserConfiguration(TestInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcSandboxRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(SandboxSessionRepositoryPort.class);
                    assertThat(context).hasSingleBean(SandboxExecutionRepositoryPort.class);
                    assertThat(context).hasSingleBean(SandboxArtifactPort.class);
                    assertThat(context).hasSingleBean(SandboxArtifactQueryPort.class);
                    assertThat(context).hasSingleBean(SandboxPolicyPort.class);
                    assertThat(context.getBean(SandboxPolicyPort.class)).isInstanceOf(DefaultSandboxPolicyPort.class);
                    assertThat(context).hasSingleBean(SandboxRuntimePort.class);
                    assertThat(context).hasSingleBean(SandboxRuntimeInboundPort.class);
                    assertThat(context).hasSingleBean(KernelSandboxRuntimeService.class);
                    assertThat(context).hasSingleBean(KernelAuditLedgerService.class);
                    assertThat(field(context.getBean(KernelSandboxRuntimeService.class), "auditLedger"))
                            .isSameAs(context.getBean(KernelAuditLedgerService.class));
                });
    }

    @Test
    void shouldKeepCustomSandboxRuntimeReplaceable() {
        contextRunner.withUserConfiguration(TestInfrastructureConfiguration.class, CustomRuntimeConfiguration.class)
                .run(context -> assertThat(context.getBean(SandboxRuntimePort.class))
                        .isSameAs(context.getBean("customSandboxRuntimePort")));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:sandbox-autoconfig-" + System.nanoTime()
                            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRuntimeConfiguration {

        @Bean
        SandboxRuntimePort customSandboxRuntimePort() {
            return SandboxRuntimePort.unsupported();
        }
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
}
