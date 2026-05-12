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

import java.util.Map;

/**
 * MCP 参数抽取端口。
 * <p>
 * 参数抽取可以由 LLM、规则或远程服务完成，内核只消费结构化参数。
 */
public interface McpParameterExtractionPort {

    /**
     * 从用户问题中抽取工具参数。
     *
     * @param toolId   工具 ID
     * @param question 用户问题
     * @return 参数 Map
     */
    Map<String, Object> extract(String toolId, String question);

    /**
     * 根据工具元数据和用户问题抽取工具参数。
     *
     * @param request 参数抽取请求
     * @return 参数 Map
     */
    default Map<String, Object> extract(McpParameterExtractionRequest request) {
        return extract(request.tool().toolId(), request.userQuestion());
    }

    /**
     * 创建空实现。缺少参数抽取器时返回空参数，由具体工具自行做参数校验和降级。
     *
     * @return 空参数抽取端口
     */
    static McpParameterExtractionPort noop() {
        return (toolId, question) -> Map.of();
    }
}
