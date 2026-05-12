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

import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolFeature;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpClientPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;

import java.util.Objects;

/**
 * 远程 MCP 工具 Feature。
 *
 * <p>该类只持有工具元数据和远程客户端引用，业务编排仍由 kernel MCP orchestrator 统一完成。
 */
public class RemoteMcpToolFeature implements McpToolFeature {

    private final McpToolDescriptor descriptor;
    private final McpClientPort client;

    public RemoteMcpToolFeature(McpToolDescriptor descriptor, McpClientPort client) {
        this.descriptor = Objects.requireNonNull(descriptor, "MCP 工具元数据不能为空");
        this.client = Objects.requireNonNull(client, "MCP HTTP 客户端不能为空");
    }

    @Override
    public McpToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public McpToolExecutionResult execute(McpToolExecutionRequest request) {
        return client.call(request);
    }
}
