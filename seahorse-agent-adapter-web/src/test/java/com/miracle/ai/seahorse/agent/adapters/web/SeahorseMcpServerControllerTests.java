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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.mcp.McpServerStatusView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseMcpServerControllerTests {

    @Test
    void shouldListMcpServerRuntimeStatus() throws Exception {
        McpServerManagementInboundPort port = mock(McpServerManagementInboundPort.class);
        when(port.listServers()).thenReturn(List.of(server()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseMcpServerController(provider(port))).build();

        mvc.perform(get("/api/mcp/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].name").value("local-echo"))
                .andExpect(jsonPath("$.data[0].transport").value("STDIO"))
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].toolCount").value(1))
                .andExpect(jsonPath("$.data[0].stderrTail").value("ready"));

        verify(port).listServers();
    }

    @Test
    void shouldFindMcpServerRuntimeStatusByName() throws Exception {
        McpServerManagementInboundPort port = mock(McpServerManagementInboundPort.class);
        when(port.findServer("local-echo")).thenReturn(Optional.of(server()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeahorseMcpServerController(provider(port))).build();

        mvc.perform(get("/api/mcp/servers/local-echo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.name").value("local-echo"))
                .andExpect(jsonPath("$.data.tools[0].toolId").value("echo"));

        verify(port).findServer("local-echo");
    }

    private static McpServerStatusView server() {
        return McpServerStatusView.builder()
                .name("local-echo")
                .transport("STDIO")
                .enabled(true)
                .status("READY")
                .toolCount(1)
                .lastDiscoveryAt(Instant.parse("2026-06-21T10:00:00Z"))
                .stderrTail("ready")
                .tools(List.of(McpServerStatusView.ToolView.builder()
                        .toolId("echo")
                        .provider("MCP")
                        .enabled(true)
                        .build()))
                .build();
    }

    private static ObjectProvider<McpServerManagementInboundPort> provider(McpServerManagementInboundPort instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(McpServerManagementInboundPort.class.getName(), instance);
        return beanFactory.getBeanProvider(McpServerManagementInboundPort.class);
    }
}
