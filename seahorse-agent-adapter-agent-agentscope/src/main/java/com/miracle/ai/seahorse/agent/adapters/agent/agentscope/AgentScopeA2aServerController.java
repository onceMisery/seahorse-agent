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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import io.a2a.spec.AgentCard;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.jsonrpc.JsonRpcTransportWrapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
public class AgentScopeA2aServerController {

    private static final String JSONRPC_TRANSPORT = TransportProtocol.JSONRPC.asString();

    private final AgentScopeA2aServer server;

    public AgentScopeA2aServerController(AgentScopeA2aServer server) {
        this.server = Objects.requireNonNull(server, "server must not be null");
    }

    @GetMapping(
            path = "${seahorse.agentscope.a2a.path:/a2a}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentCard agentCard() {
        return server.getAgentCard();
    }

    @PostMapping(
            path = "${seahorse.agentscope.a2a.path:/a2a}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Object handleJsonRpc(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {
        JsonRpcTransportWrapper wrapper = server.getTransportWrapper(JSONRPC_TRANSPORT, JsonRpcTransportWrapper.class);
        return wrapper.handleRequest(body, headers, Map.of());
    }
}
