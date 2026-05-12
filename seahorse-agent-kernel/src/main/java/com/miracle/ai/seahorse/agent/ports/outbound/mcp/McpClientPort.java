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

package com.miracle.ai.seahorse.agent.ports.outbound.mcp;

import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;

/**
 * 远程 MCP 客户端端口。
 * <p>
 * 该端口隔离远程 MCP 协议，L3 Adapter 可以用 HTTP、RPC 或其他协议实现。
 */
public interface McpClientPort {

    /**
     * 调用远程 MCP 工具。
     *
     * @param request 工具执行请求
     * @return 工具执行结果
     */
    McpToolExecutionResult call(McpToolExecutionRequest request);
}
