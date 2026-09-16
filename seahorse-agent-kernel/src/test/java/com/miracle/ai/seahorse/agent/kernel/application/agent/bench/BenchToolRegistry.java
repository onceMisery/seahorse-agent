/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.bench;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基准用的工具注册表：注册一个恒定成功的检索工具。
 *
 * <p>必须让工具调用<b>成功</b>，否则观察结果全是失败、模型会一直重试，
 * 测出来的步数反映的是故障恢复而不是开关效果。
 */
final class BenchToolRegistry implements ToolRegistryPort {

    private final Map<String, ToolPort> tools = new LinkedHashMap<>();
    private final Map<String, ToolDescriptor> descriptors = new LinkedHashMap<>();

    BenchToolRegistry() {
        ToolDescriptor descriptor = new ToolDescriptor(
                ScriptedModel.WORK_TOOL_ID,
                "bench_search",
                "基准用检索工具",
                "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}");
        ToolPort port = (toolCallId, toolId, arguments) ->
                ToolInvocationResult.ok("观察结果: " + arguments.getOrDefault("q", ""));
        descriptors.put(descriptor.toolId(), descriptor);
        tools.put(descriptor.toolId(), port);
    }

    @Override
    public List<ToolDescriptor> listTools() {
        return List.copyOf(descriptors.values());
    }

    @Override
    public Optional<ToolPort> find(String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }
}
