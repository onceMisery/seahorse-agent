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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 单进程内存工具注册中心。第一版承载 ToolPort，无需引入分布式存储；
 * 上层通过 Spring ApplicationRunner 在启动时注册 Tool。
 */
public class InMemoryToolRegistry implements ToolRegistryPort {

    private final ConcurrentHashMap<String, Registration> registry = new ConcurrentHashMap<>();

    public void register(ToolDescriptor descriptor, ToolPort port) {
        Objects.requireNonNull(descriptor, "descriptor 不能为空");
        Objects.requireNonNull(port, "port 不能为空");
        Registration prev = registry.putIfAbsent(descriptor.toolId(),
                new Registration(descriptor, port));
        if (prev != null) {
            throw new IllegalStateException("Tool 已注册: " + descriptor.toolId());
        }
    }

    @Override
    public List<ToolDescriptor> listTools() {
        return registry.values().stream()
                .map(Registration::descriptor)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<ToolPort> find(String toolId) {
        if (toolId == null) {
            return Optional.empty();
        }
        Registration reg = registry.get(toolId);
        return reg == null ? Optional.empty() : Optional.of(reg.port());
    }

    private record Registration(ToolDescriptor descriptor, ToolPort port) { }
}
