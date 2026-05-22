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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.CatalogBackedToolPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.LocalToolGatewayPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.McpToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.AgentToolJsonSupport;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GetDateTimeToolPortAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryForgetToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryReadToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryWriteToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.QueryMetadataToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SearchKnowledgeBaseToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * AgentLoop 自动装配。默认关闭，仅在显式打开 agent-mode-enabled 后生效。
 */
@AutoConfiguration
@AutoConfigureAfter({
        SeahorseAgentKernelMemoryAutoConfiguration.class,
        SeahorseAgentKernelRegistryAutoConfiguration.class,
        SeahorseAgentKernelRetrievalAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentKernelAgentAutoConfiguration {

    private static final String PROP_AGENT_ENABLED = "seahorse-agent.chat.agent-mode-enabled";
    private static final String PROP_MAX_STEPS = "seahorse-agent.chat.agent.max-steps";
    private static final String PROP_PER_TOOL_TIMEOUT = "seahorse-agent.chat.agent.per-tool-timeout";
    private static final String PROP_MAX_PARALLEL_TOOLS = "seahorse-agent.chat.agent.max-parallel-tools";
    private static final String PROP_MCP_INCLUDE = "seahorse-agent.chat.agent.tools.mcp.include";
    private static final String PROP_SEARCH_TOOLS_ENABLED = "seahorse-agent.chat.agent.tools.search.enabled";
    private static final String PROP_MEMORY_TOOLS_ENABLED = "seahorse-agent.chat.agent.tools.memory.enabled";

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnMissingBean(ToolRegistryPort.class)
    public InMemoryToolRegistry seahorseAgentToolRegistryPort() {
        return new InMemoryToolRegistry();
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnMissingBean
    public KernelAgentLoopOptions seahorseKernelAgentLoopOptions(Environment environment) {
        return KernelAgentLoopOptions.builder()
                .maxSteps(environment.getProperty(PROP_MAX_STEPS, Integer.class, 6))
                .perToolTimeout(parseDuration(environment.getProperty(PROP_PER_TOOL_TIMEOUT), Duration.ofSeconds(30)))
                .maxParallelTools(environment.getProperty(PROP_MAX_PARALLEL_TOOLS, Integer.class, 1))
                .build();
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(StreamingChatModelPort.class)
    @ConditionalOnMissingBean
    public KernelAgentLoop seahorseKernelAgentLoop(StreamingChatModelPort modelPort,
                                                   ToolRegistryPort toolRegistry,
                                                   KernelAgentLoopOptions options,
                                                   ObjectProvider<KernelRagTraceRecorder> traceRecorder,
                                                   ObjectProvider<ContextWeaverPort> contextWeaverPort,
                                                   ObjectProvider<ToolGatewayPort> toolGatewayPort,
                                                   ObjectProvider<AgentRunStepRecorder> runStepRecorder) {
        return new KernelAgentLoop(modelPort, toolRegistry,
                toolGatewayPort.getIfAvailable(() -> new LocalToolGatewayPort(toolRegistry)),
                options,
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop),
                contextWeaverPort.getIfAvailable(
                        com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver::new),
                runStepRecorder.getIfAvailable(AgentRunStepRecorder::noop));
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean({ToolCatalogRepositoryPort.class, AgentToolBindingRepositoryPort.class})
    @ConditionalOnMissingBean
    public ToolPolicyPort seahorseCatalogBackedToolPolicyPort(
            ToolCatalogRepositoryPort toolCatalogRepositoryPort,
            AgentToolBindingRepositoryPort agentToolBindingRepositoryPort) {
        return new CatalogBackedToolPolicyPort(toolCatalogRepositoryPort, agentToolBindingRepositoryPort);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(ToolRegistryPort.class)
    @ConditionalOnMissingBean
    public ToolGatewayPort seahorseToolGatewayPort(ToolRegistryPort toolRegistry,
                                                   ObjectProvider<ToolPolicyPort> toolPolicyPort) {
        return new LocalToolGatewayPort(toolRegistry, toolPolicyPort.getIfAvailable(ToolPolicyPort::defaults));
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnMissingBean
    public AgentRunStepRecorder seahorseAgentRunStepRecorder(
            ObjectProvider<AgentRunRepositoryPort> agentRunRepositoryPort,
            ObjectProvider<Clock> clockProvider) {
        AgentRunRepositoryPort repository = agentRunRepositoryPort.getIfAvailable();
        if (repository == null) {
            return AgentRunStepRecorder.noop();
        }
        return new RepositoryAgentRunStepRecorder(repository, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnMissingBean
    public AgentToolJsonSupport seahorseAgentToolJsonSupport(ObjectProvider<ObjectMapper> objectMapper) {
        return new AgentToolJsonSupport(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(KernelRetrievalEngine.class)
    @ConditionalOnProperty(name = PROP_SEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SearchKnowledgeBaseToolPortAdapter seahorseSearchKnowledgeBaseToolPortAdapter(
            KernelRetrievalEngine retrievalEngine,
            AgentToolJsonSupport jsonSupport) {
        return new SearchKnowledgeBaseToolPortAdapter(retrievalEngine, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnProperty(name = PROP_SEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public QueryMetadataToolPortAdapter seahorseQueryMetadataToolPortAdapter(AgentToolJsonSupport jsonSupport) {
        return new QueryMetadataToolPortAdapter(jsonSupport);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(MemoryEnginePort.class)
    @ConditionalOnProperty(name = PROP_MEMORY_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MemoryReadToolPortAdapter seahorseMemoryReadToolPortAdapter(MemoryEnginePort memoryEnginePort,
                                                                       AgentToolJsonSupport jsonSupport) {
        return new MemoryReadToolPortAdapter(memoryEnginePort, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(MemoryEnginePort.class)
    @ConditionalOnProperty(name = PROP_MEMORY_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MemoryWriteToolPortAdapter seahorseMemoryWriteToolPortAdapter(MemoryEnginePort memoryEnginePort,
                                                                         ObjectProvider<MemoryIngestionWorkflowPort> memoryIngestionWorkflowPort,
                                                                         ObjectProvider<MemoryGovernanceInboundPort> memoryGovernancePort,
                                                                         AgentToolJsonSupport jsonSupport) {
        return new MemoryWriteToolPortAdapter(
                memoryEnginePort,
                memoryIngestionWorkflowPort.getIfAvailable(),
                memoryGovernancePort.getIfAvailable(),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(MemoryManagementInboundPort.class)
    @ConditionalOnProperty(name = PROP_MEMORY_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MemoryForgetToolPortAdapter seahorseMemoryForgetToolPortAdapter(
            MemoryManagementInboundPort memoryManagementPort,
            AgentToolJsonSupport jsonSupport) {
        return new MemoryForgetToolPortAdapter(memoryManagementPort, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnMissingBean
    public GetDateTimeToolPortAdapter seahorseGetDateTimeToolPortAdapter() {
        return new GetDateTimeToolPortAdapter();
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(ToolRegistryPort.class)
    @ConditionalOnMissingBean
    public BuiltInAgentToolRegistrar seahorseBuiltInAgentToolRegistrar(
            ToolRegistryPort toolRegistry,
            ObjectProvider<DescribedToolPort> toolPorts) {
        return new BuiltInAgentToolRegistrar(toolRegistry, toolPorts);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean(KernelMcpOrchestrator.class)
    @ConditionalOnMissingBean
    public McpToolPortAdapter seahorseMcpToolPortAdapter(KernelMcpOrchestrator orchestrator) {
        return new McpToolPortAdapter(orchestrator);
    }

    @Bean
    @ConditionalOnAgentModeEnabled
    @ConditionalOnBean({McpToolPortAdapter.class, McpToolRegistryPort.class, ToolRegistryPort.class})
    @ConditionalOnMissingBean
    public McpToolAllowlistRegistrar seahorseMcpToolAllowlistRegistrar(McpToolPortAdapter adapter,
                                                                        McpToolRegistryPort mcpRegistry,
                                                                        ToolRegistryPort toolRegistry,
                                                                        Environment environment,
                                                                        ObjectProvider<ObjectMapper> objectMapper) {
        return new McpToolAllowlistRegistrar(
                adapter,
                mcpRegistry,
                toolRegistry,
                parseCsv(environment.getProperty(PROP_MCP_INCLUDE, "")),
                objectMapper.getIfAvailable(ObjectMapper::new));
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return DurationStyle.detectAndParse(value);
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @ConditionalOnProperty(name = PROP_AGENT_ENABLED, havingValue = "true")
    private @interface ConditionalOnAgentModeEnabled {
    }
}
