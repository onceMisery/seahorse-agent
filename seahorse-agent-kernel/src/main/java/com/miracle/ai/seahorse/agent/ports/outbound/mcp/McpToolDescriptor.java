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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 工具元数据。
 * <p>
 * 该对象只描述参数抽取与编排所需的稳定契约，不暴露旧 MCP SDK 或远程协议对象。
 *
 * @param toolId      工具 ID
 * @param description 工具描述
 * @param parameters  参数定义
 */
public record McpToolDescriptor(String toolId, String description, Map<String, Parameter> parameters) {

    public McpToolDescriptor {
        toolId = Objects.requireNonNullElse(toolId, "");
        description = Objects.requireNonNullElse(description, "");
        parameters = Map.copyOf(Objects.requireNonNullElse(parameters, Map.of()));
    }

    /**
     * MCP 工具参数定义。
     *
     * @param description  参数描述
     * @param type         参数类型
     * @param required     是否必填
     * @param defaultValue 默认值
     * @param enumValues   枚举值
     */
    public record Parameter(String description,
                            String type,
                            boolean required,
                            Object defaultValue,
                            List<String> enumValues) {

        public Parameter {
            description = Objects.requireNonNullElse(description, "");
            type = Objects.requireNonNullElse(type, "string");
            enumValues = List.copyOf(Objects.requireNonNullElse(enumValues, List.of()));
        }
    }
}
