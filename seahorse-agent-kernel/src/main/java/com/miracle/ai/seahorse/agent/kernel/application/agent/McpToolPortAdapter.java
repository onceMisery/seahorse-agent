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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;

import java.util.Map;
import java.util.Objects;

/**
 * 将现有 MCP 编排器适配为 Agent 工具端口。
 */
public class McpToolPortAdapter implements ToolPort {

    private final KernelMcpOrchestrator orchestrator;

    public McpToolPortAdapter(KernelMcpOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "MCP 编排器不能为 null");
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            McpToolExecutionResult result = orchestrator.execute(
                    new McpToolExecutionRequest(toolId, "", arguments));
            return result.success()
                    ? ToolInvocationResult.ok(result.content())
                    : ToolInvocationResult.failed(result.message());
        } catch (Exception ex) {
            return ToolInvocationResult.failed(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }
}
