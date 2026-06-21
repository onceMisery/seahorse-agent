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

public class A2aRequestSigner {

    public static final String HEADER_TENANT = "X-Seahorse-A2A-Tenant";
    public static final String HEADER_AGENT = "X-Seahorse-A2A-Agent";
    public static final String HEADER_TIMESTAMP = "X-Seahorse-A2A-Timestamp";
    public static final String HEADER_NONCE = "X-Seahorse-A2A-Nonce";
    public static final String HEADER_BODY_SHA256 = "X-Seahorse-A2A-Body-SHA256";
    public static final String HEADER_SIGNATURE = "X-Seahorse-A2A-Signature";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secret;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;

    public A2aRequestSigner(String secret) {
        this(secret, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    public A2aRequestSigner(String secret, Clock clock, Supplier<String> nonceSupplier) {
        this.secret = Objects.requireNonNull(secret, "secret must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "nonceSupplier must not be null");
    }

    public Map<String, String> sign(String tenantId, String agentName, String body) {
        String timestamp = Instant.now(clock).toString();
        String nonce = nonceSupplier.get();
        String bodySha256 = sha256Hex(body);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_TENANT, Objects.requireNonNull(tenantId, "tenantId must not be null"));
        headers.put(HEADER_AGENT, Objects.requireNonNull(agentName, "agentName must not be null"));
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_NONCE, Objects.requireNonNull(nonce, "nonce must not be null"));
        headers.put(HEADER_BODY_SHA256, bodySha256);
        headers.put(HEADER_SIGNATURE, signPayload(secret, signaturePayload(tenantId, agentName, timestamp, nonce,
                bodySha256)));
        return Map.copyOf(headers);
    }

    static String signaturePayload(String tenantId, String agentName, String timestamp, String nonce,
            String bodySha256) {
        return tenantId + "\n" + agentName + "\n" + timestamp + "\n" + nonce + "\n" + bodySha256;
    }

    static String sha256Hex(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(Objects.requireNonNullElse(body, "").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash A2A body", ex);
        }
    }

    static String signPayload(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return hex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign A2A request", ex);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
