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

import java.util.Objects;

/**
 * MCP 工具执行结果。
 * <p>
 * 结果对象保留明确状态和可拼装进 Prompt 的内容。失败时 content 固定为空字符串，
 * 由调用方决定是否记录错误或降级为仅 KB 上下文回答。
 *
 * @param toolId  工具 ID
 * @param status  执行状态
 * @param content 工具输出内容
 * @param message 状态说明
 */
public record McpToolExecutionResult(
        String toolId,
        McpToolExecutionStatus status,
        String content,
        String message
) {

    private static final String MSG_SUCCESS = "SUCCESS";
    private static final String MSG_TOOL_NOT_FOUND = "TOOL_NOT_FOUND";

    /**
     * 构造不可变结果。
     */
    public McpToolExecutionResult {
        toolId = Objects.requireNonNullElse(toolId, "");
        status = Objects.requireNonNullElse(status, McpToolExecutionStatus.EXECUTION_FAILED);
        content = Objects.requireNonNullElse(content, "");
        message = Objects.requireNonNullElse(message, "");
    }

    /**
     * 是否执行成功。
     *
     * @return true 表示成功
     */
    public boolean success() {
        return McpToolExecutionStatus.SUCCESS.equals(status);
    }

    /**
     * 创建成功结果。
     *
     * @param toolId  工具 ID
     * @param content 输出内容
     * @return 成功结果
     */
    public static McpToolExecutionResult success(String toolId, String content) {
        return new McpToolExecutionResult(toolId, McpToolExecutionStatus.SUCCESS, content, MSG_SUCCESS);
    }

    /**
     * 创建工具缺失结果。
     *
     * @param toolId 工具 ID
     * @return 工具缺失结果
     */
    public static McpToolExecutionResult toolNotFound(String toolId) {
        return new McpToolExecutionResult(toolId, McpToolExecutionStatus.TOOL_NOT_FOUND, "", MSG_TOOL_NOT_FOUND);
    }

    /**
     * 创建执行失败结果。
     *
     * @param toolId  工具 ID
     * @param message 失败说明
     * @return 执行失败结果
     */
    public static McpToolExecutionResult failed(String toolId, String message) {
        return new McpToolExecutionResult(toolId, McpToolExecutionStatus.EXECUTION_FAILED, "",
                Objects.requireNonNullElse(message, ""));
    }
}
