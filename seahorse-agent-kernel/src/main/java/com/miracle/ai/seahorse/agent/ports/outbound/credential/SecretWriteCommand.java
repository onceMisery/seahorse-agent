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

package com.miracle.ai.seahorse.agent.ports.outbound.credential;

import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadataPolicy;

import java.time.Instant;
import java.util.Objects;

/**
 * Secret write command for durable secret stores.
 */
public record SecretWriteCommand(
        String secretRef,
        String tenantId,
        SecretValue secretValue,
        String metadataJson,
        Instant createdAt
) {

    public SecretWriteCommand {
        secretRef = requireText(secretRef, "secretRef must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        secretValue = Objects.requireNonNull(secretValue, "secretValue must not be null");
        if (!secretValue.hasText()) {
            throw new IllegalArgumentException("secretValue must not be blank");
        }
        metadataJson = SecretMetadataPolicy.normalizeMetadataJson(metadataJson, secretValue.reveal());
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
