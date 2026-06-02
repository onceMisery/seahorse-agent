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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSecretStoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.SecretValueCipher;
import com.miracle.ai.seahorse.agent.adapters.mcp.http.McpHttpAutoConfiguration;
import com.miracle.ai.seahorse.agent.kernel.application.credential.KernelSecretManagementService;
import com.miracle.ai.seahorse.agent.ports.inbound.credential.SecretManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.InMemoryOAuthTokenCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthCredentialProvider;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthToken;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthTokenCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.OAuthTokenPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStoreCredentialProvider;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentCredentialAutoConfigurationTests {

    private static final String AES_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentCredentialAutoConfiguration.class));

    @Test
    void shouldCreateSecretStoreAndManagementBeansWhenJdbcKeyIsConfigured() {
        contextRunner
                .withUserConfiguration(TestInfrastructureConfiguration.class)
                .withPropertyValues("seahorse-agent.credentials.jdbc.aes-key-base64=" + AES_KEY_BASE64)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecretValueCipher.class);
                    assertThat(context).hasSingleBean(JdbcSecretStoreAdapter.class);
                    assertThat(context).hasSingleBean(SecretStorePort.class);
                    assertThat(context).hasSingleBean(SecretWritePort.class);
                    assertThat(context).hasSingleBean(SecretManagementInboundPort.class);
                    assertThat(context).hasSingleBean(KernelSecretManagementService.class);
                });
    }

    @Test
    void shouldNotCreateJdbcSecretStoreWithoutEncryptionKey() {
        contextRunner
                .withUserConfiguration(TestInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SecretValueCipher.class);
                    assertThat(context).doesNotHaveBean(SecretStorePort.class);
                    assertThat(context).doesNotHaveBean(SecretWritePort.class);
                    assertThat(context).doesNotHaveBean(SecretManagementInboundPort.class);
                });
    }

    @Test
    void shouldLetMcpCredentialsResolveFromJdbcSecretStoreRegardlessOfAutoConfigurationOrder() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        McpHttpAutoConfiguration.class,
                        SeahorseAgentCredentialAutoConfiguration.class))
                .withUserConfiguration(TestInfrastructureConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.mcp.enabled=true",
                        "seahorse-agent.credentials.jdbc.aes-key-base64=" + AES_KEY_BASE64)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SecretStorePort.class);
                    assertThat(context).hasSingleBean(CredentialProviderPort.class);
                    assertThat(context.getBean(CredentialProviderPort.class))
                            .isInstanceOf(SecretStoreCredentialProvider.class);
                });
    }

    @Test
    void shouldCreateOAuthCredentialProviderWhenOAuthTokenPortExists() {
        contextRunner
                .withBean(SecretStorePort.class, () -> secretRef -> Optional.of(SecretValue.of("client-secret")))
                .withBean(OAuthTokenPort.class, () -> request -> OAuthToken.bearer(
                        SecretValue.of("access-token"),
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        List.of("weather.read"),
                        ""))
                .run(context -> {
                    assertThat(context).hasSingleBean(OAuthTokenCachePort.class);
                    assertThat(context.getBean(OAuthTokenCachePort.class))
                            .isInstanceOf(InMemoryOAuthTokenCachePort.class);
                    assertThat(context).hasSingleBean(CredentialProviderPort.class);
                    assertThat(context.getBean(CredentialProviderPort.class))
                            .isInstanceOf(OAuthCredentialProvider.class);
                });
    }

    @Test
    void shouldBackOffWhenCustomCredentialProviderExistsForOAuth() {
        CredentialProviderPort customProvider = request -> com.miracle.ai.seahorse.agent.ports.outbound.credential
                .CredentialMaterial.none();

        contextRunner
                .withBean(SecretStorePort.class, () -> secretRef -> Optional.of(SecretValue.of("client-secret")))
                .withBean(OAuthTokenPort.class, () -> request -> OAuthToken.bearer(
                        SecretValue.of("access-token"),
                        Instant.now().plus(1, ChronoUnit.HOURS),
                        List.of("weather.read"),
                        ""))
                .withBean(CredentialProviderPort.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasSingleBean(CredentialProviderPort.class);
                    assertThat(context.getBean(CredentialProviderPort.class)).isSameAs(customProvider);
                    assertThat(context).hasSingleBean(OAuthTokenCachePort.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:secret-autoconfig-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                    "sa",
                    "");
        }

        @Bean
        CurrentUserPort currentUserPort() {
            return () -> Optional.of(new CurrentUser(1L, "admin", "admin", null));
        }
    }
}
