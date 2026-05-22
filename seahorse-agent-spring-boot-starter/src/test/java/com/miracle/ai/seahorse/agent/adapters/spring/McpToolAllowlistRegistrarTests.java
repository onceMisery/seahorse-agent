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

import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolAllowlistRegistrarTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-23T00:00:00Z");
    private static final String WEATHER_TOOL_ID = "weather_query";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAutoConfiguration.class));

    @Test
    void shouldWriteAllowlistedMcpToolToCatalogWithDefaultPolicyMetadata() {
        contextRunner.withUserConfiguration(McpCatalogConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.chat.agent-mode-enabled=true",
                        "seahorse-agent.chat.agent.tools.mcp.include=weather_query, missing_tool")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    context.getBeansOfType(ApplicationRunner.class)
                            .values()
                            .forEach(McpToolAllowlistRegistrarTests::run);

                    InMemoryToolRegistry registry = context.getBean(InMemoryToolRegistry.class);
                    assertThat(registry.find(WEATHER_TOOL_ID)).isPresent();

                    RecordingToolCatalogRepository catalog =
                            context.getBean(RecordingToolCatalogRepository.class);
                    assertThat(catalog.savedEntries()).hasSize(1);
                    assertThat(catalog.findById(WEATHER_TOOL_ID)).hasValueSatisfying(entry -> {
                        assertThat(entry.toolId()).isEqualTo(WEATHER_TOOL_ID);
                        assertThat(entry.provider()).isEqualTo(ToolProvider.MCP);
                        assertThat(entry.name()).isEqualTo(WEATHER_TOOL_ID);
                        assertThat(entry.description()).isEqualTo("查询天气");
                        assertThat(entry.schemaJson()).contains("\"city\"", "\"required\":[\"city\"]");
                        assertThat(entry.riskLevel()).isEqualTo(ToolRiskLevel.MEDIUM);
                        assertThat(entry.actionType()).isEqualTo(ToolActionType.EXECUTE);
                        assertThat(entry.resourceType()).isEqualTo("MCP");
                        assertThat(entry.ownerTeam()).isEqualTo("mcp");
                        assertThat(entry.enabled()).isTrue();
                        assertThat(entry.requiresApproval()).isFalse();
                        assertThat(entry.createdAt()).isEqualTo(FIXED_NOW);
                        assertThat(entry.updatedAt()).isEqualTo(FIXED_NOW);
                    });
                    assertThat(catalog.findById("missing_tool")).isEmpty();
                });
    }

    private static void run(ApplicationRunner runner) {
        try {
            runner.run(null);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class McpCatalogConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }

        @Bean
        RecordingToolCatalogRepository toolCatalogRepositoryPort() {
            return new RecordingToolCatalogRepository();
        }

        @Bean
        McpToolRegistryPort mcpToolRegistryPort() {
            return new McpToolRegistryPort() {
                @Override
                public Optional<McpToolExecutorPort> findExecutor(String toolId) {
                    if (!WEATHER_TOOL_ID.equals(toolId)) {
                        return Optional.empty();
                    }
                    return Optional.of(request -> McpToolExecutionResult.success(request.toolId(), "{\"ok\":true}"));
                }

                @Override
                public Optional<McpToolDescriptor> findTool(String toolId) {
                    if (!WEATHER_TOOL_ID.equals(toolId)) {
                        return Optional.empty();
                    }
                    return Optional.of(new McpToolDescriptor(
                            WEATHER_TOOL_ID,
                            "查询天气",
                            Map.of("city", new McpToolDescriptor.Parameter(
                                    "城市", "string", true, null, List.of()))));
                }
            };
        }

        @Bean
        KernelMcpOrchestrator kernelMcpOrchestrator(McpToolRegistryPort registryPort) {
            return new KernelMcpOrchestrator(registryPort);
        }
    }

    private static final class RecordingToolCatalogRepository implements ToolCatalogRepositoryPort {

        private final Map<String, ToolCatalogEntry> entries = new LinkedHashMap<>();

        @Override
        public void save(ToolCatalogEntry entry) {
            entries.put(entry.toolId(), entry);
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return Optional.ofNullable(entries.get(toolId));
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
        }

        List<ToolCatalogEntry> savedEntries() {
            return List.copyOf(entries.values());
        }
    }
}
