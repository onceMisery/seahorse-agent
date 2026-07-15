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

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
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

    private static SandboxRuntimeTransportAuthenticator authenticator() {
        return new SandboxRuntimeTransportAuthenticator(SECRET, NODE_ID, Duration.ofMinutes(2), CLOCK);
    }

    private static SandboxRuntimeTransportSigner signer(String nonce) {
        return new SandboxRuntimeTransportSigner(SECRET, CLOCK, () -> nonce);
    }
}
