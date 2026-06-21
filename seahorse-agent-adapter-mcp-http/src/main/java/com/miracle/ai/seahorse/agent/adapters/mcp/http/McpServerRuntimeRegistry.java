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
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;

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

    private final Map<String, McpServerStatusView> servers = new LinkedHashMap<>();

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
}
