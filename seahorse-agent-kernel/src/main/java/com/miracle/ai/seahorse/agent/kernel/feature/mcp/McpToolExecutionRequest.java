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

import java.util.Map;
import java.util.Objects;

/**
 * MCP 工具执行请求。
 * <p>
 * 请求对象把 toolId 和参数收束为单个 DTO，避免内核方法参数膨胀。
 *
 * @param toolId    工具 ID
 * @param arguments 工具参数
 */
public record McpToolExecutionRequest(String toolId, String userQuestion, Map<String, Object> arguments) {

    public McpToolExecutionRequest(String toolId, Map<String, Object> arguments) {
        this(toolId, "", arguments);
    }

    /**
     * 构造不可变请求。
     */
    public McpToolExecutionRequest {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("MCP 工具 ID 不能为空");
        }
        userQuestion = Objects.requireNonNullElse(userQuestion, "");
        arguments = Map.copyOf(Objects.requireNonNullElse(arguments, Map.of()));
    }
}
