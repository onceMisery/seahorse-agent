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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSearchToolPortAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolSearchToolPortAdapter adapter = new ToolSearchToolPortAdapter(
            new Registry(List.of(
                    new ToolDescriptor("weather", "Weather", "Weather lookup", "{\"type\":\"object\"}"),
                    new ToolDescriptor("web_search", "Web Search", "Search public Web sources", "{\"type\":\"object\"}"),
                    new ToolDescriptor("delete-memory", "Delete Memory", "Delete stored memory", "{}"))),
            new AgentToolJsonSupport(objectMapper));

    @Test
    void shouldReturnOnlyInjectedAllowedToolMetadata() throws Exception {
        ToolInvocationResult result = adapter.invoke("call-1", ToolSearchToolPortAdapter.TOOL_ID, Map.of(
                "query", "search",
                ToolSearchToolPortAdapter.ALLOWED_TOOL_IDS_ARGUMENT, List.of("web_search")));

        assertTrue(result.success(), result.error());
        JsonNode tools = objectMapper.readTree(result.content()).get("tools");
        assertEquals(1, tools.size());
        assertEquals("web_search", tools.get(0).get("toolId").asText());
        assertEquals("Web Search", tools.get(0).get("name").asText());
        assertFalse(tools.get(0).has("schemaJson"));
    }

    @Test
    void shouldRejectMissingInjectedAllowedToolSnapshot() {
        ToolInvocationResult result = adapter.invoke("call-1", ToolSearchToolPortAdapter.TOOL_ID, Map.of(
                "query", "search"));

        assertFalse(result.success());
        assertEquals("allowed tool snapshot is required", result.error());
    }

    @Test
    void shouldRejectMismatchedToolId() {
        ToolInvocationResult result = adapter.invoke("call-1", "other", Map.of(
                ToolSearchToolPortAdapter.ALLOWED_TOOL_IDS_ARGUMENT, List.of("weather")));

        assertFalse(result.success());
        assertEquals("Tool id mismatch", result.error());
    }

    private record Registry(List<ToolDescriptor> tools) implements ToolRegistryPort {
        @Override
        public List<ToolDescriptor> listTools() {
            return tools;
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            return Optional.empty();
        }
    }
}
