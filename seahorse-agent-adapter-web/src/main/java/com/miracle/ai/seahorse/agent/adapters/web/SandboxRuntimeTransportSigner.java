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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class SandboxRuntimeTransportSigner {

    public static final String HEADER_NODE = "X-Seahorse-Sandbox-Node";
    public static final String HEADER_OWNER = "X-Seahorse-Sandbox-Lease-Owner";
    public static final String HEADER_TIMESTAMP = "X-Seahorse-Sandbox-Timestamp";
    public static final String HEADER_NONCE = "X-Seahorse-Sandbox-Nonce";
    public static final String HEADER_BODY_SHA256 = "X-Seahorse-Sandbox-Body-SHA256";
    public static final String HEADER_SIGNATURE = "X-Seahorse-Sandbox-Signature";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secret;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;

    public SandboxRuntimeTransportSigner(String secret) {
        this(secret, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    SandboxRuntimeTransportSigner(String secret, Clock clock, Supplier<String> nonceSupplier) {
        this.secret = requireSecret(secret);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "nonceSupplier must not be null");
    }

    public Map<String, String> sign(String nodeId, String method, String path, String body) {
        return sign(nodeId, "transport-owner", method, path, body);
    }

    public Map<String, String> sign(String nodeId, String ownerId, String method, String path, String body) {
        String timestamp = Instant.now(clock).toString();
        String nonce = Objects.requireNonNull(nonceSupplier.get(), "nonce must not be null");
        String bodySha256 = sha256Hex(body);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_NODE, requireText(nodeId, "nodeId must not be blank"));
        headers.put(HEADER_OWNER, requireText(ownerId, "ownerId must not be blank"));
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_NONCE, nonce);
        headers.put(HEADER_BODY_SHA256, bodySha256);
        headers.put(HEADER_SIGNATURE, signPayload(secret,
                signaturePayload(nodeId, ownerId, method, path, timestamp, nonce, bodySha256)));
        return Map.copyOf(headers);
    }

    static String signaturePayload(String nodeId,
                                   String ownerId,
                                   String method,
                                   String path,
                                   String timestamp,
                                   String nonce,
                                   String bodySha256) {
        return requireText(nodeId, "nodeId must not be blank") + "\n"
                + requireText(ownerId, "ownerId must not be blank") + "\n"
                + requireText(method, "method must not be blank").toUpperCase() + "\n"
                + requirePath(path) + "\n"
                + requireText(timestamp, "timestamp must not be blank") + "\n"
                + requireText(nonce, "nonce must not be blank") + "\n"
                + requireText(bodySha256, "bodySha256 must not be blank");
    }

    static String sha256Hex(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(Objects.requireNonNullElse(body, "").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash sandbox transport body", ex);
        }
    }

    static String signPayload(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(requireSecret(secret).getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return hex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign sandbox transport request", ex);
        }
    }

    static String requireSecret(String value) {
        String secret = requireText(value, "sandbox transport shared secret must not be blank");
        if (secret.length() < 32) {
            throw new IllegalArgumentException("sandbox transport shared secret must contain at least 32 characters");
        }
        return secret;
    }

    private static String requirePath(String value) {
        String path = requireText(value, "path must not be blank");
        if (!path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("path must be an absolute request path without query or fragment");
        }
        return path;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
