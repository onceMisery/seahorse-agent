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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import java.util.Objects;

/**
 * Agent 工具元数据：toolId、显示名、自然语言描述、JSON Schema。
 *
 * <p>jsonSchema 由具体 Tool 实现自行提供，用于喂给 OpenAI 兼容 function-calling
 * 的 {@code parameters} 字段；MUST 为合法 JSON 字符串，但本契约只做空白校验。
 */
public record ToolDescriptor(String toolId, String name, String description, String jsonSchema) {

    public ToolDescriptor {
        requireText(toolId, "toolId");
        requireText(name, "name");
        description = Objects.requireNonNullElse(description, "");
        jsonSchema = Objects.requireNonNullElse(jsonSchema, "{}");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ToolDescriptor." + field + " 不能为空");
        }
    }
}
