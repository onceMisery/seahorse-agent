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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolFeature;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class NativeMcpToolRegistryTests {

    @Test
    void shouldExposeExecutorAndDescriptor() {
        McpToolFeature feature = new StubMcpToolFeature("weather_query");
        NativeMcpToolRegistry registry = new NativeMcpToolRegistry(List.of(feature));

        Assertions.assertTrue(registry.findExecutor("weather_query").isPresent());
        Assertions.assertTrue(registry.findTool("weather_query").isPresent());
        Assertions.assertEquals("weather_query", registry.findTool("weather_query").get().toolId());
    }

    @Test
    void shouldIgnoreBlankToolId() {
        McpToolFeature feature = new StubMcpToolFeature("");
        NativeMcpToolRegistry registry = new NativeMcpToolRegistry(List.of(feature));

        Assertions.assertTrue(registry.findExecutor("").isEmpty());
        Assertions.assertTrue(registry.findTool("").isEmpty());
    }

    @Test
    void shouldReplaceToolsInPlaceAndClosePreviousFeatures() {
        CloseTrackingMcpToolFeature oldFeature = new CloseTrackingMcpToolFeature("old_tool");
        NativeMcpToolRegistry registry = new NativeMcpToolRegistry(List.of(oldFeature));

        registry.replaceAll(List.of(new StubMcpToolFeature("new_tool")));

        Assertions.assertTrue(registry.findExecutor("old_tool").isEmpty());
        Assertions.assertTrue(registry.findExecutor("new_tool").isPresent());
        Assertions.assertTrue(oldFeature.closed);
    }

    private record StubMcpToolFeature(String toolId) implements McpToolFeature {

        @Override
        public McpToolDescriptor descriptor() {
            return new McpToolDescriptor(toolId, "测试工具", Map.of());
        }

        @Override
        public McpToolExecutionResult execute(McpToolExecutionRequest request) {
            return McpToolExecutionResult.success(toolId, "ok");
        }
    }

    private static final class CloseTrackingMcpToolFeature implements McpToolFeature, AutoCloseable {
        private final String toolId;
        private boolean closed;

        private CloseTrackingMcpToolFeature(String toolId) {
            this.toolId = toolId;
        }

        @Override
        public McpToolDescriptor descriptor() {
            return new McpToolDescriptor(toolId, "test tool", Map.of());
        }

        @Override
        public McpToolExecutionResult execute(McpToolExecutionRequest request) {
            return McpToolExecutionResult.success(toolId, "ok");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
