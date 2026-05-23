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

public record MemoryGarbageCollectionResult(
        String reason,
        int scannedCount,
        int derivedIndexCandidateCount,
        int archiveCandidateCount,
        int physicalDeleteCandidateCount,
        int archivedCount,
        int physicallyDeletedCount,
        int enqueuedDeleteTaskCount,
        int markedIndexDeletedCount,
        boolean dryRun,
        List<String> errors,
        Instant executedAt
) {

    public MemoryGarbageCollectionResult(String reason,
                                         int scannedCount,
                                         int enqueuedDeleteTaskCount,
                                         int markedIndexDeletedCount,
                                         boolean dryRun,
                                         List<String> errors,
                                         Instant executedAt) {
        this(reason,
                scannedCount,
                scannedCount,
                0,
                0,
                0,
                0,
                enqueuedDeleteTaskCount,
                markedIndexDeletedCount,
                dryRun,
                errors,
                executedAt);
    }

    public MemoryGarbageCollectionResult(String reason,
                                         int scannedCount,
                                         int archivedCount,
                                         int enqueuedDeleteTaskCount,
                                         int markedIndexDeletedCount,
                                         boolean dryRun,
                                         List<String> errors,
                                         Instant executedAt) {
        this(reason,
                scannedCount,
                0,
                0,
                0,
                archivedCount,
                0,
                enqueuedDeleteTaskCount,
                markedIndexDeletedCount,
                dryRun,
                errors,
                executedAt);
    }

    public MemoryGarbageCollectionResult {
        reason = Objects.requireNonNullElse(reason, "");
        scannedCount = Math.max(0, scannedCount);
        derivedIndexCandidateCount = Math.max(0, derivedIndexCandidateCount);
        archiveCandidateCount = Math.max(0, archiveCandidateCount);
        physicalDeleteCandidateCount = Math.max(0, physicalDeleteCandidateCount);
        archivedCount = Math.max(0, archivedCount);
        physicallyDeletedCount = Math.max(0, physicallyDeletedCount);
        enqueuedDeleteTaskCount = Math.max(0, enqueuedDeleteTaskCount);
        markedIndexDeletedCount = Math.max(0, markedIndexDeletedCount);
        errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        executedAt = Objects.requireNonNullElse(executedAt, Instant.EPOCH);
    }
}
