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

import java.util.List;
import java.util.Optional;

/**
 * 工具注册中心出站端口。KernelAgentLoop 通过此端口枚举可用工具与按 toolId 派发执行。
 */
public interface ToolRegistryPort {

    List<ToolDescriptor> listTools();

    Optional<ToolPort> find(String toolId);

    static ToolRegistryPort empty() {
        return new ToolRegistryPort() {
            @Override
            public List<ToolDescriptor> listTools() {
                return List.of();
            }

            @Override
            public Optional<ToolPort> find(String toolId) {
                return Optional.empty();
            }
        };
    }
}
