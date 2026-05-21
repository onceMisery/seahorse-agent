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
import java.util.Objects;

public record MemoryAliasResolutionRunResult(
        String reason,
        int scannedCount,
        int normalizedCount,
        int dictionaryMatchCount,
        int skippedCount,
        List<String> errors,
        Instant executedAt
) {

    public MemoryAliasResolutionRunResult {
        reason = Objects.requireNonNullElse(reason, "manual-alias-resolution").trim();
        if (reason.isBlank()) {
            reason = "manual-alias-resolution";
        }
        scannedCount = Math.max(0, scannedCount);
        normalizedCount = Math.max(0, normalizedCount);
        dictionaryMatchCount = Math.max(0, dictionaryMatchCount);
        skippedCount = Math.max(0, skippedCount);
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        executedAt = Objects.requireNonNullElseGet(executedAt, Instant::now);
    }

    public static MemoryAliasResolutionRunResult empty(String reason) {
        return new MemoryAliasResolutionRunResult(reason, 0, 0, 0, 0, List.of(), Instant.now());
    }
}
