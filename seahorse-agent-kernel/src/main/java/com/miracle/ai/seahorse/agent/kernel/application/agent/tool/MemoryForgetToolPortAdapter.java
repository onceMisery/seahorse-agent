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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;

import java.util.Map;
import java.util.Objects;

public class MemoryForgetToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "memory_forget";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Forget Memory",
            "Delete a memory owned by the current server-side user scope.",
            """
                    {"type":"object","required":["memoryId","layer","reason"],"properties":{"memoryId":{"type":"string"},"layer":{"type":"string","enum":["short_term","long_term","semantic"]},"reason":{"type":"string"}}}
                    """);

    private final MemoryManagementInboundPort memoryManagementPort;
    private final AgentToolJsonSupport jsonSupport;

    public MemoryForgetToolPortAdapter(MemoryManagementInboundPort memoryManagementPort,
                                       AgentToolJsonSupport jsonSupport) {
        this.memoryManagementPort = Objects.requireNonNull(memoryManagementPort, "memoryManagementPort must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            String userId = jsonSupport.string(arguments, "_seahorseUserId");
            String layer = jsonSupport.string(arguments, "layer");
            String memoryId = jsonSupport.string(arguments, "memoryId");
            if (userId.isBlank()) {
                return ToolInvocationResult.failed("server user scope is missing");
            }
            if (layer.isBlank() || memoryId.isBlank()) {
                return ToolInvocationResult.failed("layer and memoryId are required");
            }
            MemoryRecord record = memoryManagementPort.findMemory(layer, memoryId).orElse(null);
            if (record == null) {
                return ToolInvocationResult.failed("memory not found");
            }
            Object owner = record.metadata().get("userId");
            if (owner != null && !userId.equals(owner.toString())) {
                return ToolInvocationResult.failed("memory does not belong to current user scope");
            }
            boolean deleted = memoryManagementPort.deleteMemory(layer, memoryId);
            return ToolInvocationResult.ok(jsonSupport.write(Map.of(
                    "success", deleted,
                    "memoryId", memoryId,
                    "layer", layer)));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("memory_forget failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }
}
