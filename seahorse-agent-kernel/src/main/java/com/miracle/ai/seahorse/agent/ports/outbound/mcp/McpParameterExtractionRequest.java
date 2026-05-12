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

import java.util.Objects;

/**
 * MCP 参数抽取请求。
 * <p>
 * 将工具元数据、用户问题和意图节点上的自定义提示词聚合成单个请求，避免内核方法参数膨胀。
 *
 * @param tool                 工具元数据
 * @param userQuestion         用户原始问题
 * @param customPromptTemplate 自定义参数抽取提示词
 */
public record McpParameterExtractionRequest(McpToolDescriptor tool,
                                            String userQuestion,
                                            String customPromptTemplate) {

    public McpParameterExtractionRequest {
        tool = Objects.requireNonNull(tool, "MCP 工具元数据不能为空");
        userQuestion = Objects.requireNonNullElse(userQuestion, "");
        customPromptTemplate = Objects.requireNonNullElse(customPromptTemplate, "");
    }
}
