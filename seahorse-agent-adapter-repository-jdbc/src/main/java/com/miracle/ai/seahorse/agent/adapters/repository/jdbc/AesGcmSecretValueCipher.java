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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-GCM cipher for secret-at-rest storage.
 */
public class AesGcmSecretValueCipher implements SecretValueCipher {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final int AES_128_KEY_BYTES = 16;
    private static final int AES_192_KEY_BYTES = 24;
    private static final int AES_256_KEY_BYTES = 32;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom;

    public AesGcmSecretValueCipher(byte[] key) {
        this(key, new SecureRandom());
    }

    AesGcmSecretValueCipher(byte[] key, SecureRandom secureRandom) {
        byte[] safeKey = Objects.requireNonNull(key, "key must not be null").clone();
        if (!isSupportedKeyLength(safeKey.length)) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        this.keySpec = new SecretKeySpec(safeKey, AES_ALGORITHM);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public static AesGcmSecretValueCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("base64Key must not be blank");
        }
        return new AesGcmSecretValueCipher(Base64.getDecoder().decode(base64Key.trim()));
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext must not be blank");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to encrypt secret", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("ciphertext must not be blank");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.trim());
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("ciphertext payload is invalid");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("failed to decrypt secret", ex);
        }
    }

    private boolean isSupportedKeyLength(int length) {
        return length == AES_128_KEY_BYTES || length == AES_192_KEY_BYTES || length == AES_256_KEY_BYTES;
    }
}
