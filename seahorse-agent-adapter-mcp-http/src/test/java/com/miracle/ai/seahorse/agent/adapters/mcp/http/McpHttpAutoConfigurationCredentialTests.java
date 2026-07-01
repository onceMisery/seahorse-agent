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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerStatusView;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStoreCredentialProvider;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class McpHttpAutoConfigurationCredentialTests {

    private static final String SECRET_REF = "secret:mcp/weather";
    private static final String RAW_TOKEN = "sk-live-secret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("seahorse-agent.adapters.mcp.enabled=true")
            .withConfiguration(AutoConfigurations.of(McpHttpAutoConfiguration.class));

    @Test
    void shouldRegisterCredentialProviderWhenSecretStoreExists() {
        contextRunner.withUserConfiguration(SecretStoreConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CredentialProviderPort.class);
                    assertThat(context.getBean(CredentialProviderPort.class))
                            .isInstanceOf(SecretStoreCredentialProvider.class);

                    CredentialMaterial material = context.getBean(CredentialProviderPort.class)
                            .resolve(CredentialRequest.staticBearer(SECRET_REF));

                    assertThat(material.secretRef()).isEqualTo(SECRET_REF);
                    assertThat(material.secretValue().reveal()).isEqualTo(RAW_TOKEN);
                });
    }

    @Test
    void shouldBackOffWhenCredentialProviderAlreadyExists() {
        contextRunner.withUserConfiguration(SecretStoreConfiguration.class)
                .withBean(CredentialProviderPort.class, () -> request -> CredentialMaterial.none())
                .run(context -> {
                    assertThat(context).hasSingleBean(CredentialProviderPort.class);
                    assertThat(context.getBean(CredentialProviderPort.class))
                            .isNotInstanceOf(SecretStoreCredentialProvider.class);
                });
    }

    @Test
    void shouldBackOffRuntimeRegistryWhenManagementPortAlreadyExists() {
        contextRunner
                .withBean(McpServerManagementInboundPort.class, () -> new McpServerManagementInboundPort() {
                    @Override
                    public List<McpServerStatusView> listServers() {
                        return List.of();
                    }

                    @Override
                    public Optional<McpServerStatusView> findServer(String serverName) {
                        return Optional.empty();
                    }
                })
                .run(context -> assertThat(context).doesNotHaveBean(McpServerRuntimeRegistry.class));
    }

    @Test
    void shouldBlockStdioServerWhenCommandIsNotAllowlisted() {
        contextRunner
                .withBean(OkHttpClient.class, OkHttpClient::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.mcp.servers[0].name=blocked-shell",
                        "seahorse-agent.adapters.mcp.servers[0].transport=stdio",
                        "seahorse-agent.adapters.mcp.servers[0].command=pwsh",
                        "seahorse-agent.adapters.mcp.stdio-command-allowlist[0]=node")
                .run(context -> {
                    assertThat(context).hasSingleBean(McpServerRuntimeRegistry.class);
                    McpServerRuntimeRegistry registry = context.getBean(McpServerRuntimeRegistry.class);

                    assertThat(registry.listServers()).hasSize(1);
                    assertThat(registry.findServer("blocked-shell")).hasValueSatisfying(status -> {
                        assertThat(status.getStatus()).isEqualTo(McpServerRuntimeRegistry.STATUS_FAILED);
                        assertThat(status.getToolCount()).isZero();
                        assertThat(status.getStderrTail()).isEqualTo("stdio command not allowed: pwsh");
                    });
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class SecretStoreConfiguration {

        @Bean
        SecretStorePort secretStorePort() {
            return secretRef -> SECRET_REF.equals(secretRef)
                    ? Optional.of(SecretValue.of(RAW_TOKEN))
                    : Optional.empty();
        }
    }
}
