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
import java.util.regex.Pattern;
import java.util.function.BiPredicate;

public final class SandboxRuntimeTransportAuthenticator {

    private static final int MAX_HEADER_CHARS = 128;
    private static final int SHA256_HEX_CHARS = 64;
    private static final int MAX_REMEMBERED_NONCES = 10_000;
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private final String secret;
    private final String localNodeId;
    private final String localOwnerId;
    private final BiPredicate<String, String> liveOwnerValidator;
    private final Duration allowedTimestampSkew;
    private final Clock clock;
    private final Map<String, Instant> nonceExpirations = new ConcurrentHashMap<>();

    public SandboxRuntimeTransportAuthenticator(String secret,
                                                String localNodeId,
                                                Duration allowedTimestampSkew) {
        this(secret, localNodeId, "transport-owner", allowedTimestampSkew, (nodeId, ownerId) -> true);
    }

    SandboxRuntimeTransportAuthenticator(String secret,
                                         String localNodeId,
                                         Duration allowedTimestampSkew,
                                         Clock clock) {
        this(secret, localNodeId, "transport-owner", allowedTimestampSkew, clock, (nodeId, ownerId) -> true);
    }

    public SandboxRuntimeTransportAuthenticator(String secret,
                                                String localNodeId,
                                                String localOwnerId,
                                                Duration allowedTimestampSkew,
                                                BiPredicate<String, String> liveOwnerValidator) {
        this(secret, localNodeId, localOwnerId, allowedTimestampSkew, Clock.systemUTC(), liveOwnerValidator);
    }

    SandboxRuntimeTransportAuthenticator(String secret,
                                         String localNodeId,
                                         String localOwnerId,
                                         Duration allowedTimestampSkew,
                                         Clock clock,
                                         BiPredicate<String, String> liveOwnerValidator) {
        this.secret = SandboxRuntimeTransportSigner.requireSecret(secret);
        this.localNodeId = requireHeaderValue(localNodeId, "sandbox transport local node id");
        this.localOwnerId = requireHeaderValue(localOwnerId, "sandbox transport local owner id");
        this.liveOwnerValidator = Objects.requireNonNull(liveOwnerValidator, "liveOwnerValidator must not be null");
        this.allowedTimestampSkew = Objects.requireNonNullElse(allowedTimestampSkew, Duration.ofMinutes(2));
        if (this.allowedTimestampSkew.isNegative() || this.allowedTimestampSkew.isZero()
                || this.allowedTimestampSkew.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("sandbox transport allowed timestamp skew must be between 1ms and 10m");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void authenticate(String method, String path, String body, Map<String, String> headers) {
        String nodeId = requiredHeader(headers, SandboxRuntimeTransportSigner.HEADER_NODE);
        if (!constantTimeEquals(localNodeId, nodeId)) {
            unauthorized();
        }
        String ownerId = requiredHeader(headers, SandboxRuntimeTransportSigner.HEADER_OWNER);
        if (!constantTimeEquals(localOwnerId, ownerId)) {
            unauthorized();
        }
        String timestamp = requiredHeader(headers, SandboxRuntimeTransportSigner.HEADER_TIMESTAMP);
        String nonce = requiredHeader(headers, SandboxRuntimeTransportSigner.HEADER_NONCE);
        String bodySha256 = requiredSha256Header(headers, SandboxRuntimeTransportSigner.HEADER_BODY_SHA256);
        String signature = requiredSha256Header(headers, SandboxRuntimeTransportSigner.HEADER_SIGNATURE);
        Instant requestTime = parseTimestamp(timestamp);
        if (Duration.between(requestTime, clock.instant()).abs().compareTo(allowedTimestampSkew) > 0) {
            unauthorized();
        }
        String actualBodySha256 = SandboxRuntimeTransportSigner.sha256Hex(body);
        if (!constantTimeEquals(actualBodySha256, bodySha256)) {
            unauthorized();
        }
        String expectedSignature = SandboxRuntimeTransportSigner.signPayload(
                secret,
                SandboxRuntimeTransportSigner.signaturePayload(
                        nodeId, ownerId, method, path, timestamp, nonce, bodySha256));
        if (!constantTimeEquals(expectedSignature, signature)) {
            unauthorized();
        }
        rememberNonce(nodeId, ownerId, nonce, requestTime);
        if (!liveOwnerValidator.test(nodeId, ownerId)) {
            unauthorized();
        }
    }

    private void rememberNonce(String nodeId, String ownerId, String nonce, Instant requestTime) {
        purgeExpiredNonces();
        if (nonceExpirations.size() >= MAX_REMEMBERED_NONCES) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sandbox transport nonce capacity exceeded");
        }
        Instant expiration = requestTime.plus(allowedTimestampSkew);
        if (nonceExpirations.putIfAbsent(nodeId + "|" + ownerId + "|" + nonce, expiration) != null) {
            unauthorized();
        }
    }

    private void purgeExpiredNonces() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Instant>> iterator = nonceExpirations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private static Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            unauthorized();
            throw ex;
        }
    }

    private static String requiredSha256Header(Map<String, String> headers, String name) {
        String value = requiredHeader(headers, name);
        if (!SHA256_HEX.matcher(value).matches()) {
            unauthorized();
        }
        return value;
    }

    private static String requiredHeader(Map<String, String> headers, String name) {
        if (headers == null) {
            unauthorized();
        }
        String value = headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (value == null || value.isBlank() || value.length() > MAX_HEADER_CHARS) {
            unauthorized();
        }
        return value.trim();
    }

    private static String requireHeaderValue(String value, String label) {
        if (value == null || value.isBlank() || value.length() > MAX_HEADER_CHARS) {
            throw new IllegalArgumentException(label + " must contain 1-128 characters");
        }
        return value.trim();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void unauthorized() {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid sandbox transport credentials");
    }
}
