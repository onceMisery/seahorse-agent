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

import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerStatusView;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerTestResultView;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class McpServerRuntimeRegistry implements McpServerManagementInboundPort {

    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    private static final String ECHO_TOOL_ID = "echo";
    private static final String TEST_TEXT = "seahorse mcp health check";

    private final Map<String, McpServerStatusView> servers = new LinkedHashMap<>();
    private McpToolRegistryPort toolRegistry = McpToolRegistryPort.empty();
    private LifecycleActions lifecycleActions = LifecycleActions.unsupported();

    public interface LifecycleActions {

        void restart(String serverName);

        void refreshTools(String serverName);

        static LifecycleActions unsupported() {
            return new LifecycleActions() {
                @Override
                public void restart(String serverName) {
                    throw new UnsupportedOperationException("MCP server restart is unsupported");
                }

                @Override
                public void refreshTools(String serverName) {
                    throw new UnsupportedOperationException("MCP server tool refresh is unsupported");
                }
            };
        }
    }

    public synchronized void setToolRegistry(McpToolRegistryPort toolRegistry) {
        this.toolRegistry = Objects.requireNonNullElseGet(toolRegistry, McpToolRegistryPort::empty);
    }

    public synchronized void setLifecycleActions(LifecycleActions lifecycleActions) {
        this.lifecycleActions = Objects.requireNonNullElseGet(lifecycleActions, LifecycleActions::unsupported);
    }

    public synchronized void recordDisabled(McpHttpAdapterProperties.Server server) {
        record(server, STATUS_DISABLED, List.of(), "");
    }

    public synchronized void recordReady(
            McpHttpAdapterProperties.Server server,
            List<McpToolDescriptor> tools,
            String stderrTail) {
        record(server, STATUS_READY, tools, stderrTail);
    }

    public synchronized void recordFailed(McpHttpAdapterProperties.Server server, String reason) {
        record(server, STATUS_FAILED, List.of(), reason);
    }

    @Override
    public synchronized List<McpServerStatusView> listServers() {
        return servers.values().stream()
                .sorted(Comparator.comparing(McpServerStatusView::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    public synchronized Optional<McpServerStatusView> findServer(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(servers.get(serverName.trim()));
    }

    @Override
    public synchronized McpServerTestResultView testServer(String serverName) {
        Optional<McpServerStatusView> server = findServer(serverName);
        if (server.isEmpty()) {
            return testResult(serverName, "", false, "NOT_FOUND", "", "MCP server not found");
        }
        Optional<String> echoToolId = echoToolId(server.get());
        if (echoToolId.isEmpty()) {
            return testResult(server.get().getName(), "", false, "TOOL_NOT_FOUND", "", "safe echo tool not found");
        }
        Optional<McpToolExecutorPort> executor = toolRegistry.findExecutor(echoToolId.get());
        if (executor.isEmpty()) {
            return testResult(server.get().getName(), echoToolId.get(), false,
                    "TOOL_NOT_FOUND", "", "MCP tool executor not found");
        }
        try {
            McpToolExecutionResult result = executor.get().execute(
                    new McpToolExecutionRequest(echoToolId.get(), Map.of("text", TEST_TEXT)));
            return testResult(
                    server.get().getName(),
                    result.toolId(),
                    result.success(),
                    result.status().name(),
                    result.content(),
                    result.message());
        } catch (RuntimeException ex) {
            return testResult(server.get().getName(), echoToolId.get(), false,
                    "EXECUTION_FAILED", "", Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    @Override
    public synchronized McpServerStatusView restartServer(String serverName) {
        String name = existingServerName(serverName);
        lifecycleActions.restart(name);
        return servers.get(name);
    }

    @Override
    public synchronized McpServerStatusView refreshTools(String serverName) {
        String name = existingServerName(serverName);
        lifecycleActions.refreshTools(name);
        return servers.get(name);
    }

    private void record(
            McpHttpAdapterProperties.Server server,
            String status,
            List<McpToolDescriptor> tools,
            String stderrTail) {
        String name = serverName(server);
        List<McpServerStatusView.ToolView> toolViews = Objects.requireNonNullElse(tools, List.<McpToolDescriptor>of())
                .stream()
                .map(tool -> McpServerStatusView.ToolView.builder()
                        .toolId(tool.toolId())
                        .provider("MCP")
                        .enabled(true)
                        .build())
                .toList();
        servers.put(name, McpServerStatusView.builder()
                .name(name)
                .transport(server == null || server.getTransport() == null ? "" : server.getTransport().name())
                .enabled(server != null && server.isEnabled())
                .status(status)
                .toolCount(toolViews.size())
                .lastDiscoveryAt(Instant.now())
                .stderrTail(Objects.requireNonNullElse(stderrTail, ""))
                .tools(toolViews)
                .build());
    }

    private String serverName(McpHttpAdapterProperties.Server server) {
        if (server == null || server.getName() == null || server.getName().isBlank()) {
            return "default";
        }
        return server.getName().trim();
    }

    private Optional<String> echoToolId(McpServerStatusView server) {
        return Objects.requireNonNullElse(server.getTools(), List.<McpServerStatusView.ToolView>of()).stream()
                .map(McpServerStatusView.ToolView::getToolId)
                .filter(Objects::nonNull)
                .filter(toolId -> ECHO_TOOL_ID.equals(toolId) || toolId.endsWith("." + ECHO_TOOL_ID))
                .findFirst();
    }

    private String existingServerName(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            throw new IllegalArgumentException("MCP server name must not be blank");
        }
        String name = serverName.trim();
        if (!servers.containsKey(name)) {
            throw new IllegalArgumentException("MCP server not found: " + name);
        }
        return name;
    }

    private McpServerTestResultView testResult(
            String serverName,
            String toolId,
            boolean success,
            String status,
            String content,
            String message) {
        return McpServerTestResultView.builder()
                .serverName(Objects.requireNonNullElse(serverName, ""))
                .toolId(Objects.requireNonNullElse(toolId, ""))
                .success(success)
                .status(Objects.requireNonNullElse(status, ""))
                .content(Objects.requireNonNullElse(content, ""))
                .message(Objects.requireNonNullElse(message, ""))
                .testedAt(Instant.now())
                .build();
    }
}
