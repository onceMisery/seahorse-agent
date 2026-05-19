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

import java.util.Map;

/**
 * Agent 工具出站端口：单个工具的执行入口。
 *
 * <p>实现方负责把 LLM 给的 {@code arguments}（已按 ToolDescriptor.jsonSchema 解析为 Map）
 * 转译到具体后端（MCP / 检索 / 记忆 / 自定义），并把结果序列化成字符串放进
 * {@link ToolInvocationResult#content()}。
 *
 * <p>不抛异常的契约：实现内部应捕获所有异常并转 {@link ToolInvocationResult#failed(String)},
 * 让 KernelAgentLoop 把失败作为 observation 喂给 LLM 继续推理。
 */
public interface ToolPort {

    ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments);

    /**
     * 默认"工具未注册"实现，便于 ToolRegistryPort 缺失时返回结构化错误。
     */
    static ToolPort notFound(String toolId) {
        return (callId, requested, arguments) ->
                ToolInvocationResult.failed("Tool 未注册: " + toolId);
    }
}
