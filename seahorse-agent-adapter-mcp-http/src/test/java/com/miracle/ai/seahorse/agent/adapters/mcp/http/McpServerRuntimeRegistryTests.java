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
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
