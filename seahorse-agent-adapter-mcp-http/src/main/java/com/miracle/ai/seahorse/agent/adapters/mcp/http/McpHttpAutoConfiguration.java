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
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolFeature;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStoreCredentialProvider;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MCP HTTP 原生 adapter 自动装配。
 *
 * <p>当配置了远程 MCP Server 时，本配置会在启动期发现远程工具并注册为 {@link McpToolFeature}。
 * 未配置或远程不可用时保持空注册表，让 RAG 主链路降级为仅 KB 检索。
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentCredentialAutoConfiguration")
@AutoConfigureBefore(name = "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelAutoConfiguration")
@EnableConfigurationProperties(McpHttpAdapterProperties.class)
@Conditional(NativeMcpEnabledCondition.class)
public class McpHttpAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(McpHttpAutoConfiguration.class);
    private static final String MSG_CREDENTIAL_PROVIDER_MISSING = "credential provider missing";
    private static final String MSG_CLIENT_SECRET_REF_MISSING = "clientSecretRef missing";
    private static final String MSG_CREDENTIAL_RESOLUTION_FAILED = "credential resolution failed";

    @Bean
    @ConditionalOnBean(SecretStorePort.class)
    @ConditionalOnMissingBean(CredentialProviderPort.class)
    public CredentialProviderPort seahorseSecretStoreCredentialProvider(SecretStorePort secretStorePort) {
        return new SecretStoreCredentialProvider(secretStorePort);
    }

    @Bean
    @ConditionalOnBean(OkHttpClient.class)
    @ConditionalOnMissingBean(McpToolRegistryPort.class)
    public NativeMcpToolRegistry seahorseNativeMcpToolRegistry(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            McpHttpAdapterProperties properties,
            ObjectProvider<McpToolFeature> localToolFeatures,
            ObjectProvider<CredentialProviderPort> credentialProvider) {
        List<McpToolFeature> features = new ArrayList<>(localToolFeatures.orderedStream().toList());
        OkHttpClient effectiveHttpClient = httpClient.newBuilder()
                .callTimeout(properties.getCallTimeout())
                .build();
        features.addAll(discoverRemoteFeatures(effectiveHttpClient, objectMapper, properties, credentialProvider));
        return new NativeMcpToolRegistry(features);
    }

    @Bean
    @ConditionalOnMissingBean(McpParameterExtractionPort.class)
    public LlmMcpParameterExtractionAdapter seahorseLlmMcpParameterExtractionAdapter(
            ObjectProvider<ChatModelPort> chatModelPort,
            ObjectMapper objectMapper) {
        return new LlmMcpParameterExtractionAdapter(chatModelPort, objectMapper);
    }

    private List<McpToolFeature> discoverRemoteFeatures(OkHttpClient httpClient,
                                                        ObjectMapper objectMapper,
                                                        McpHttpAdapterProperties properties,
                                                        ObjectProvider<CredentialProviderPort> credentialProvider) {
        List<McpToolFeature> features = new ArrayList<>();
        for (McpHttpAdapterProperties.Server server : properties.getServers()) {
            features.addAll(discoverServerFeatures(httpClient, objectMapper, server, credentialProvider));
        }
        return features;
    }

    private List<McpToolFeature> discoverServerFeatures(OkHttpClient httpClient,
                                                        ObjectMapper objectMapper,
                                                        McpHttpAdapterProperties.Server server,
                                                        ObjectProvider<CredentialProviderPort> credentialProvider) {
        if (!server.isEnabled() || server.getUrl().isBlank()) {
            return List.of();
        }
        Optional<CredentialMaterial> credentialMaterial = resolveCredentialMaterial(server, credentialProvider);
        if (credentialMaterial.isEmpty()) {
            return List.of();
        }
        try {
            StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                    httpClient, objectMapper, server.getName(), server.getUrl(), credentialMaterial.get());
            if (!client.initialize()) {
                return List.of();
            }
            List<McpToolDescriptor> tools = client.listTools();
            LOG.info("MCP Server 工具发现完成, server={}, toolCount={}", server.getName(), tools.size());
            return tools.stream()
                    .map(descriptor -> new RemoteMcpToolFeature(descriptor, client))
                    .map(McpToolFeature.class::cast)
                    .toList();
        } catch (Exception ex) {
            LOG.warn("MCP Server 工具发现失败, server={}, reason={}", server.getName(), ex.getMessage());
            return List.of();
        }
    }

    private Optional<CredentialMaterial> resolveCredentialMaterial(
            McpHttpAdapterProperties.Server server,
            ObjectProvider<CredentialProviderPort> credentialProvider) {
        if (CredentialAuthType.NONE.equals(server.getAuthType())) {
            return Optional.of(CredentialMaterial.none());
        }
        CredentialProviderPort provider = credentialProvider.getIfAvailable();
        if (provider == null) {
            LOG.warn("MCP Server credential skipped, server={}, authType={}, reason={}",
                    server.getName(), server.getAuthType(), MSG_CREDENTIAL_PROVIDER_MISSING);
            return Optional.empty();
        }
        if (CredentialAuthType.STATIC_BEARER.equals(server.getAuthType())
                && server.getClientSecretRef().isBlank()) {
            LOG.warn("MCP Server credential skipped, server={}, authType={}, reason={}",
                    server.getName(), server.getAuthType(), MSG_CLIENT_SECRET_REF_MISSING);
            return Optional.empty();
        }
        try {
            return Optional.of(provider.resolve(toCredentialRequest(server)));
        } catch (RuntimeException ex) {
            LOG.warn("MCP Server credential skipped, server={}, authType={}, reason={}",
                    server.getName(), server.getAuthType(), MSG_CREDENTIAL_RESOLUTION_FAILED);
            return Optional.empty();
        }
    }

    private CredentialRequest toCredentialRequest(McpHttpAdapterProperties.Server server) {
        return switch (server.getAuthType()) {
            case NONE -> CredentialRequest.none();
            case STATIC_BEARER -> CredentialRequest.staticBearer(server.getClientSecretRef());
        };
    }
}
