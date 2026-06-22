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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class A2aRequestAuthenticator {

    private static final String DEFAULT_AUTH_HEADER = "X-Seahorse-A2A-Token";

    private final AgentScopeProperties properties;
    private final Clock clock;
    private final Map<String, Instant> nonceExpirations = new ConcurrentHashMap<>();

    public A2aRequestAuthenticator(AgentScopeProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public A2aRequestAuthenticator(AgentScopeProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void authenticate(String body, Map<String, String> headers) {
        A2aAuthMode mode = Objects.requireNonNullElse(properties.getA2a().getAuthMode(),
                A2aAuthMode.SHARED_SECRET);
        switch (mode) {
            case NONE -> {
            }
            case SHARED_SECRET -> verifySharedSecret(headers);
            case TENANT_SIGNED -> verifyTenantSigned(body, headers);
        }
    }

    private void verifySharedSecret(Map<String, String> headers) {
        String expected = configuredSecret();
        String headerName = textOrDefault(properties.getA2a().getAuthHeaderName(), DEFAULT_AUTH_HEADER);
        String actual = trimToNull(headerValue(headers, headerName));
        if (actual == null || !constantTimeEquals(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid A2A credentials");
        }
    }

    private void verifyTenantSigned(String body, Map<String, String> headers) {
        String secret = configuredSecret();
        String tenantId = requiredHeader(headers, A2aRequestSigner.HEADER_TENANT);
        String agentName = requiredHeader(headers, A2aRequestSigner.HEADER_AGENT);
        if (!tenantId.equals(textOrDefault(properties.getA2a().getTenantId(), "default"))
                || !agentName.equals(textOrDefault(properties.getA2a().getAgentName(), "seahorse-agent"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A2A tenant or agent mismatch");
        }

        String timestamp = requiredHeader(headers, A2aRequestSigner.HEADER_TIMESTAMP);
        String nonce = requiredHeader(headers, A2aRequestSigner.HEADER_NONCE);
        String bodySha256 = requiredHeader(headers, A2aRequestSigner.HEADER_BODY_SHA256);
        String signature = requiredHeader(headers, A2aRequestSigner.HEADER_SIGNATURE);

        verifyTimestamp(timestamp);
        String actualBodySha256 = A2aRequestSigner.sha256Hex(body);
        if (!constantTimeEquals(actualBodySha256, bodySha256)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid A2A body hash");
        }
        String expectedSignature = A2aRequestSigner.signPayload(secret,
                A2aRequestSigner.signaturePayload(tenantId, agentName, timestamp, nonce, bodySha256));
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid A2A signature");
        }
        rememberNonce(tenantId, agentName, nonce);
    }

    private void verifyTimestamp(String timestamp) {
        Instant requestTime;
        try {
            requestTime = Instant.parse(timestamp);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid A2A timestamp");
        }
        Duration skew = Objects.requireNonNullElse(properties.getA2a().getAllowedTimestampSkew(),
                Duration.ofMinutes(5));
        Duration actualSkew = Duration.between(requestTime, Instant.now(clock)).abs();
        if (actualSkew.compareTo(skew) > 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A2A timestamp outside allowed skew");
        }
    }

    private void rememberNonce(String tenantId, String agentName, String nonce) {
        purgeExpiredNonces();
        String key = tenantId + "|" + agentName + "|" + nonce;
        Duration ttl = Objects.requireNonNullElse(properties.getA2a().getNonceTtl(), Duration.ofMinutes(10));
        Instant expiresAt = Instant.now(clock).plus(ttl);
        Instant previous = nonceExpirations.putIfAbsent(key, expiresAt);
        if (previous != null && previous.isAfter(Instant.now(clock))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Replayed A2A nonce");
        }
        nonceExpirations.put(key, expiresAt);
    }

    private void purgeExpiredNonces() {
        Instant now = Instant.now(clock);
        Iterator<Map.Entry<String, Instant>> iterator = nonceExpirations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private String requiredHeader(Map<String, String> headers, String headerName) {
        String value = trimToNull(headerValue(headers, headerName));
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing A2A signed header: " + headerName);
        }
        return value;
    }

    private String configuredSecret() {
        String secret = trimToNull(properties.getA2a().getSharedSecret());
        if (secret == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A2A shared secret is not configured");
        }
        return secret;
    }

    private String headerValue(Map<String, String> headers, String headerName) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String exact = headers.get(headerName);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String textOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
