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

package com.miracle.ai.seahorse.agent.kernel.feature.mcp;

import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentKind;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 内核编排器契约测试。
 * <p>
 * MCP 是 RAG 主链路的增强能力，工具缺失、参数抽取异常或执行异常时必须返回可解释的失败结果，
 * 不能反向影响知识库检索和最终 Prompt 组装。
 */
class KernelMcpOrchestratorTests {

    private static final String TOOL_ID = "weather";
    private static final String MISSING_TOOL_ID = "missing";
    private static final String PARAM_CITY = "city";

    @Test
    void shouldReturnToolNotFoundWhenToolDoesNotExist() {
        KernelMcpOrchestrator orchestrator = new KernelMcpOrchestrator(new EmptyToolRegistry());

        McpToolExecutionResult result = orchestrator.execute(new McpToolExecutionRequest(MISSING_TOOL_ID, Map.of()));

        Assertions.assertFalse(result.success());
        Assertions.assertEquals(McpToolExecutionStatus.TOOL_NOT_FOUND, result.status());
        Assertions.assertTrue(result.content().isBlank());
    }

    @Test
    void shouldConvertToolExceptionToFailedResult() {
        KernelMcpOrchestrator orchestrator = new KernelMcpOrchestrator(new SingleToolRegistry(request -> {
            throw new IllegalStateException("远程工具不可用");
        }));

        McpToolExecutionResult result = orchestrator.execute(new McpToolExecutionRequest(TOOL_ID, Map.of()));

        Assertions.assertFalse(result.success());
        Assertions.assertEquals(McpToolExecutionStatus.EXECUTION_FAILED, result.status());
        Assertions.assertTrue(result.content().isBlank());
    }

    @Test
    void shouldReturnToolContentWhenExecutionSucceeds() {
        KernelMcpOrchestrator orchestrator = new KernelMcpOrchestrator(new SingleToolRegistry(
                request -> McpToolExecutionResult.success(request.toolId(), "晴天")));

        McpToolExecutionResult result = orchestrator.execute(new McpToolExecutionRequest(TOOL_ID, Map.of()));

        Assertions.assertTrue(result.success());
        Assertions.assertEquals(McpToolExecutionStatus.SUCCESS, result.status());
        Assertions.assertEquals("晴天", result.content());
    }

    @Test
    void shouldExtractParametersAndExecuteMatchedIntentTools() {
        KernelMcpOrchestrator orchestrator = new KernelMcpOrchestrator(
                new SingleToolRegistry(request -> McpToolExecutionResult.success(
                        request.toolId(), request.arguments().get(PARAM_CITY) + "：晴天")),
                (toolId, question) -> Map.of(PARAM_CITY, "杭州"),
                Runnable::run);

        List<McpToolExecutionResult> results = orchestrator.executeTools("杭州天气", List.of(weatherIntentScore()));

        Assertions.assertEquals(1, results.size());
        Assertions.assertTrue(results.get(0).success());
        Assertions.assertEquals("杭州：晴天", results.get(0).content());
    }

    private static final class EmptyToolRegistry implements McpToolRegistryPort {

        @Override
        public Optional<McpToolExecutorPort> findExecutor(String toolId) {
            return Optional.empty();
        }
    }

    private record SingleToolRegistry(McpToolExecutorPort executor) implements McpToolRegistryPort {

        @Override
        public Optional<McpToolExecutorPort> findExecutor(String toolId) {
            if (TOOL_ID.equals(toolId)) {
                return Optional.of(executor);
            }
            return Optional.empty();
        }

        @Override
        public Optional<McpToolDescriptor> findTool(String toolId) {
            if (TOOL_ID.equals(toolId)) {
                return Optional.of(new McpToolDescriptor(TOOL_ID, "天气查询", Map.of()));
            }
            return Optional.empty();
        }
    }

    private IntentScore weatherIntentScore() {
        return IntentScore.builder()
                .node(IntentNode.builder()
                        .id("weather-node")
                        .kind(IntentKind.MCP)
                        .mcpToolId(TOOL_ID)
                        .build())
                .score(0.95D)
                .build();
    }
}
