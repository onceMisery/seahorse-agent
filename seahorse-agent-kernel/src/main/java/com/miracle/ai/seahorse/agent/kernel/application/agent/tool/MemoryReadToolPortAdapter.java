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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MemoryReadToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "memory_read";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_ID, "Read Memory",
            "Read current user's Seahorse memory. User scope is injected by the server.",
            """
                    {"type":"object","required":["query"],"properties":{"query":{"type":"string"},"layers":{"type":"array","items":{"type":"string","enum":["SHORT","LONG","SEMANTIC"]}},"limit":{"type":"integer","minimum":1,"maximum":20}}}
                    """);

    private final MemoryEnginePort memoryEnginePort;
    private final AgentToolJsonSupport jsonSupport;

    public MemoryReadToolPortAdapter(MemoryEnginePort memoryEnginePort, AgentToolJsonSupport jsonSupport) {
        this.memoryEnginePort = Objects.requireNonNull(memoryEnginePort, "memoryEnginePort must not be null");
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
            if (userId.isBlank()) {
                return ToolInvocationResult.failed("server user scope is missing");
            }
            String query = jsonSupport.string(arguments, "query");
            int limit = jsonSupport.boundedInt(arguments, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
            List<MemoryItem> items = memoryEnginePort.retrieveMemories(MemoryLoadRequest.builder()
                    .userId(userId)
                    .conversationId(jsonSupport.string(arguments, "_seahorseConversationId"))
                    .currentQuestion(query)
                    .build()).stream().limit(limit).toList();
            return ToolInvocationResult.ok(jsonSupport.write(Map.of(
                    "query", query,
                    "resultCount", items.size(),
                    "memories", items.stream().map(this::memory).toList())));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("memory_read failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, Object> memory(MemoryItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memoryId", Objects.requireNonNullElse(item.getId(), ""));
        result.put("layer", item.getLayer() == null ? MemoryLayer.SHORT_TERM.name() : item.getLayer().name());
        result.put("type", Objects.requireNonNullElse(item.getType(), ""));
        result.put("content", Objects.requireNonNullElse(item.getContent(), ""));
        result.put("importanceScore", item.getImportanceScore());
        result.put("confidenceLevel", item.getConfidenceLevel());
        result.put("updatedAt", item.getCreateTime() == null ? "" : item.getCreateTime().toString());
        return result;
    }
}
