/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.time.Instant;
import java.util.Objects;

public record SandboxArtifactScannerHealth(Instant checkedAt,
                                           String scannerId,
                                           String scannerMode,
                                           String status,
                                           boolean externalEngine,
                                           boolean available) {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    public SandboxArtifactScannerHealth {
        checkedAt = Objects.requireNonNullElseGet(checkedAt, Instant::now);
        scannerId = textOrDefault(scannerId, "unknown");
        scannerMode = textOrDefault(scannerMode, "UNKNOWN");
        status = available ? STATUS_AVAILABLE : STATUS_UNAVAILABLE;
    }

    public static SandboxArtifactScannerHealth unavailable() {
        return new SandboxArtifactScannerHealth(Instant.now(), "unknown", "UNAVAILABLE", STATUS_UNAVAILABLE, false, false);
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
