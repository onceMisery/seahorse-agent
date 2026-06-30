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

import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.jsonrpc.JsonRpcTransportWrapper;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScopeA2aServerControllerTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void controllerKeepsExplicitComponentScanGuard() {
        ConditionalOnProperty condition = AgentScopeA2aServerController.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("seahorse.agentscope.a2a.controller-component-scan-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void rejectsPostWhenSharedSecretIsMissing() {
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                new AgentScopeProperties());

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", Map.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void rejectsPostWhenSharedSecretHeaderDoesNotMatch() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setAuthHeaderName("X-Test-A2A-Token");
        properties.getA2a().setSharedSecret("unit-secret");
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                properties);

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", Map.of("X-Test-A2A-Token", "wrong")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void acceptsPostWhenSharedSecretHeaderNameUsesDifferentCase() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setAuthHeaderName("X-Test-A2A-Token");
        properties.getA2a().setSharedSecret("unit-secret");
        AgentScopeA2aServer server = mock(AgentScopeA2aServer.class);
        JsonRpcTransportWrapper wrapper = mock(JsonRpcTransportWrapper.class);
        when(server.getTransportWrapper("JSONRPC", JsonRpcTransportWrapper.class)).thenReturn(wrapper);
        when(wrapper.handleRequest("{}", Map.of("x-test-a2a-token", "unit-secret"), Map.of()))
                .thenReturn(Map.of("ok", true));
        AgentScopeA2aServerController controller = controller(
                server,
                properties);

        Object response = controller.handleJsonRpc("{}", Map.of("x-test-a2a-token", "unit-secret"));

        assertThat(response).isEqualTo(Map.of("ok", true));
        verify(wrapper).handleRequest("{}", Map.of("x-test-a2a-token", "unit-secret"), Map.of());
    }

    @Test
    void acceptsPostWithoutCredentialsWhenAuthModeIsNone() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setAuthMode(A2aAuthMode.NONE);
        AgentScopeA2aServer server = mock(AgentScopeA2aServer.class);
        JsonRpcTransportWrapper wrapper = mock(JsonRpcTransportWrapper.class);
        when(server.getTransportWrapper("JSONRPC", JsonRpcTransportWrapper.class)).thenReturn(wrapper);
        when(wrapper.handleRequest(eq("{}"), anyMap(), eq(Map.of()))).thenReturn(Map.of("ok", true));
        AgentScopeA2aServerController controller = controller(server, properties);

        Object response = controller.handleJsonRpc("{}", Map.of());

        assertThat(response).isEqualTo(Map.of("ok", true));
    }

    @Test
    void recordsA2aAuthObservationWithAgentDimensions() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setAuthMode(A2aAuthMode.NONE);
        properties.getA2a().setTenantId("tenant-a");
        properties.getA2a().setAgentName("planner");
        AgentScopeA2aServer server = mock(AgentScopeA2aServer.class);
        JsonRpcTransportWrapper wrapper = mock(JsonRpcTransportWrapper.class);
        when(server.getTransportWrapper("JSONRPC", JsonRpcTransportWrapper.class)).thenReturn(wrapper);
        when(wrapper.handleRequest(eq("{}"), anyMap(), eq(Map.of()))).thenReturn(Map.of("ok", true));
        RecordingObservationPort observationPort = new RecordingObservationPort();
        AgentScopeA2aServerController controller = new AgentScopeA2aServerController(
                server,
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK),
                observationPort.support());

        Object response = controller.handleJsonRpc("{}", Map.of());

        assertThat(response).isEqualTo(Map.of("ok", true));
        assertThat(observationPort.commands).hasSize(1);
        assertThat(observationPort.commands.get(0).name()).isEqualTo("a2a.auth");
        assertThat(observationPort.commands.get(0).tenantId()).isEqualTo("tenant-a");
        assertThat(observationPort.commands.get(0).attributes())
                .containsEntry("agentName", "planner")
                .containsEntry("a2a.authMode", "NONE");
        assertThat(observationPort.closed).isEqualTo(1);
    }

    @Test
    void acceptsTenantSignedPost() {
        AgentScopeProperties properties = signedProperties();
        AgentScopeA2aServer server = mock(AgentScopeA2aServer.class);
        JsonRpcTransportWrapper wrapper = mock(JsonRpcTransportWrapper.class);
        when(server.getTransportWrapper("JSONRPC", JsonRpcTransportWrapper.class)).thenReturn(wrapper);
        when(wrapper.handleRequest(eq("{}"), anyMap(), eq(Map.of()))).thenReturn(Map.of("ok", true));
        AgentScopeA2aServerController controller = controller(
                server,
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));
        Map<String, String> headers = new A2aRequestSigner("unit-secret", FIXED_CLOCK, () -> "nonce-1")
                .sign("tenant-a", "planner", "{}");

        Object response = controller.handleJsonRpc("{}", headers);

        assertThat(response).isEqualTo(Map.of("ok", true));
    }

    @Test
    void rejectsTenantSignedPostWhenSignatureIsMissing() {
        AgentScopeProperties properties = signedProperties();
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", Map.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectsTenantSignedPostWhenSignatureIsWrong() {
        AgentScopeProperties properties = signedProperties();
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));
        Map<String, String> headers = new A2aRequestSigner("wrong-secret", FIXED_CLOCK, () -> "nonce-1")
                .sign("tenant-a", "planner", "{}");

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", headers))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectsTenantSignedPostWhenNonceIsReplayed() {
        AgentScopeProperties properties = signedProperties();
        AgentScopeA2aServer server = mock(AgentScopeA2aServer.class);
        JsonRpcTransportWrapper wrapper = mock(JsonRpcTransportWrapper.class);
        when(server.getTransportWrapper("JSONRPC", JsonRpcTransportWrapper.class)).thenReturn(wrapper);
        when(wrapper.handleRequest(eq("{}"), anyMap(), eq(Map.of()))).thenReturn(Map.of("ok", true));
        AgentScopeA2aServerController controller = controller(
                server,
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));
        Map<String, String> headers = new A2aRequestSigner("unit-secret", FIXED_CLOCK, () -> "nonce-1")
                .sign("tenant-a", "planner", "{}");
        controller.handleJsonRpc("{}", headers);

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", headers))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsTenantSignedPostWhenTimestampIsOutsideSkew() {
        AgentScopeProperties properties = signedProperties();
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));
        Clock oldClock = Clock.fixed(Instant.parse("2026-06-20T11:40:00Z"), ZoneOffset.UTC);
        Map<String, String> headers = new A2aRequestSigner("unit-secret", oldClock, () -> "nonce-1")
                .sign("tenant-a", "planner", "{}");

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", headers))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectsTenantSignedPostWhenTenantDoesNotMatch() {
        AgentScopeProperties properties = signedProperties();
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));
        Map<String, String> headers = new A2aRequestSigner("unit-secret", FIXED_CLOCK, () -> "nonce-1")
                .sign("tenant-b", "planner", "{}");

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", headers))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void rejectsTenantSignedPostWhenSecretIsMissing() {
        AgentScopeProperties properties = signedProperties();
        properties.getA2a().setSharedSecret("");
        AgentScopeA2aServerController controller = controller(
                mock(AgentScopeA2aServer.class),
                properties,
                new A2aRequestAuthenticator(properties, FIXED_CLOCK));

        assertThatThrownBy(() -> controller.handleJsonRpc("{}", Map.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private AgentScopeProperties signedProperties() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setAuthMode(A2aAuthMode.TENANT_SIGNED);
        properties.getA2a().setTenantId("tenant-a");
        properties.getA2a().setAgentName("planner");
        properties.getA2a().setSharedSecret("unit-secret");
        return properties;
    }

    private AgentScopeA2aServerController controller(
            AgentScopeA2aServer server,
            AgentScopeProperties properties) {
        return controller(server, properties, new A2aRequestAuthenticator(properties));
    }

    private AgentScopeA2aServerController controller(
            AgentScopeA2aServer server,
            AgentScopeProperties properties,
            A2aRequestAuthenticator authenticator) {
        return new AgentScopeA2aServerController(
                server,
                properties,
                authenticator,
                AgentScopeObservationSupport.noop());
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
