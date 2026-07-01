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
import com.miracle.ai.seahorse.agent.kernel.application.agent.GovernedToolExecutionPort;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolFeature;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerManagementInboundPort;
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

import java.time.Duration;
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
    private static final String MSG_CLIENT_ID_MISSING = "clientId missing";
    private static final String MSG_SERVER_NAME_MISSING = "server name missing";
    private static final String MSG_STDIO_COMMAND_NOT_ALLOWED = "stdio command not allowed";
    private static final String MSG_USER_DELEGATED_UNSUPPORTED = "user delegated credentials unsupported";
    private static final String MSG_CREDENTIAL_RESOLUTION_FAILED = "credential resolution failed";

    @Bean
    @ConditionalOnBean(SecretStorePort.class)
    @ConditionalOnMissingBean(CredentialProviderPort.class)
    public CredentialProviderPort seahorseSecretStoreCredentialProvider(SecretStorePort secretStorePort) {
        return new SecretStoreCredentialProvider(secretStorePort);
    }

    @Bean
    @ConditionalOnMissingBean(McpServerManagementInboundPort.class)
    public McpServerRuntimeRegistry seahorseMcpServerRuntimeRegistry(
            ObjectProvider<GovernedToolExecutionPort> governedToolExecutionPort) {
        return new McpServerRuntimeRegistry(governedToolExecutionPort::getIfAvailable);
    }

    @Bean
    @ConditionalOnBean(OkHttpClient.class)
    @ConditionalOnMissingBean(McpToolRegistryPort.class)
    public NativeMcpToolRegistry seahorseNativeMcpToolRegistry(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            McpHttpAdapterProperties properties,
            ObjectProvider<McpToolFeature> localToolFeatures,
            ObjectProvider<CredentialProviderPort> credentialProvider,
            ObjectProvider<McpServerRuntimeRegistry> mcpServerRuntimeRegistryProvider) {
        List<McpToolFeature> baseFeatures = new ArrayList<>(localToolFeatures.orderedStream().toList());
        List<McpToolFeature> features = new ArrayList<>(baseFeatures);
        McpServerRuntimeRegistry mcpServerRuntimeRegistry = mcpServerRuntimeRegistryProvider
                .getIfAvailable(McpServerRuntimeRegistry::new);
        OkHttpClient effectiveHttpClient = httpClient.newBuilder()
                .callTimeout(properties.getCallTimeout())
                .build();
        features.addAll(discoverRemoteFeatures(
                effectiveHttpClient,
                objectMapper,
                properties,
                credentialProvider,
                mcpServerRuntimeRegistry));
        NativeMcpToolRegistry registry = new NativeMcpToolRegistry(features);
        mcpServerRuntimeRegistry.setLifecycleActions(new McpServerRuntimeRegistry.LifecycleActions() {
            @Override
            public void restart(String serverName) {
                rediscoverRemoteFeatures();
            }

            @Override
            public void refreshTools(String serverName) {
                rediscoverRemoteFeatures();
            }

            private void rediscoverRemoteFeatures() {
                List<McpToolFeature> refreshedFeatures = new ArrayList<>(baseFeatures);
                refreshedFeatures.addAll(discoverRemoteFeatures(
                        effectiveHttpClient,
                        objectMapper,
                        properties,
                        credentialProvider,
                        mcpServerRuntimeRegistry));
                registry.replaceAll(refreshedFeatures);
            }
        });
        return registry;
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
                                                        ObjectProvider<CredentialProviderPort> credentialProvider,
                                                        McpServerRuntimeRegistry mcpServerRuntimeRegistry) {
        List<McpToolFeature> features = new ArrayList<>();
        for (McpHttpAdapterProperties.Server server : properties.getServers()) {
            features.addAll(discoverServerFeatures(
                    httpClient,
                    objectMapper,
                    server,
                    credentialProvider,
                    properties.getCallTimeout(),
                    properties.getStdioCommandAllowlist(),
                    StdioMcpRunnerPolicy.from(properties.getStdioRunnerIsolation()),
                    mcpServerRuntimeRegistry));
        }
        return features;
    }

    private List<McpToolFeature> discoverServerFeatures(OkHttpClient httpClient,
                                                        ObjectMapper objectMapper,
                                                        McpHttpAdapterProperties.Server server,
                                                        ObjectProvider<CredentialProviderPort> credentialProvider,
                                                        Duration callTimeout,
                                                        List<String> stdioCommandAllowlist,
                                                        StdioMcpRunnerPolicy stdioRunnerPolicy,
                                                        McpServerRuntimeRegistry mcpServerRuntimeRegistry) {
        if (!server.isEnabled()) {
            mcpServerRuntimeRegistry.recordDisabled(server);
            return List.of();
        }
        return switch (server.getTransport()) {
            case STDIO -> discoverStdioServerFeatures(
                    objectMapper,
                    server,
                    callTimeout,
                    stdioCommandAllowlist,
                    stdioRunnerPolicy,
                    mcpServerRuntimeRegistry);
            case STREAMABLE_HTTP -> discoverHttpServerFeatures(
                    httpClient,
                    objectMapper,
                    server,
                    credentialProvider,
                    mcpServerRuntimeRegistry);
        };
    }

    private List<McpToolFeature> discoverHttpServerFeatures(OkHttpClient httpClient,
                                                            ObjectMapper objectMapper,
                                                            McpHttpAdapterProperties.Server server,
                                                            ObjectProvider<CredentialProviderPort> credentialProvider,
                                                            McpServerRuntimeRegistry mcpServerRuntimeRegistry) {
        if (server.getUrl().isBlank()) {
            mcpServerRuntimeRegistry.recordFailed(server, "url missing");
            return List.of();
        }
        Optional<CredentialMaterial> credentialMaterial = resolveCredentialMaterial(server, credentialProvider);
        if (credentialMaterial.isEmpty()) {
            mcpServerRuntimeRegistry.recordFailed(server, "credential unavailable");
            return List.of();
        }
        try {
            StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                    httpClient, objectMapper, server.getName(), server.getUrl(), credentialMaterial.get());
            if (!client.initialize()) {
                mcpServerRuntimeRegistry.recordFailed(server, "initialize failed");
                return List.of();
            }
            List<McpToolDescriptor> tools = client.listTools();
            mcpServerRuntimeRegistry.recordReady(server, tools, "");
            LOG.info("MCP Server 工具发现完成, server={}, toolCount={}", server.getName(), tools.size());
            return tools.stream()
                    .map(descriptor -> new RemoteMcpToolFeature(descriptor, client))
                    .map(McpToolFeature.class::cast)
                    .toList();
        } catch (Exception ex) {
            mcpServerRuntimeRegistry.recordFailed(server, ex.getMessage());
            LOG.warn("MCP Server 工具发现失败, server={}, reason={}", server.getName(), ex.getMessage());
            return List.of();
        }
    }

    private List<McpToolFeature> discoverStdioServerFeatures(ObjectMapper objectMapper,
                                                             McpHttpAdapterProperties.Server server,
                                                             Duration callTimeout,
                                                             List<String> stdioCommandAllowlist,
                                                             StdioMcpRunnerPolicy stdioRunnerPolicy,
                                                             McpServerRuntimeRegistry mcpServerRuntimeRegistry) {
        if (server.getCommand().isBlank()) {
            mcpServerRuntimeRegistry.recordFailed(server, "command missing");
            LOG.warn("MCP stdio Server skipped, server={}, reason=command missing", server.getName());
            return List.of();
        }
        if (!isStdioCommandAllowed(server.getCommand(), stdioCommandAllowlist)) {
            String reason = MSG_STDIO_COMMAND_NOT_ALLOWED + ": " + server.getCommand();
            mcpServerRuntimeRegistry.recordFailed(server, reason);
            LOG.warn("MCP stdio Server skipped, server={}, reason={}", server.getName(), reason);
            return List.of();
        }
        Optional<String> runnerPolicyViolation = stdioRunnerPolicy.validateWorkingDir(server.getWorkingDir());
        if (runnerPolicyViolation.isPresent()) {
            String reason = runnerPolicyViolation.orElseThrow();
            mcpServerRuntimeRegistry.recordFailed(server, reason);
            LOG.warn("MCP stdio Server skipped, server={}, reason={}", server.getName(), reason);
            return List.of();
        }
        StdioMcpClient client = new StdioMcpClient(
                objectMapper,
                server.getName(),
                server.getCommand(),
                server.getArgs(),
                server.getEnv(),
                server.getWorkingDir(),
                callTimeout,
                stdioRunnerPolicy);
        try {
            if (!client.initialize()) {
                mcpServerRuntimeRegistry.recordFailed(server, client.stderrTail());
                client.close();
                return List.of();
            }
            List<McpToolDescriptor> tools = client.listTools();
            if (tools.isEmpty()) {
                mcpServerRuntimeRegistry.recordFailed(server, "no tools discovered");
                client.close();
                return List.of();
            }
            mcpServerRuntimeRegistry.recordReady(server, tools, client.stderrTail());
            LOG.info("MCP stdio Server 宸ュ叿鍙戠幇瀹屾垚, server={}, toolCount={}", server.getName(), tools.size());
            return tools.stream()
                    .map(descriptor -> new RemoteMcpToolFeature(descriptor, client))
                    .map(McpToolFeature.class::cast)
                    .toList();
        } catch (Exception ex) {
            mcpServerRuntimeRegistry.recordFailed(server, ex.getMessage());
            client.close();
            LOG.warn("MCP stdio Server 宸ュ叿鍙戠幇澶辫触, server={}, reason={}", server.getName(), ex.getMessage());
            return List.of();
        }
    }

    private boolean isStdioCommandAllowed(String command, List<String> stdioCommandAllowlist) {
        String safeCommand = command == null ? "" : command.trim();
        if (safeCommand.isBlank()) {
            return false;
        }
        return stdioCommandAllowlist != null && stdioCommandAllowlist.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .anyMatch(safeCommand::equals);
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
        Optional<String> invalidReason = validateCredentialConfiguration(server);
        if (invalidReason.isPresent()) {
            LOG.warn("MCP Server credential skipped, server={}, authType={}, reason={}",
                    server.getName(), server.getAuthType(), invalidReason.get());
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
            case CLIENT_CREDENTIALS -> CredentialRequest.clientCredentials(
                    server.getTenantId(),
                    server.getName(),
                    server.getClientId(),
                    server.getClientSecretRef(),
                    server.getScopes(),
                    server.getAudience(),
                    server.getResource());
            case USER_DELEGATED -> CredentialRequest.userDelegated(
                    server.getTenantId(),
                    server.getName(),
                    server.getClientId(),
                    server.getScopes(),
                    server.getAudience(),
                    server.getResource());
        };
    }

    private Optional<String> validateCredentialConfiguration(McpHttpAdapterProperties.Server server) {
        if (CredentialAuthType.USER_DELEGATED.equals(server.getAuthType())) {
            return Optional.of(MSG_USER_DELEGATED_UNSUPPORTED);
        }
        if (CredentialAuthType.STATIC_BEARER.equals(server.getAuthType())
                && server.getClientSecretRef().isBlank()) {
            return Optional.of(MSG_CLIENT_SECRET_REF_MISSING);
        }
        if (CredentialAuthType.CLIENT_CREDENTIALS.equals(server.getAuthType())) {
            if (server.getName().isBlank()) {
                return Optional.of(MSG_SERVER_NAME_MISSING);
            }
            if (server.getClientId().isBlank()) {
                return Optional.of(MSG_CLIENT_ID_MISSING);
            }
            if (server.getClientSecretRef().isBlank()) {
                return Optional.of(MSG_CLIENT_SECRET_REF_MISSING);
            }
        }
        return Optional.empty();
    }
}
