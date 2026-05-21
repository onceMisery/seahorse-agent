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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;

import java.util.Map;
import java.util.Objects;

public record MemoryAliasResolutionOptions(
        int scanLimit,
        String userId,
        String tenantId,
        double autoResolveConfidenceThreshold,
        Map<String, MemoryAliasCandidate> dictionary
) {

    private static final int DEFAULT_SCAN_LIMIT = 100;
    private static final String DEFAULT_TENANT_ID = "default";
    private static final double DEFAULT_AUTO_RESOLVE_CONFIDENCE_THRESHOLD = 0.95D;

    public MemoryAliasResolutionOptions {
        scanLimit = scanLimit <= 0 ? DEFAULT_SCAN_LIMIT : scanLimit;
        userId = Objects.requireNonNullElse(userId, "").trim();
        tenantId = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID).trim();
        if (tenantId.isBlank()) {
            tenantId = DEFAULT_TENANT_ID;
        }
        autoResolveConfidenceThreshold = Math.max(0D, Math.min(1D, autoResolveConfidenceThreshold));
        dictionary = Map.copyOf(Objects.requireNonNullElse(dictionary, Map.of()));
    }

    public static MemoryAliasResolutionOptions defaults() {
        return new MemoryAliasResolutionOptions(
                DEFAULT_SCAN_LIMIT,
                "",
                DEFAULT_TENANT_ID,
                DEFAULT_AUTO_RESOLVE_CONFIDENCE_THRESHOLD,
                Map.of());
    }
}
