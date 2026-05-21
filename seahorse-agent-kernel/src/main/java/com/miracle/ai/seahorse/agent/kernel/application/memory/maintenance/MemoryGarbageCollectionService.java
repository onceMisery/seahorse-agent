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
        List<MemoryGarbageCollectionCandidate> candidates = scanCandidates(now, errors);
        int enqueued = 0;
        int marked = 0;
        if (!options.dryRun()) {
            List<String> successfullyQueuedIds = new ArrayList<>();
            for (MemoryGarbageCollectionCandidate candidate : candidates) {
                EnqueueResult enqueueResult = enqueueDeletes(candidate, errors);
                if (enqueueResult.queuedCount() > 0) {
                    enqueued += enqueueResult.queuedCount();
                }
                if (enqueueResult.completed()) {
                    successfullyQueuedIds.add(candidate.memoryId());
                }
            }
            marked = markDeleted(successfullyQueuedIds, now, errors);
        }
        return new MemoryGarbageCollectionResult(
                Objects.requireNonNullElse(reason, "manual-gc"),
                candidates.size(),
                enqueued,
                marked,
                options.dryRun(),
                errors,
                now);
    }

    private List<MemoryGarbageCollectionCandidate> scanCandidates(Instant now, List<String> errors) {
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
