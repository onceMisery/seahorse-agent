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

package com.miracle.ai.seahorse.agent.kernel.domain.credential;

import java.time.Instant;

/**
 * Secret metadata returned to management callers. It never carries secret plaintext.
 *
 * @param secretRef   unique secret reference identifier
 * @param tenantId    owning tenant
 * @param name        human-readable secret name
 * @param secretType  type classification (e.g. API_KEY, OAUTH_TOKEN)
 * @param maskedHint  masked hint for display (e.g. "sk-****abcd")
 * @param status      current lifecycle status
 * @param expiresAt   optional expiry time
 * @param createdAt   creation timestamp
 */
public record SecretMetadata(
        String secretRef,
        String tenantId,
        String name,
        String secretType,
        String maskedHint,
        SecretStatus status,
        Instant expiresAt,
        Instant createdAt
) {

    public SecretMetadata {
        secretRef = requireText(secretRef, "secretRef must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        status = status == null ? SecretStatus.ACTIVE : status;
    }

    /**
     * Backward-compatible constructor for existing callers using the legacy
     * {@code (secretRef, tenantId, metadataJson, createdAt, rotatedAt)} signature.
     *
     * @param secretRef     unique secret reference
     * @param tenantId      owning tenant
     * @param metadataJson  metadata JSON (mapped to maskedHint for display)
     * @param createdAt     creation timestamp
     * @param rotatedAt     rotation timestamp (unused in new schema, retained for compat)
     */
    public SecretMetadata(String secretRef, String tenantId, String metadataJson,
                          Instant createdAt, Instant rotatedAt) {
        this(secretRef, tenantId, null, null, metadataJson, SecretStatus.ACTIVE, null, createdAt);
    }

    /**
     * Backward-compatible accessor: returns the maskedHint as the legacy metadataJson field.
     */
    public String metadataJson() {
        return maskedHint != null ? maskedHint : "";
    }

    /**
     * Backward-compatible accessor: rotation timestamp is no longer tracked on this record.
     */
    public Instant rotatedAt() {
        return null;
    }

    /**
     * Returns {@code true} if this secret has passed its expiry time.
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /**
     * Returns {@code true} if the secret is currently in ACTIVE status.
     */
    public boolean isActive() {
        return status == SecretStatus.ACTIVE;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
