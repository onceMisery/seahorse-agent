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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeahorseMcpServerController {

    @NonNull
    private final ObjectProvider<McpServerManagementInboundPort> mcpServerPortProvider;

    @GetMapping({"/mcp/servers", "/api/mcp/servers"})
    public ApiResponse<Object> listServers() {
        return ApiResponses.requireService(mcpServerPortProvider, McpServerManagementInboundPort::listServers);
    }

    @GetMapping({"/mcp/servers/{serverName}", "/api/mcp/servers/{serverName}"})
    public ApiResponse<Object> findServer(@PathVariable String serverName) {
        return ApiResponses.requireService(mcpServerPortProvider, port -> port.findServer(serverName)
                .orElseThrow(() -> new ResourceNotFoundException("MCP server not found")));
    }

    @GetMapping({
            "/mcp/servers/{serverName}/stderr-tail",
            "/api/mcp/servers/{serverName}/stderr-tail"
    })
    public ApiResponse<Object> stderrTail(@PathVariable String serverName) {
        return ApiResponses.requireService(mcpServerPortProvider, port -> port.findServer(serverName)
                .map(view -> view.getStderrTail() == null ? "" : view.getStderrTail())
                .orElseThrow(() -> new ResourceNotFoundException("MCP server not found")));
    }

    @PostMapping({"/mcp/servers/{serverName}/test", "/api/mcp/servers/{serverName}/test"})
    public ApiResponse<Object> testServer(@PathVariable String serverName) {
        return ApiResponses.requireService(mcpServerPortProvider, port -> port.testServer(serverName));
    }

    @PostMapping({"/mcp/servers/{serverName}/restart", "/api/mcp/servers/{serverName}/restart"})
    public ApiResponse<Object> restartServer(@PathVariable String serverName) {
        return ApiResponses.requireService(mcpServerPortProvider, port -> port.restartServer(serverName));
    }

    @PostMapping({"/mcp/servers/{serverName}/refresh-tools", "/api/mcp/servers/{serverName}/refresh-tools"})
    public ApiResponse<Object> refreshTools(@PathVariable String serverName) {
        return ApiResponses.requireService(mcpServerPortProvider, port -> port.refreshTools(serverName));
    }
}
