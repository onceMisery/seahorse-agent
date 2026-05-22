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

public record MemoryMaintenanceRunRecord(
        String runId,
        String reason,
        String status,
        boolean compactionRequested,
        boolean aliasRequested,
        boolean garbageCollectionRequested,
        int compactionScannedCount,
        int compactionGroupCount,
        int compactionFragmentCount,
        int aliasScannedCount,
        int aliasNormalizedCount,
        int aliasDictionaryMatchCount,
        int aliasSkippedCount,
        int gcScannedCount,
        int gcEnqueuedCount,
        int gcMarkedCount,
        boolean gcDryRun,
        List<String> skippedTasks,
        List<String> errors,
        Instant createTime,
        Instant updateTime
) {

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_SUCCEEDED_WITH_WARNINGS = "SUCCEEDED_WITH_WARNINGS";
    public static final String STATUS_FAILED = "FAILED";

    public MemoryMaintenanceRunRecord(String runId,
                                      String reason,
                                      String status,
                                      boolean compactionRequested,
                                      boolean aliasRequested,
                                      boolean garbageCollectionRequested,
                                      int compactionScannedCount,
                                      int compactionGroupCount,
                                      int compactionFragmentCount,
                                      int gcScannedCount,
                                      int gcEnqueuedCount,
                                      int gcMarkedCount,
                                      boolean gcDryRun,
                                      List<String> skippedTasks,
                                      List<String> errors,
                                      Instant createTime,
                                      Instant updateTime) {
        this(runId,
                reason,
                status,
                compactionRequested,
                aliasRequested,
                garbageCollectionRequested,
                compactionScannedCount,
                compactionGroupCount,
                compactionFragmentCount,
                0,
                0,
                0,
                0,
                gcScannedCount,
                gcEnqueuedCount,
                gcMarkedCount,
                gcDryRun,
                skippedTasks,
                errors,
                createTime,
                updateTime);
    }

    public MemoryMaintenanceRunRecord(String runId,
                                      String reason,
                                      String status,
                                      boolean compactionRequested,
                                      boolean aliasRequested,
                                      boolean garbageCollectionRequested,
                                      int gcScannedCount,
                                      int gcEnqueuedCount,
                                      int gcMarkedCount,
                                      boolean gcDryRun,
                                      List<String> skippedTasks,
                                      List<String> errors,
                                      Instant createTime,
                                      Instant updateTime) {
        this(runId,
                reason,
                status,
                compactionRequested,
                aliasRequested,
                garbageCollectionRequested,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                gcScannedCount,
                gcEnqueuedCount,
                gcMarkedCount,
                gcDryRun,
                skippedTasks,
                errors,
                createTime,
                updateTime);
    }

    public MemoryMaintenanceRunRecord {
        runId = Objects.requireNonNullElse(runId, "").trim();
        reason = Objects.requireNonNullElse(reason, "").trim();
        status = normalizeStatus(status);
        compactionScannedCount = Math.max(0, compactionScannedCount);
        compactionGroupCount = Math.max(0, compactionGroupCount);
        compactionFragmentCount = Math.max(0, compactionFragmentCount);
        aliasScannedCount = Math.max(0, aliasScannedCount);
        aliasNormalizedCount = Math.max(0, aliasNormalizedCount);
        aliasDictionaryMatchCount = Math.max(0, aliasDictionaryMatchCount);
        aliasSkippedCount = Math.max(0, aliasSkippedCount);
        gcScannedCount = Math.max(0, gcScannedCount);
        gcEnqueuedCount = Math.max(0, gcEnqueuedCount);
        gcMarkedCount = Math.max(0, gcMarkedCount);
        skippedTasks = List.copyOf(Objects.requireNonNullElse(skippedTasks, List.of()));
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElse(updateTime, createTime);
    }

    public static String normalizeStatus(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim().toUpperCase();
        return normalized.isBlank() ? STATUS_SUCCEEDED : normalized;
    }
}
