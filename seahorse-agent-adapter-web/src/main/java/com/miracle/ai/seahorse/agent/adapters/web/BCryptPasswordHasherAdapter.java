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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * {@link PasswordHasherPort} adapter that uses SHA-256 with a random salt.
 *
 * <p>Hashed values are stored in the format {@code $sha256$<salt_hex>$<hash_hex>}.
 * The {@link #matches} method also supports plain-text passwords for backward
 * compatibility during migration.
 */
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private static final String PREFIX = "$sha256$";
    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith(PREFIX)) {
            return verifyHashed(rawPassword, storedPassword);
        }
        // Plain-text fallback for migration compatibility
        return storedPassword.equals(rawPassword);
    }

    @Override
    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword must not be null");
        }
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        String hashHex = computeHash(salt, rawPassword);
        return PREFIX + saltHex + "$" + hashHex;
    }

    // ─── private helpers ──────────────────────────────────────────────────

    private boolean verifyHashed(String rawPassword, String storedPassword) {
        // Format: $sha256$<salt_hex>$<hash_hex>
        String body = storedPassword.substring(PREFIX.length());
        int separatorIndex = body.indexOf('$');
        if (separatorIndex < 0) {
            return false;
        }
        String saltHex = body.substring(0, separatorIndex);
        String expectedHashHex = body.substring(separatorIndex + 1);
        byte[] salt = HexFormat.of().parseHex(saltHex);
        String actualHashHex = computeHash(salt, rawPassword);
        return expectedHashHex.equals(actualHashHex);
    }

    private String computeHash(byte[] salt, String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
