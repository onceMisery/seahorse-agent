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

package com.miracle.ai.seahorse.agent.kernel.application.credential;

import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadata;
import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretMetadataQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWriteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretWritePort;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service for secret lifecycle management: rotation, disabling, and expiry checks.
 */
public class SecretRotationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretRotationService.class);

    private final SecretStorePort secretStorePort;
    private final SecretWritePort secretWritePort;
    private final SecretMetadataQueryPort secretMetadataQueryPort;
    private final Clock clock;

    public SecretRotationService(SecretStorePort secretStorePort,
                                 SecretWritePort secretWritePort,
                                 SecretMetadataQueryPort secretMetadataQueryPort,
                                 Clock clock) {
        this.secretStorePort = Objects.requireNonNull(secretStorePort, "secretStorePort must not be null");
        this.secretWritePort = Objects.requireNonNull(secretWritePort, "secretWritePort must not be null");
        this.secretMetadataQueryPort = Objects.requireNonNull(secretMetadataQueryPort,
                "secretMetadataQueryPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    /**
     * Rotate a secret: validates current status, stores new value, marks old version as ROTATED.
     *
     * @param tenantId  the owning tenant
     * @param secretRef the secret reference to rotate
     * @param newValue  the new secret plaintext value
     * @return updated secret metadata
     * @throws IllegalStateException if the secret is not in ACTIVE status
     */
    public SecretMetadata rotate(String tenantId, String secretRef, String newValue) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(secretRef, "secretRef must not be null");
        Objects.requireNonNull(newValue, "newValue must not be null");

        SecretMetadata current = secretMetadataQueryPort.findByRef(tenantId, secretRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Secret not found: tenantId=" + tenantId + ", secretRef=" + secretRef));

        if (!current.isActive()) {
            throw new IllegalStateException(
                    "Cannot rotate secret in status " + current.status() + "; must be ACTIVE");
        }

        Instant now = clock.instant();

        // Write new secret version
        SecretWriteCommand writeCommand = new SecretWriteCommand(
                secretRef,
                tenantId,
                SecretValue.of(newValue),
                current.maskedHint() != null ? current.maskedHint() : "",
                now);
        SecretMetadata updated = secretWritePort.putSecret(writeCommand);

        // Mark old version as ROTATED
        secretMetadataQueryPort.updateStatus(secretRef, SecretStatus.ROTATED, tenantId, now);

        LOGGER.info("Rotated secret {} for tenant {}", secretRef, tenantId);
        return updated;
    }

    /**
     * Disable a secret, preventing further use.
     *
     * @param tenantId  the owning tenant
     * @param secretRef the secret reference to disable
     */
    public void disable(String tenantId, String secretRef) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(secretRef, "secretRef must not be null");

        secretMetadataQueryPort.findByRef(tenantId, secretRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Secret not found: tenantId=" + tenantId + ", secretRef=" + secretRef));

        Instant now = clock.instant();
        secretMetadataQueryPort.updateStatus(secretRef, SecretStatus.DISABLED, tenantId, now);
        LOGGER.info("Disabled secret {} for tenant {}", secretRef, tenantId);
    }

    /**
     * Scan for all ACTIVE secrets that have passed their expiresAt and mark them as EXPIRED.
     *
     * @return number of secrets marked as expired
     */
    public int checkExpiry() {
        Instant now = clock.instant();
        List<SecretMetadata> expired = secretMetadataQueryPort.findExpiredBefore(now);

        for (SecretMetadata secret : expired) {
            secretMetadataQueryPort.updateStatus(secret.secretRef(), SecretStatus.EXPIRED, "system", now);
            LOGGER.info("Expired secret {} for tenant {}", secret.secretRef(), secret.tenantId());
        }

        return expired.size();
    }
}
