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

public record SandboxRuntimeNodeRegistration(String nodeId,
                                             String runtime,
                                             String engine,
                                             String observedHealthStatus,
                                             boolean observedAdmissionAvailable,
                                             String observedAdmissionStatus,
                                             int observedActiveSessionCount,
                                             int observedActiveSessionLimit,
                                             long observedWorkspaceFreeBytes,
                                             Instant observedAt,
                                             Instant heartbeatAt,
                                             Instant expiresAt,
                                             String registrationStatus) {

    public static final String REGISTRATION_LIVE = "LIVE";
    public static final String REGISTRATION_STALE = "STALE";

    public SandboxRuntimeNodeRegistration {
        nodeId = requireText(nodeId, "nodeId must not be blank");
        runtime = requireText(runtime, "runtime must not be blank");
        engine = engine == null ? "" : engine.trim();
        observedHealthStatus = requireText(observedHealthStatus, "observedHealthStatus must not be blank");
        observedAdmissionStatus = requireText(observedAdmissionStatus, "observedAdmissionStatus must not be blank");
        observedActiveSessionCount = Math.max(observedActiveSessionCount, 0);
        observedActiveSessionLimit = Math.max(observedActiveSessionLimit, 0);
        observedWorkspaceFreeBytes = Math.max(observedWorkspaceFreeBytes, -1L);
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        heartbeatAt = Objects.requireNonNull(heartbeatAt, "heartbeatAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(heartbeatAt)) {
            throw new IllegalArgumentException("expiresAt must be after heartbeatAt");
        }
        registrationStatus = normalizeRegistrationStatus(registrationStatus);
    }

    public static SandboxRuntimeNodeRegistration live(SandboxRuntimeNodeHealth health,
                                                      Instant heartbeatAt,
                                                      Instant expiresAt) {
        SandboxRuntimeNodeHealth safeHealth = Objects.requireNonNull(health, "health must not be null");
        return new SandboxRuntimeNodeRegistration(
                safeHealth.nodeId(),
                safeHealth.runtime(),
                safeHealth.engine(),
                safeHealth.status(),
                safeHealth.admissionAvailable(),
                safeHealth.admissionStatus(),
                safeHealth.activeSessionCount(),
                safeHealth.activeSessionLimit(),
                safeHealth.workspaceFreeBytes(),
                safeHealth.checkedAt(),
                heartbeatAt,
                expiresAt,
                REGISTRATION_LIVE);
    }

    private static String normalizeRegistrationStatus(String value) {
        if (REGISTRATION_LIVE.equals(value) || REGISTRATION_STALE.equals(value)) {
            return value;
        }
        return REGISTRATION_STALE;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
