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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MemoryGarbageCollectionService {

    private final MemoryGarbageCollectionPort garbageCollectionPort;
    private final MemoryOutboxPort outboxPort;
    private final MemoryGarbageCollectionOptions options;

    public MemoryGarbageCollectionService(MemoryGarbageCollectionPort garbageCollectionPort,
                                          MemoryOutboxPort outboxPort,
                                          MemoryGarbageCollectionOptions options) {
        this.garbageCollectionPort = garbageCollectionPort == null
                ? MemoryGarbageCollectionPort.noop()
                : garbageCollectionPort;
        this.outboxPort = outboxPort == null ? MemoryOutboxPort.noop() : outboxPort;
        this.options = Objects.requireNonNullElseGet(options, MemoryGarbageCollectionOptions::vectorOnly);
    }

    public MemoryGarbageCollectionResult run(String reason) {
        Instant now = Instant.now();
        List<String> errors = new ArrayList<>();
        List<MemoryGarbageCollectionCandidate> derivedIndexCandidates = scanDerivedIndexDeleteCandidates(now, errors);
        List<MemoryGarbageCollectionCandidate> archiveCandidates = scanLifecycleArchiveCandidates(now, errors);
        List<MemoryGarbageCollectionCandidate> physicalDeleteCandidates = scanPhysicalDeleteCandidates(now, errors);
        int enqueued = 0;
        int marked = 0;
        int archived = 0;
        int physicallyDeleted = 0;
        if (!options.dryRun()) {
            List<String> successfullyQueuedIds = new ArrayList<>();
            for (MemoryGarbageCollectionCandidate candidate : archiveCandidates) {
                if (markArchived(candidate, now, errors)) {
                    archived++;
                    EnqueueResult enqueueResult = enqueueDeletes(candidate, errors);
                    if (enqueueResult.queuedCount() > 0) {
                        enqueued += enqueueResult.queuedCount();
                    }
                    if (enqueueResult.completed()) {
                        successfullyQueuedIds.add(candidate.memoryId());
                    }
                }
            }
            for (MemoryGarbageCollectionCandidate candidate : derivedIndexCandidates) {
                EnqueueResult enqueueResult = enqueueDeletes(candidate, errors);
                if (enqueueResult.queuedCount() > 0) {
                    enqueued += enqueueResult.queuedCount();
                }
                if (enqueueResult.completed()) {
                    successfullyQueuedIds.add(candidate.memoryId());
                }
            }
            marked = markDeleted(successfullyQueuedIds, now, errors);
            physicallyDeleted = markPhysicallyDeleted(physicalDeleteCandidates, now, errors);
        }
        return new MemoryGarbageCollectionResult(
                Objects.requireNonNullElse(reason, "manual-gc"),
                derivedIndexCandidates.size() + archiveCandidates.size() + physicalDeleteCandidates.size(),
                derivedIndexCandidates.size(),
                archiveCandidates.size(),
                physicalDeleteCandidates.size(),
                archived,
                physicallyDeleted,
                enqueued,
                marked,
                options.dryRun(),
                errors,
                now);
    }

    private List<MemoryGarbageCollectionCandidate> scanDerivedIndexDeleteCandidates(Instant now, List<String> errors) {
        try {
            return garbageCollectionPort.scanDerivedIndexDeleteCandidates(
                    now,
                    options.retention(),
                    options.scanLimit());
        } catch (RuntimeException ex) {
            errors.add("scan:" + errorMessage(ex));
            return List.of();
        }
    }

    private List<MemoryGarbageCollectionCandidate> scanPhysicalDeleteCandidates(Instant now, List<String> errors) {
        if (!options.physicalDeleteEnabled()) {
            return List.of();
        }
        try {
            return garbageCollectionPort.scanPhysicalDeleteCandidates(
                    now,
                    options.physicalDeleteRetention(),
                    options.scanLimit());
        } catch (RuntimeException ex) {
            errors.add("physical-delete-scan:" + errorMessage(ex));
            return List.of();
        }
    }

    private List<MemoryGarbageCollectionCandidate> scanLifecycleArchiveCandidates(Instant now, List<String> errors) {
        if (!options.archiveEnabled()) {
            return List.of();
        }
        try {
            return garbageCollectionPort.scanLifecycleArchiveCandidates(
                    now,
                    options.archiveIdleRetention(),
                    options.archiveScoreThreshold(),
                    options.scanLimit());
        } catch (RuntimeException ex) {
            errors.add("archive-scan:" + errorMessage(ex));
            return List.of();
        }
    }

    private boolean markArchived(MemoryGarbageCollectionCandidate candidate, Instant now, List<String> errors) {
        if (candidate == null || candidate.memoryId().isBlank()) {
            return false;
        }
        try {
            return garbageCollectionPort.markArchived(
                    List.of(candidate.memoryId()),
                    now,
                    "generational gc archive") > 0;
        } catch (RuntimeException ex) {
            errors.add(candidate.memoryId() + ":archive:" + errorMessage(ex));
            return false;
        }
    }

    private int markPhysicallyDeleted(List<MemoryGarbageCollectionCandidate> candidates,
                                      Instant now,
                                      List<String> errors) {
        List<String> safeIds = candidates.stream()
                .filter(Objects::nonNull)
                .map(MemoryGarbageCollectionCandidate::memoryId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (safeIds.isEmpty()) {
            return 0;
        }
        try {
            return garbageCollectionPort.markPhysicallyDeleted(safeIds, now);
        } catch (RuntimeException ex) {
            errors.add("physical-delete:" + errorMessage(ex));
            return 0;
        }
    }

    private EnqueueResult enqueueDeletes(MemoryGarbageCollectionCandidate candidate, List<String> errors) {
        if (candidate == null || candidate.memoryId().isBlank()) {
            return new EnqueueResult(0, false);
        }
        int queued = 0;
        boolean attempted = false;
        boolean failed = false;
        if (options.vectorIndexEnabled()) {
            attempted = true;
            if (enqueueDelete(candidate, MemoryOutboxPort.MemoryOutboxTask.vectorDelete(
                    candidate.memoryId(),
                    candidate.userId(),
                    candidate.tenantId()), errors)) {
                queued++;
            } else {
                failed = true;
            }
        }
        if (options.keywordIndexEnabled()) {
            attempted = true;
            if (enqueueDelete(candidate, MemoryOutboxPort.MemoryOutboxTask.keywordDelete(
                    candidate.memoryId(),
                    candidate.userId(),
                    candidate.tenantId()), errors)) {
                queued++;
            } else {
                failed = true;
            }
        }
        if (options.graphIndexEnabled()) {
            attempted = true;
            if (enqueueDelete(candidate, MemoryOutboxPort.MemoryOutboxTask.graphDelete(
                    candidate.memoryId(),
                    candidate.userId(),
                    candidate.tenantId()), errors)) {
                queued++;
            } else {
                failed = true;
            }
        }
        return new EnqueueResult(queued, attempted && !failed);
    }

    private boolean enqueueDelete(MemoryGarbageCollectionCandidate candidate,
                                  MemoryOutboxPort.MemoryOutboxTask task,
                                  List<String> errors) {
        try {
            outboxPort.enqueue(task);
            return true;
        } catch (RuntimeException ex) {
            errors.add(candidate.memoryId() + ":" + task.taskType() + ":" + errorMessage(ex));
            return false;
        }
    }

    private int markDeleted(List<String> memoryIds, Instant now, List<String> errors) {
        List<String> safeIds = memoryIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (safeIds.isEmpty()) {
            return 0;
        }
        try {
            return garbageCollectionPort.markDerivedIndexesDeleted(safeIds, now);
        } catch (RuntimeException ex) {
            errors.add("mark:" + errorMessage(ex));
            return 0;
        }
    }

    private String errorMessage(RuntimeException ex) {
        return Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName());
    }

    private record EnqueueResult(int queuedCount, boolean completed) {
    }
}
