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
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.McpToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task A13 契约测试：AgentLoop Spring Boot 自动装配与 MCP allowlist。
 */
class SeahorseAgentKernelAgentAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAutoConfiguration.class));

    @Test
    void shouldKeepAgentModeDisabledByDefault() {
        contextRunner.withUserConfiguration(StreamingModelConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(KernelAgentLoop.class);
                    assertThat(context).doesNotHaveBean(ToolRegistryPort.class);
                });
    }

    @Test
    void shouldCreateAgentLoopWhenAgentModeIsEnabled() {
        contextRunner.withUserConfiguration(StreamingModelConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.chat.agent-mode-enabled=true",
                        "seahorse-agent.chat.agent.max-steps=9",
                        "seahorse-agent.chat.agent.per-tool-timeout=12s",
                        "seahorse-agent.chat.agent.max-parallel-tools=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InMemoryToolRegistry.class);
                    assertThat(context).hasSingleBean(ToolRegistryPort.class);
                    assertThat(context).hasSingleBean(KernelAgentLoop.class);

                    KernelAgentLoopOptions options = context.getBean(KernelAgentLoopOptions.class);
                    assertThat(options.maxSteps()).isEqualTo(9);
                    assertThat(options.perToolTimeout()).isEqualTo(Duration.ofSeconds(12));
                    assertThat(options.maxParallelTools()).isEqualTo(1);
                });
    }

    @Test
    void shouldNotExposeMcpToolsWhenIncludeListIsEmpty() throws Exception {
        contextRunner.withUserConfiguration(StreamingModelConfiguration.class, McpConfiguration.class)
                .withPropertyValues("seahorse-agent.chat.agent-mode-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(McpToolPortAdapter.class);

                    context.getBean(ApplicationRunner.class).run(null);

                    InMemoryToolRegistry registry = context.getBean(InMemoryToolRegistry.class);
                    assertThat(registry.listTools()).isEmpty();
                });
    }

    @Test
    void shouldRegisterOnlyIncludedMcpTools() throws Exception {
        contextRunner.withUserConfiguration(StreamingModelConfiguration.class, McpConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.chat.agent-mode-enabled=true",
                        "seahorse-agent.chat.agent.tools.mcp.include=weather_query, missing_tool")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    context.getBean(ApplicationRunner.class).run(null);

                    InMemoryToolRegistry registry = context.getBean(InMemoryToolRegistry.class);
                    assertThat(registry.listTools())
                            .singleElement()
                            .satisfies(tool -> {
                                assertThat(tool.toolId()).isEqualTo("weather_query");
                                assertThat(tool.name()).isEqualTo("weather_query");
                                assertThat(tool.description()).isEqualTo("查询天气");
                                assertThat(tool.jsonSchema()).contains("\"city\"");
                            });
                    assertThat(registry.find("weather_query")).isPresent();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class StreamingModelConfiguration {

        @Bean
        StreamingChatModelPort streamingChatModelPort() {
            return StreamingChatModelPort.noop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class McpConfiguration {

        @Bean
        McpToolRegistryPort mcpToolRegistryPort() {
            return new McpToolRegistryPort() {
                @Override
                public Optional<McpToolExecutorPort> findExecutor(String toolId) {
                    if (!"weather_query".equals(toolId)) {
                        return Optional.empty();
                    }
                    return Optional.of(request -> McpToolExecutionResult.success(request.toolId(), "{\"ok\":true}"));
                }

                @Override
                public Optional<McpToolDescriptor> findTool(String toolId) {
                    if (!"weather_query".equals(toolId)) {
                        return Optional.empty();
                    }
                    return Optional.of(new McpToolDescriptor(
                            "weather_query",
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
}
