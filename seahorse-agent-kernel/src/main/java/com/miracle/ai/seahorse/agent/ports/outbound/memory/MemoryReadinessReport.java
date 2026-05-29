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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MemoryReadinessReport(
        String userId,
        String tenantId,
        String status,
        List<MemoryReadinessCapability> capabilities,
        List<String> gaps,
        Instant generatedAt
) {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_NO_EVIDENCE = "NO_EVIDENCE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_MANUAL_EXPORT_ONLY = "MANUAL_EXPORT_ONLY";

    public MemoryReadinessReport {
        userId = Objects.requireNonNullElse(userId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "default");
        status = Objects.requireNonNullElse(status, STATUS_NO_EVIDENCE);
        capabilities = List.copyOf(Objects.requireNonNullElse(capabilities, List.of()));
        gaps = List.copyOf(Objects.requireNonNullElse(gaps, List.of()));
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
    }

    public Optional<MemoryReadinessCapability> capability(String name) {
        String expected = Objects.requireNonNullElse(name, "");
        return capabilities.stream()
                .filter(capability -> expected.equals(capability.name()))
                .findFirst();
    }

    public record MemoryReadinessCapability(
            String name,
            String status,
            boolean enabled,
            int evidenceCount,
            Instant lastEvidenceAt,
            List<String> gaps,
            Map<String, Object> details
    ) {

        public MemoryReadinessCapability {
            name = Objects.requireNonNullElse(name, "");
            status = Objects.requireNonNullElse(status, STATUS_NO_EVIDENCE);
            gaps = List.copyOf(Objects.requireNonNullElse(gaps, List.of()));
            details = Map.copyOf(Objects.requireNonNullElse(details, Map.of()));
            lastEvidenceAt = Objects.requireNonNullElse(lastEvidenceAt, Instant.EPOCH);
        }
    }
}
