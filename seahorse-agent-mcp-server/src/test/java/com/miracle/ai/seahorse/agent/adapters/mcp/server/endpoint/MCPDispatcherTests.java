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

package com.miracle.ai.seahorse.agent.adapters.mcp.server.endpoint;

import com.miracle.ai.seahorse.agent.adapters.mcp.server.core.DefaultMCPToolRegistry;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.core.MCPToolDefinition;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.core.MCPToolExecutor;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.core.MCPToolRequest;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.core.MCPToolResponse;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.protocol.JsonRpcError;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.protocol.JsonRpcRequest;
import com.miracle.ai.seahorse.agent.adapters.mcp.server.protocol.JsonRpcResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class MCPDispatcherTests {

    private static final String TOOL_ID = "echo";
    private static final String PARAM_MESSAGE = "message";

    @Test
    void shouldReturnInitializeMetadataWithSeahorseServerName() {
        MCPDispatcher dispatcher = new MCPDispatcher(new DefaultMCPToolRegistry(List.of()));

        JsonRpcResponse response = dispatcher.dispatch(new JsonRpcRequest("2.0", 1, "initialize", Map.of()));

        Assertions.assertNull(response.getError());
        Map<?, ?> result = (Map<?, ?>) response.getResult();
        Map<?, ?> serverInfo = (Map<?, ?>) result.get("serverInfo");
        Assertions.assertEquals("seahorse-agent-mcp-server", serverInfo.get("name"));
    }

    @Test
    void shouldListAndCallRegisteredTool() {
        DefaultMCPToolRegistry registry = new DefaultMCPToolRegistry(List.of(new EchoToolExecutor()));
        registry.init();
        MCPDispatcher dispatcher = new MCPDispatcher(registry);

        JsonRpcResponse listResponse = dispatcher.dispatch(new JsonRpcRequest("2.0", 1, "tools/list", Map.of()));
        Map<?, ?> listResult = (Map<?, ?>) listResponse.getResult();
        List<?> tools = (List<?>) listResult.get("tools");
        Assertions.assertEquals(1, tools.size());

        Map<String, Object> params = Map.of(
                "name", TOOL_ID,
                "arguments", Map.of(PARAM_MESSAGE, "hello")
        );
        JsonRpcResponse callResponse = dispatcher.dispatch(new JsonRpcRequest("2.0", 2, "tools/call", params));

        Assertions.assertNull(callResponse.getError());
        Map<?, ?> callResult = (Map<?, ?>) callResponse.getResult();
        Assertions.assertEquals(false, callResult.get("isError"));
        List<?> content = (List<?>) callResult.get("content");
        Map<?, ?> text = (Map<?, ?>) content.get(0);
        Assertions.assertEquals("hello", text.get("text"));
    }

    @Test
    void shouldReturnNullForJsonRpcNotification() {
        MCPDispatcher dispatcher = new MCPDispatcher(new DefaultMCPToolRegistry(List.of()));

        JsonRpcResponse response = dispatcher.dispatch(new JsonRpcRequest("2.0", null, "notifications/initialized", null));

        Assertions.assertNull(response);
    }

    @Test
    void shouldReturnMethodNotFoundForMissingTool() {
        MCPDispatcher dispatcher = new MCPDispatcher(new DefaultMCPToolRegistry(List.of()));

        JsonRpcResponse response = dispatcher.dispatch(new JsonRpcRequest("2.0", 1, "tools/call", Map.of("name", "none")));

        Assertions.assertNotNull(response.getError());
        Assertions.assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.getError().getCode());
    }

    @Test
    void shouldReturnMethodNotFoundForUnknownMethod() {
        MCPDispatcher dispatcher = new MCPDispatcher(new DefaultMCPToolRegistry(List.of()));

        JsonRpcResponse response = dispatcher.dispatch(new JsonRpcRequest("2.0", 1, "unknown/method", Map.of()));

        Assertions.assertNotNull(response.getError());
        Assertions.assertEquals(JsonRpcError.METHOD_NOT_FOUND, response.getError().getCode());
    }

    @Test
    void shouldReturnToolErrorContentWhenExecutorThrowsException() {
        DefaultMCPToolRegistry registry = new DefaultMCPToolRegistry(List.of(new FailingToolExecutor()));
        registry.init();
        MCPDispatcher dispatcher = new MCPDispatcher(registry);

        JsonRpcResponse response = dispatcher.dispatch(new JsonRpcRequest("2.0", 1, "tools/call",
                Map.of("name", "failing", "arguments", Map.of())));

        Assertions.assertNull(response.getError());
        Map<?, ?> result = (Map<?, ?>) response.getResult();
        Assertions.assertEquals(true, result.get("isError"));
        List<?> content = (List<?>) result.get("content");
        Map<?, ?> text = (Map<?, ?>) content.get(0);
        Assertions.assertEquals("工具调用异常: boom", text.get("text"));
    }

    private static final class EchoToolExecutor implements MCPToolExecutor {

        @Override
        public MCPToolDefinition getToolDefinition() {
            MCPToolDefinition.ParameterDef parameter = MCPToolDefinition.ParameterDef.builder()
                    .description("Message to echo.")
                    .required(true)
                    .build();
            return MCPToolDefinition.builder()
                    .toolId(TOOL_ID)
                    .description("Echo tool.")
                    .parameters(Map.of(PARAM_MESSAGE, parameter))
                    .build();
        }

        @Override
        public MCPToolResponse execute(MCPToolRequest request) {
            return MCPToolResponse.success(TOOL_ID, request.getStringParameter(PARAM_MESSAGE));
        }
    }

    private static final class FailingToolExecutor implements MCPToolExecutor {

        @Override
        public MCPToolDefinition getToolDefinition() {
            return MCPToolDefinition.builder()
                    .toolId("failing")
                    .description("Failing tool.")
                    .parameters(Map.of())
                    .build();
        }

        @Override
        public MCPToolResponse execute(MCPToolRequest request) {
            throw new IllegalStateException("boom");
        }
    }
}
