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
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP HTTP 原生 adapter 自动装配。
 *
 * <p>当配置了远程 MCP Server 时，本配置会在启动期发现远程工具并注册为 {@link McpToolFeature}。
 * 未配置或远程不可用时保持空注册表，让 RAG 主链路降级为仅 KB 检索。
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKernelAutoConfiguration")
@EnableConfigurationProperties(McpHttpAdapterProperties.class)
@Conditional(NativeMcpEnabledCondition.class)
public class McpHttpAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(McpHttpAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(OkHttpClient.class)
    @ConditionalOnMissingBean(McpToolRegistryPort.class)
    public NativeMcpToolRegistry seahorseNativeMcpToolRegistry(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            McpHttpAdapterProperties properties,
            ObjectProvider<McpToolFeature> localToolFeatures) {
        List<McpToolFeature> features = new ArrayList<>(localToolFeatures.orderedStream().toList());
        OkHttpClient effectiveHttpClient = httpClient.newBuilder()
                .callTimeout(properties.getCallTimeout())
                .build();
        features.addAll(discoverRemoteFeatures(effectiveHttpClient, objectMapper, properties));
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
                                                        McpHttpAdapterProperties properties) {
        List<McpToolFeature> features = new ArrayList<>();
        for (McpHttpAdapterProperties.Server server : properties.getServers()) {
            features.addAll(discoverServerFeatures(httpClient, objectMapper, server));
        }
        return features;
    }

    private List<McpToolFeature> discoverServerFeatures(OkHttpClient httpClient,
                                                        ObjectMapper objectMapper,
                                                        McpHttpAdapterProperties.Server server) {
        if (!server.isEnabled() || server.getUrl().isBlank()) {
            return List.of();
        }
        try {
            StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                    httpClient, objectMapper, server.getName(), server.getUrl());
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
}
