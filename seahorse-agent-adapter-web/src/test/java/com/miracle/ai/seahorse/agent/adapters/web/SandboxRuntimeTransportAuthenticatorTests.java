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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxRuntimeTransportAuthenticatorTests {

    private static final String SECRET = "sandbox-transport-test-secret-32-chars";
    private static final String NODE_ID = "sandbox-node-a";
    private static final String PATH = "/internal/sandbox/runtime/sessions";
    private static final String BODY = "{\"tenantId\":\"default\"}";
    private static final Instant NOW = Instant.parse("2026-07-15T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldAcceptValidSignatureOnce() {
        SandboxRuntimeTransportAuthenticator authenticator = authenticator();
        Map<String, String> headers = signer("nonce-valid").sign(NODE_ID, "POST", PATH, BODY);

        assertThatCode(() -> authenticator.authenticate("POST", PATH, BODY, headers)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authenticator.authenticate("POST", PATH, BODY, headers))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void shouldRejectBodyPathAndNodeTampering() {
        assertThatThrownBy(() -> authenticator().authenticate(
                "POST", PATH, BODY + " ", signer("nonce-body").sign(NODE_ID, "POST", PATH, BODY)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> authenticator().authenticate(
                "POST", PATH + "/other", BODY, signer("nonce-path").sign(NODE_ID, "POST", PATH, BODY)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> authenticator().authenticate(
                "POST", PATH, BODY, signer("nonce-node").sign("sandbox-node-b", "POST", PATH, BODY)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldRejectExpiredSignatureAndWeakSecret() {
        Clock expiredClock = Clock.fixed(NOW.minus(Duration.ofMinutes(3)), ZoneOffset.UTC);
        SandboxRuntimeTransportSigner expiredSigner = new SandboxRuntimeTransportSigner(
                SECRET,
                expiredClock,
                () -> "nonce-expired");

        assertThatThrownBy(() -> authenticator().authenticate(
                "POST", PATH, BODY, expiredSigner.sign(NODE_ID, "POST", PATH, BODY)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> new SandboxRuntimeTransportSigner("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void shouldRetainFutureTimestampNonceForEntireSignatureWindow() {
        MutableClock authenticatorClock = new MutableClock(NOW);
        Clock futureSignerClock = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
        SandboxRuntimeTransportSigner futureSigner = new SandboxRuntimeTransportSigner(
                SECRET,
                futureSignerClock,
                () -> "nonce-future");
        SandboxRuntimeTransportAuthenticator authenticator = new SandboxRuntimeTransportAuthenticator(
                SECRET,
                NODE_ID,
                "owner-a",
                Duration.ofMinutes(2),
                authenticatorClock,
                (nodeId, ownerId) -> true);
        Map<String, String> headers = futureSigner.sign(NODE_ID, "owner-a", "POST", PATH, BODY);

        authenticator.authenticate("POST", PATH, BODY, headers);
        authenticatorClock.setInstant(NOW.plusSeconds(150));

        assertThatThrownBy(() -> authenticator.authenticate("POST", PATH, BODY, headers))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void shouldFenceRequestsByProcessOwnerAndLiveLease() {
        SandboxRuntimeTransportAuthenticator authenticator = new SandboxRuntimeTransportAuthenticator(
                SECRET,
                NODE_ID,
                "owner-a",
                Duration.ofMinutes(2),
                CLOCK,
                (nodeId, ownerId) -> "owner-a".equals(ownerId));

        assertThatThrownBy(() -> authenticator.authenticate(
                "POST", PATH, BODY, signer("nonce-owner-b").sign(NODE_ID, "owner-b", "POST", PATH, BODY)))
                .isInstanceOf(ResponseStatusException.class);
        SandboxRuntimeTransportAuthenticator staleLease = new SandboxRuntimeTransportAuthenticator(
                SECRET,
                NODE_ID,
                "owner-a",
                Duration.ofMinutes(2),
                CLOCK,
                (nodeId, ownerId) -> false);
        assertThatThrownBy(() -> staleLease.authenticate(
                "POST", PATH, BODY, signer("nonce-stale").sign(NODE_ID, "owner-a", "POST", PATH, BODY)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldReserveOwnerLeaseOnlyAfterSignatureAndReplayValidation() {
        AtomicInteger reservations = new AtomicInteger();
        SandboxRuntimeTransportAuthenticator authenticator = new SandboxRuntimeTransportAuthenticator(
                SECRET,
                NODE_ID,
                "owner-a",
                Duration.ofMinutes(2),
                CLOCK,
                (nodeId, ownerId) -> reservations.incrementAndGet() > 0);
        Map<String, String> validHeaders = signer("nonce-reservation").sign(
                NODE_ID, "owner-a", "POST", PATH, BODY);

        assertThatThrownBy(() -> authenticator.authenticate("POST", PATH, BODY + " ", validHeaders))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(reservations).hasValue(0);
        authenticator.authenticate("POST", PATH, BODY, validHeaders);
        assertThat(reservations).hasValue(1);
        assertThatThrownBy(() -> authenticator.authenticate("POST", PATH, BODY, validHeaders))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(reservations).hasValue(1);
    }

    @Test
    void shouldTrackAuthenticatedCreateUntilOperationCloses() {
        AtomicInteger inFlight = new AtomicInteger();
        SandboxRuntimeTransportAuthenticator authenticator = new SandboxRuntimeTransportAuthenticator(
                SECRET,
                NODE_ID,
                "owner-a",
                Duration.ofMinutes(2),
                CLOCK,
                (nodeId, ownerId) -> true,
                new SandboxRuntimeTransportAuthenticator.CreateOperationTracker() {
                    @Override
                    public boolean begin(String nodeId, String ownerId, String operationId) {
                        return inFlight.incrementAndGet() == 1;
                    }

                    @Override
                    public boolean end(String nodeId, String ownerId, String operationId) {
                        return inFlight.decrementAndGet() == 0;
                    }
                });
        Map<String, String> headers = signer("nonce-create-operation").sign(
                NODE_ID, "owner-a", "POST", PATH, BODY);

        try (var ignored = authenticator.authenticateCreate("POST", PATH, BODY, headers)) {
            assertThat(inFlight).hasValue(1);
        }

        assertThat(inFlight).hasValue(0);
    }

    @Test
    void shouldRejectPlainHttpRemoteEndpointByDefault() {
        HttpSandboxRemoteRuntimeAdapter adapter = new HttpSandboxRemoteRuntimeAdapter(
                new ObjectMapper().findAndRegisterModules(),
                SECRET,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1024L);

        assertThatThrownBy(() -> adapter.createSession(
                new SandboxRuntimeNodeEndpoint(
                        NODE_ID,
                        URI.create("http://runtime-node:9090/internal/sandbox/runtime"),
                        NOW.plusSeconds(45)),
                new SandboxSessionRequest(
                        "default", "run-1", SandboxRuntimeType.CODE_INTERPRETER, false, java.util.List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void shouldPreserveLegacyTransportJsonAndSerializeCoordinatorSessionId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SandboxSessionRequest legacy = objectMapper.readValue("""
                {"tenantId":"default","runId":"run-legacy","runtimeType":"CODE_INTERPRETER",
                 "networkRequested":false,"requestedHosts":[],"profileId":"python-small",
                 "expiresAt":"2026-07-15T14:00:00Z"}
                """, SandboxSessionRequest.class);
        SandboxSessionRequest assigned = new SandboxSessionRequest(
                "default",
                "run-assigned",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                java.util.List.of(),
                "python-small",
                NOW.plusSeconds(3600),
                "sandbox_coordinator_123");

        assertThat(legacy.sessionId()).isNull();
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(assigned)).path("sessionId").asText())
                .isEqualTo("sandbox_coordinator_123");
    }

    @Test
    void shouldValidateSessionOwnershipTransportContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SandboxRuntimeTransportProtocol.SessionOwnershipRequest request = objectMapper.readValue(
                "{\"sessionId\":\"sandbox_coordinator_123\"}",
                SandboxRuntimeTransportProtocol.SessionOwnershipRequest.class);
        SandboxRuntimeTransportProtocol.SessionOwnershipResponse response = objectMapper.readValue(
                "{\"sessionId\":\"sandbox_coordinator_123\",\"ownership\":\"OWNED\"}",
                SandboxRuntimeTransportProtocol.SessionOwnershipResponse.class);

        assertThat(request.sessionId()).isEqualTo("sandbox_coordinator_123");
        assertThat(response.ownership()).isEqualTo(SandboxRuntimeSessionOwnership.OWNED);
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"sessionId\":\"../unsafe\"}",
                SandboxRuntimeTransportProtocol.SessionOwnershipRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"sessionId\":\"sandbox_coordinator_123\"}",
                SandboxRuntimeTransportProtocol.SessionOwnershipResponse.class))
                .hasRootCauseInstanceOf(NullPointerException.class);
    }

    private static SandboxRuntimeTransportAuthenticator authenticator() {
        return new SandboxRuntimeTransportAuthenticator(SECRET, NODE_ID, Duration.ofMinutes(2), CLOCK);
    }

    private static SandboxRuntimeTransportSigner signer(String nonce) {
        return new SandboxRuntimeTransportSigner(SECRET, CLOCK, () -> nonce);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
