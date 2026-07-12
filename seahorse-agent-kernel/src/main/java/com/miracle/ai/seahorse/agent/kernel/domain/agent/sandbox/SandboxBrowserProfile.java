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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.time.Instant;
import java.util.Objects;

public record SandboxBrowserProfile(String profileId,
                                    String tenantId,
                                    String name,
                                    String sessionStateArtifactId,
                                    SandboxBrowserProfileStatus status,
                                    Instant expiresAt,
                                    Instant createdAt,
                                    Instant updatedAt) {

    public static final int MAX_NAME_LENGTH = 96;

    public SandboxBrowserProfile {
        profileId = requireText(profileId, "profileId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        name = requireName(name);
        sessionStateArtifactId = requireText(sessionStateArtifactId, "sessionStateArtifactId must not be blank");
        status = Objects.requireNonNullElse(status, SandboxBrowserProfileStatus.ACTIVE);
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean usableAt(Instant now) {
        return status == SandboxBrowserProfileStatus.ACTIVE && expiresAt.isAfter(now);
    }

    private static String requireName(String value) {
        String normalized = requireText(value, "name must not be blank");
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must not exceed " + MAX_NAME_LENGTH + " chars");
        }
        return normalized;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
