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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import com.sun.net.httpserver.HttpServer;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class A2aAgentRemoteInvokerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void invokesA2aAgentWithNonStreamingJsonRpcMessageSend() throws Exception {
        AtomicReference<JsonNode> requestJson = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/a2a", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestJson.set(OBJECT_MAPPER.readTree(body));
            String token = exchange.getRequestHeaders().getFirst("X-Test-A2A-Token");
            if (!"unit-secret".equals(token)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] response = """
                    {"jsonrpc":"2.0","id":"smoke","result":{"role":"agent","parts":[{"kind":"text","text":"remote ok"}],"messageId":"reply-1","kind":"message"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            A2aAgentRemoteInvoker invoker = new A2aAgentRemoteInvoker(
                    Duration.ofSeconds(5),
                    "X-Test-A2A-Token",
                    "unit-secret");

            String content = invoker.invoke(agentCard(server), new A2AAgentRequest(
                    "tenant-a",
                    "planner",
                    "draft plan",
                    Map.of("trace", "unit")));

            assertThat(content).isEqualTo("remote ok");
            assertThat(requestJson.get().path("method").asText()).isEqualTo("message/send");
            assertThat(requestJson.get().path("params").path("message").path("parts").get(0).path("text").asText())
                    .isEqualTo("draft plan");
            assertThat(requestJson.get().path("params").path("metadata").path("trace").asText()).isEqualTo("unit");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void signsTenantScopedA2aRequests() throws Exception {
        AtomicReference<Map<String, List<String>>> capturedHeaders = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/a2a", exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            byte[] response = """
                    {"jsonrpc":"2.0","id":"smoke","result":{"role":"agent","parts":[{"kind":"text","text":"signed ok"}],"messageId":"reply-1","kind":"message"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);
            A2aAgentRemoteInvoker invoker = new A2aAgentRemoteInvoker(
                    Duration.ofSeconds(5),
                    A2aAuthMode.TENANT_SIGNED,
                    "X-Test-A2A-Token",
                    "unit-secret",
                    clock,
                    () -> "nonce-1");

            String content = invoker.invoke(agentCard(server), new A2AAgentRequest(
                    "tenant-a",
                    "planner",
                    "draft plan",
                    Map.of()));

            assertThat(content).isEqualTo("signed ok");
            assertThat(capturedHeaders.get()).containsKeys(
                    "X-seahorse-a2a-tenant",
                    "X-seahorse-a2a-agent",
                    "X-seahorse-a2a-timestamp",
                    "X-seahorse-a2a-nonce",
                    "X-seahorse-a2a-body-sha256",
                    "X-seahorse-a2a-signature");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractsTextFromTaskStatusMessageAndArtifacts() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/a2a", exchange -> {
            byte[] response = """
                    {"jsonrpc":"2.0","id":"smoke","result":{"status":{"message":{"parts":[{"kind":"text","text":"status text"}]}},"artifacts":[{"parts":[{"kind":"text","text":"artifact text"}]}]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            A2aAgentRemoteInvoker invoker = new A2aAgentRemoteInvoker(Duration.ofSeconds(5));

            String content = invoker.invoke(agentCard(server), new A2AAgentRequest(
                    "tenant-a",
                    "planner",
                    "draft plan",
                    Map.of()));

            assertThat(content).isEqualTo("status text\nartifact text");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recordsA2aInvokeObservationWithRemoteDimensions() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/a2a", exchange -> {
            byte[] response = """
                    {"jsonrpc":"2.0","id":"smoke","result":{"role":"agent","parts":[{"kind":"text","text":"remote ok"}],"messageId":"reply-1","kind":"message"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            RecordingObservationPort observationPort = new RecordingObservationPort();
            A2aAgentRemoteInvoker invoker = new A2aAgentRemoteInvoker(
                    Duration.ofSeconds(5),
                    A2aAuthMode.NONE,
                    "X-Test-A2A-Token",
                    "",
                    observationPort.support());

            String content = invoker.invoke(agentCard(server), new A2AAgentRequest(
                    "tenant-a",
                    "planner",
                    "draft plan",
                    Map.of()));

            assertThat(content).isEqualTo("remote ok");
            assertThat(observationPort.commands).hasSize(1);
            assertThat(observationPort.commands.get(0).name()).isEqualTo("a2a.invoke");
            assertThat(observationPort.commands.get(0).tenantId()).isEqualTo("tenant-a");
            assertThat(observationPort.commands.get(0).attributes())
                    .containsEntry("remoteAgent", "planner")
                    .containsEntry("a2a.authMode", "NONE")
                    .containsEntry("endpoint", "http://localhost:" + server.getAddress().getPort() + "/a2a");
            assertThat(observationPort.closed).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void surfacesJsonRpcError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/a2a", exchange -> {
            byte[] response = """
                    {"jsonrpc":"2.0","id":"smoke","error":{"code":-32000,"message":"remote rejected"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            A2aAgentRemoteInvoker invoker = new A2aAgentRemoteInvoker(Duration.ofSeconds(5));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> invoker.invoke(agentCard(server), new A2AAgentRequest(
                            "tenant-a",
                            "planner",
                            "draft plan",
                            Map.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("remote rejected");
        } finally {
            server.stop(0);
        }
    }

    private AgentCard agentCard(HttpServer server) {
        return new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("tenant-a/planner")
                .description("Planner")
                .version("1.0.0")
                .url("http://localhost:" + server.getAddress().getPort() + "/a2a")
                .capabilities(new AgentCapabilities.Builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill.Builder()
                        .id("planner")
                        .name("Planner")
                        .description("Planner")
                        .tags(List.of("planner"))
                        .build()))
                .build();
    }

    private static final class RecordingObservationPort implements ObservationPort {
        private final List<ObservationCommand> commands = new ArrayList<>();
        private int closed;

        private AgentScopeObservationSupport support() {
            return new AgentScopeObservationSupport(this);
        }

        @Override
        public ObservationScope start(ObservationCommand command) {
            commands.add(command);
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                }

                @Override
                public void close() {
                    closed++;
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
        }
    }
}
