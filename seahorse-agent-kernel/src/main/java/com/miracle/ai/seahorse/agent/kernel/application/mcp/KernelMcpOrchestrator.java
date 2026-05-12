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

package com.miracle.ai.seahorse.agent.kernel.application.mcp;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * L1 MCP 工具编排器。
 * <p>
 * 该类负责工具元数据查找、参数抽取、并发执行、异常封装和降级结果生成。它不直接依赖
 * HTTP、OkHttp、远程服务地址等技术细节，符合 L1 内核不依赖具体 SDK 的边界要求。
 */
public class KernelMcpOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(KernelMcpOrchestrator.class);

    private final McpToolRegistryPort toolRegistry;
    private final McpParameterExtractionPort parameterExtractionPort;
    private final Executor mcpExecutor;

    public KernelMcpOrchestrator(McpToolRegistryPort toolRegistry) {
        this(toolRegistry, McpParameterExtractionPort.noop(), Runnable::run);
    }

    public KernelMcpOrchestrator(McpToolRegistryPort toolRegistry,
                                 McpParameterExtractionPort parameterExtractionPort,
                                 Executor mcpExecutor) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "MCP 工具注册端口不能为空");
        this.parameterExtractionPort = Objects.requireNonNullElse(parameterExtractionPort,
                McpParameterExtractionPort.noop());
        this.mcpExecutor = Objects.requireNonNull(mcpExecutor, "MCP 执行线程池不能为空");
    }

    /**
     * 执行单个 MCP 工具请求。
     *
     * @param request 工具执行请求
     * @return 工具执行结果
     */
    public McpToolExecutionResult execute(McpToolExecutionRequest request) {
        Objects.requireNonNull(request, "MCP 工具执行请求不能为空");
        Optional<McpToolExecutorPort> executor = toolRegistry.findExecutor(request.toolId());
        if (executor.isEmpty()) {
            return McpToolExecutionResult.toolNotFound(request.toolId());
        }
        return executeSafely(executor.get(), request);
    }

    /**
     * 并发执行一个子问题命中的 MCP 工具。
     *
     * @param question        子问题文本
     * @param mcpIntentScores MCP 意图候选
     * @return 工具执行结果列表
     */
    public List<McpToolExecutionResult> executeTools(String question, List<IntentScore> mcpIntentScores) {
        List<IntentScore> safeScores = Objects.requireNonNullElse(mcpIntentScores, List.of());
        if (safeScores.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<McpToolExecutionResult>> futures = safeScores.stream()
                .map(score -> CompletableFuture.supplyAsync(() -> executeIntentTool(question, score), mcpExecutor))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private McpToolExecutionResult executeIntentTool(String question, IntentScore score) {
        IntentNode node = score == null ? null : score.getNode();
        String toolId = node == null ? "" : Objects.requireNonNullElse(node.getMcpToolId(), "");
        if (toolId.isBlank()) {
            return null;
        }
        Optional<McpToolDescriptor> descriptor = toolRegistry.findTool(toolId);
        if (descriptor.isEmpty()) {
            LOG.warn("MCP 工具不存在: {}", toolId);
            return McpToolExecutionResult.toolNotFound(toolId);
        }
        Map<String, Object> arguments = extractArguments(question, node, descriptor.get());
        return execute(new McpToolExecutionRequest(toolId, question, arguments));
    }

    private Map<String, Object> extractArguments(String question, IntentNode node, McpToolDescriptor descriptor) {
        try {
            McpParameterExtractionRequest request = new McpParameterExtractionRequest(
                    descriptor, question, node.getParamPromptTemplate());
            return parameterExtractionPort.extract(request);
        } catch (Exception ex) {
            LOG.error("MCP 工具 {} 参数抽取失败，按空参数降级", descriptor.toolId(), ex);
            return Map.of();
        }
    }

    private McpToolExecutionResult executeSafely(McpToolExecutorPort executor, McpToolExecutionRequest request) {
        try {
            return executor.execute(request);
        } catch (Exception ex) {
            LOG.error("MCP 工具 {} 执行失败，按失败结果降级", request.toolId(), ex);
            return McpToolExecutionResult.failed(request.toolId(), ex.getMessage());
        }
    }
}
