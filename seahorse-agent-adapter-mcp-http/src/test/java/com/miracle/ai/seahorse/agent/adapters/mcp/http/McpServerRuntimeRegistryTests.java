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

import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerStatusView;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerTestResultView;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerRuntimeRegistryTests {

    @Test
    void shouldExposeSortedRuntimeStatuses() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();

        registry.recordReady(server("z-stdio", McpHttpAdapterProperties.Transport.STDIO, true),
                List.of(new McpToolDescriptor("filesystem.read_file", "Read file", Map.of())),
                "server ready");
        registry.recordFailed(server("a-http", McpHttpAdapterProperties.Transport.STREAMABLE_HTTP, true),
                "credential unavailable");
        registry.recordDisabled(server("m-disabled", McpHttpAdapterProperties.Transport.STREAMABLE_HTTP, false));

        List<McpServerStatusView> servers = registry.listServers();

        assertThat(servers).extracting(McpServerStatusView::getName)
                .containsExactly("a-http", "m-disabled", "z-stdio");
        assertThat(servers.get(0).getStatus()).isEqualTo(McpServerRuntimeRegistry.STATUS_FAILED);
        assertThat(servers.get(0).getStderrTail()).isEqualTo("credential unavailable");
        assertThat(servers.get(1).getStatus()).isEqualTo(McpServerRuntimeRegistry.STATUS_DISABLED);
        assertThat(servers.get(2).getStatus()).isEqualTo(McpServerRuntimeRegistry.STATUS_READY);
        assertThat(servers.get(2).getToolCount()).isEqualTo(1);
        assertThat(servers.get(2).getTools()).extracting(McpServerStatusView.ToolView::getToolId)
                .containsExactly("filesystem.read_file");
        assertThat(servers.get(2).getStderrTail()).isEqualTo("server ready");
    }

    @Test
    void shouldFindTrimmedServerName() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();
        registry.recordFailed(server("local-echo", McpHttpAdapterProperties.Transport.STDIO, true), "boom");

        assertThat(registry.findServer(" local-echo ")).isPresent();
        assertThat(registry.findServer("")).isEmpty();
    }

    @Test
    void shouldRunSafeEchoTestCallThroughRegisteredExecutor() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();
        registry.recordReady(server("local-echo", McpHttpAdapterProperties.Transport.STDIO, true),
                List.of(new McpToolDescriptor("echo", "Echo text", Map.of())),
                "");
        CapturingExecutor executor = new CapturingExecutor();
        registry.setToolRegistry(new SingleToolRegistry("echo", executor));

        McpServerTestResultView result = registry.testServer("local-echo");

        assertThat(result.getServerName()).isEqualTo("local-echo");
        assertThat(result.getToolId()).isEqualTo("echo");
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("echo seahorse mcp health check");
        assertThat(executor.lastRequest.toolId()).isEqualTo("echo");
        assertThat(executor.lastRequest.arguments()).containsEntry("text", "seahorse mcp health check");
    }

    @Test
    void shouldRejectTestCallWhenNoSafeEchoToolExists() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();
        registry.recordReady(server("filesystem", McpHttpAdapterProperties.Transport.STDIO, true),
                List.of(new McpToolDescriptor("filesystem.read_file", "Read file", Map.of())),
                "");

        McpServerTestResultView result = registry.testServer("filesystem");

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getToolId()).isEmpty();
        assertThat(result.getMessage()).isEqualTo("safe echo tool not found");
    }

    @Test
    void shouldReportExecutorFailureWithoutThrowingDuringSafeTestCall() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();
        registry.recordReady(server("local-echo", McpHttpAdapterProperties.Transport.STDIO, true),
                List.of(new McpToolDescriptor("echo", "Echo text", Map.of())),
                "");
        registry.setToolRegistry(new SingleToolRegistry("echo", request -> {
            throw new IllegalStateException("stdio process stopped");
        }));

        McpServerTestResultView result = registry.testServer("local-echo");

        assertThat(result.getServerName()).isEqualTo("local-echo");
        assertThat(result.getToolId()).isEqualTo("echo");
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("EXECUTION_FAILED");
        assertThat(result.getMessage()).isEqualTo("stdio process stopped");
    }

    @Test
    void shouldRestartServerThroughLifecycleAction() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();
        registry.recordFailed(server("local-echo", McpHttpAdapterProperties.Transport.STDIO, true), "boom");
        registry.setLifecycleActions(new CapturingLifecycleActions(registry));

        McpServerStatusView status = registry.restartServer("local-echo");

        assertThat(status.getName()).isEqualTo("local-echo");
        assertThat(status.getStatus()).isEqualTo(McpServerRuntimeRegistry.STATUS_READY);
        assertThat(status.getToolCount()).isEqualTo(1);
        assertThat(status.getStderrTail()).isEqualTo("restarted");
    }

    @Test
    void shouldRefreshToolsThroughLifecycleAction() {
        McpServerRuntimeRegistry registry = new McpServerRuntimeRegistry();
        registry.recordReady(server("local-echo", McpHttpAdapterProperties.Transport.STDIO, true),
                List.of(new McpToolDescriptor("echo", "Echo text", Map.of())),
                "old");
        registry.setLifecycleActions(new CapturingLifecycleActions(registry));

        McpServerStatusView status = registry.refreshTools("local-echo");

        assertThat(status.getStatus()).isEqualTo(McpServerRuntimeRegistry.STATUS_READY);
        assertThat(status.getStderrTail()).isEqualTo("refreshed");
        assertThat(status.getTools()).extracting(McpServerStatusView.ToolView::getToolId)
                .containsExactly("echo", "local-echo.extra");
    }

    private static McpHttpAdapterProperties.Server server(
            String name,
            McpHttpAdapterProperties.Transport transport,
            boolean enabled) {
        McpHttpAdapterProperties.Server server = new McpHttpAdapterProperties.Server();
        server.setName(name);
        server.setTransport(transport);
        server.setEnabled(enabled);
        return server;
    }

    private static final class CapturingExecutor implements McpToolExecutorPort {
        private McpToolExecutionRequest lastRequest;

        @Override
        public McpToolExecutionResult execute(McpToolExecutionRequest request) {
            lastRequest = request;
            return McpToolExecutionResult.success(request.toolId(), "echo " + request.arguments().get("text"));
        }
    }

    private record SingleToolRegistry(String toolId, McpToolExecutorPort executor) implements McpToolRegistryPort {
        @Override
        public Optional<McpToolExecutorPort> findExecutor(String requestedToolId) {
            return toolId.equals(requestedToolId) ? Optional.of(executor) : Optional.empty();
        }
    }

    private static final class CapturingLifecycleActions implements McpServerRuntimeRegistry.LifecycleActions {
        private final McpServerRuntimeRegistry registry;

        private CapturingLifecycleActions(McpServerRuntimeRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void restart(String serverName) {
            registry.recordReady(server(serverName, McpHttpAdapterProperties.Transport.STDIO, true),
                    List.of(new McpToolDescriptor("echo", "Echo text", Map.of())),
                    "restarted");
        }

        @Override
        public void refreshTools(String serverName) {
            registry.recordReady(server(serverName, McpHttpAdapterProperties.Transport.STDIO, true),
                    List.of(
                            new McpToolDescriptor("echo", "Echo text", Map.of()),
                            new McpToolDescriptor(serverName + ".extra", "Extra", Map.of())),
                    "refreshed");
        }
    }
}
