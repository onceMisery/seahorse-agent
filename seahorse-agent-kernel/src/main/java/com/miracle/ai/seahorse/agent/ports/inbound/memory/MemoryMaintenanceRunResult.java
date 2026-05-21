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

package com.miracle.ai.seahorse.agent.ports.inbound.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record MemoryMaintenanceRunResult(
        String reason,
        boolean compactionEnabled,
        boolean aliasEnabled,
        boolean garbageCollectionEnabled,
        MemoryCompactionResult compactionResult,
        MemoryGarbageCollectionResult garbageCollectionResult,
        List<String> skippedTasks,
        List<String> errors,
        Instant executedAt
) {

    public static final String SKIP_COMPACTION_UNAVAILABLE = "COMPACTION_UNAVAILABLE";
    public static final String SKIP_ALIAS_UNAVAILABLE = "ALIAS_UNAVAILABLE";
    public static final String SKIP_GARBAGE_COLLECTION_DISABLED = "GARBAGE_COLLECTION_DISABLED";

    public MemoryMaintenanceRunResult(String reason,
                                      boolean compactionEnabled,
                                      boolean aliasEnabled,
                                      boolean garbageCollectionEnabled,
                                      MemoryGarbageCollectionResult garbageCollectionResult,
                                      List<String> skippedTasks,
                                      List<String> errors,
                                      Instant executedAt) {
        this(reason,
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled,
                null,
                garbageCollectionResult,
                skippedTasks,
                errors,
                executedAt);
    }

    public MemoryMaintenanceRunResult {
        reason = Objects.requireNonNullElse(reason, MemoryMaintenanceRunCommand.DEFAULT_REASON);
        if (reason.isBlank()) {
            reason = MemoryMaintenanceRunCommand.DEFAULT_REASON;
        }
        skippedTasks = List.copyOf(Objects.requireNonNullElse(skippedTasks, List.of()));
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        executedAt = Objects.requireNonNullElseGet(executedAt, Instant::now);
    }
}
