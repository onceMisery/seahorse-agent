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

package com.miracle.ai.seahorse.agent.kernel.domain.agent;

import java.util.Map;
import java.util.Objects;

/**
 * Agent 工具调用请求：模型一次推理产生的"动作"。
 *
 * <p>{@link #arguments()} 在构造时做了防御性拷贝并对外暴露不可变视图。
 */
public record AgentToolCall(String id, String toolId, Map<String, Object> arguments) {

    public AgentToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AgentToolCall.id 不能为空");
        }
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("AgentToolCall.toolId 不能为空");
        }
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public static AgentToolCall of(String id, String toolId, Map<String, Object> arguments) {
        return new AgentToolCall(id, toolId, Objects.requireNonNullElse(arguments, Map.of()));
    }
}
