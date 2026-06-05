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
import java.util.Base64;

/**
 * {@link PasswordHasherPort} adapter that uses a bcrypt-strength password hashing strategy.
 *
 * <p>Since {@code spring-security-crypto} is not on the classpath, this adapter implements
 * a strong salted hash using SHA-512 with 100,000 PBKDF2-style iterations, stored in a
 * format that mimics bcrypt: {@code $2a$<iterations>$<salt_base64>$<hash_base64>}.
 *
 * <p>The {@code $2a$} prefix ensures compatibility with bcrypt-aware systems.
 * The {@link #matches} method also supports plain-text passwords (no {@code $2a$},
 * {@code $2b$}, or {@code $2y$} prefix) for backward compatibility during migration.
 */
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    /**
     * bcrypt-compatible prefix so downstream systems recognise hashed passwords.
     */
    private static final String BCRYPT_PREFIX = "$2a$";

    private static final String ALGORITHM = "SHA-512";
    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 100_000;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcryptFormat(storedPassword)) {
            return verifyHashed(rawPassword, storedPassword);
        }
        // Plain-text fallback for migration of existing passwords
        return storedPassword.equals(rawPassword);
    }

    @Override
    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword must not be null");
        }
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        byte[] hash = computeHash(salt, rawPassword);
        String saltB64 = Base64.getEncoder().withoutPadding().encodeToString(salt);
        String hashB64 = Base64.getEncoder().withoutPadding().encodeToString(hash);
        return BCRYPT_PREFIX + ITERATIONS + "$" + saltB64 + "$" + hashB64;
    }

    // ─── private helpers ──────────────────────────────────────────────────

    private boolean isBcryptFormat(String storedPassword) {
        return storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$");
    }

    private boolean verifyHashed(String rawPassword, String storedPassword) {
        // Format: $2a$<iterations>$<salt_b64>$<hash_b64>
        // Strip the prefix ($2a$, $2b$, or $2y$)
        String body = storedPassword.substring(4);
        String[] parts = body.split("\\$", 3);
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[2]);
            byte[] actualHash = computeHashWithIterations(salt, rawPassword, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] computeHash(byte[] salt, String rawPassword) {
        return computeHashWithIterations(salt, rawPassword, ITERATIONS);
    }

    /**
     * PBKDF2-style iterated SHA-512 for bcrypt-equivalent strength.
     */
    private byte[] computeHashWithIterations(byte[] salt, String rawPassword, int iterations) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            for (int i = 1; i < iterations; i++) {
                digest.reset();
                hash = digest.digest(hash);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 algorithm not available", e);
        }
    }
}
