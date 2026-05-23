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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryGarbageCollectionServiceTests {

    @Test
    void shouldEnqueueDerivedIndexDeleteTasksForEligibleCandidates() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        gcPort.candidates.add(new MemoryGarbageCollectionCandidate(
                "m-obsolete", "user-1", "tenant-1", "short_term", "OBSOLETE", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(20, Duration.ofDays(7), false, true, true, true));

        MemoryGarbageCollectionResult result = service.run("manual");

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.enqueuedDeleteTaskCount()).isEqualTo(3);
        assertThat(result.markedIndexDeletedCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(outboxPort.tasks)
                .extracting(MemoryOutboxPort.MemoryOutboxTask::taskType)
                .containsExactly(
                        MemoryOutboxTaskTypes.VECTOR_DELETE,
                        MemoryOutboxTaskTypes.KEYWORD_DELETE,
                        MemoryOutboxTaskTypes.GRAPH_DELETE);
        assertThat(outboxPort.tasks)
                .extracting(MemoryOutboxPort.MemoryOutboxTask::targetId)
                .containsExactly("m-obsolete", "m-obsolete", "m-obsolete");
        assertThat(gcPort.markedIds).containsExactly("m-obsolete");
    }

    @Test
    void shouldRespectDryRun() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        gcPort.candidates.add(new MemoryGarbageCollectionCandidate(
                "m-obsolete", "user-1", "tenant-1", "semantic", "COMPACTED", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(20, Duration.ofDays(7), true, true, true, true));

        MemoryGarbageCollectionResult result = service.run("dry-run");

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.enqueuedDeleteTaskCount()).isZero();
        assertThat(result.markedIndexDeletedCount()).isZero();
        assertThat(outboxPort.tasks).isEmpty();
        assertThat(gcPort.markedIds).isEmpty();
    }

    @Test
    void shouldOnlyEnqueueEnabledIndexTasks() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        gcPort.candidates.add(new MemoryGarbageCollectionCandidate(
                "m-obsolete", "user-1", "tenant-1", "long_term", "OBSOLETE", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(20, Duration.ofDays(7), false, true, false, false));

        MemoryGarbageCollectionResult result = service.run("vector-only");

        assertThat(result.enqueuedDeleteTaskCount()).isEqualTo(1);
        assertThat(outboxPort.tasks)
                .extracting(MemoryOutboxPort.MemoryOutboxTask::taskType)
                .containsExactly(MemoryOutboxTaskTypes.VECTOR_DELETE);
        assertThat(gcPort.markedIds).containsExactly("m-obsolete");
    }

    @Test
    void shouldArchiveColdLifecycleCandidatesAndRemoveDerivedIndexes() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        gcPort.archiveCandidates.add(new MemoryGarbageCollectionCandidate(
                "m-cold", "user-1", "tenant-1", "long_term", "ACTIVE", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(
                        20,
                        Duration.ofDays(7),
                        false,
                        true,
                        true,
                        false,
                        true,
                        Duration.ofDays(90),
                        0.15D));

        MemoryGarbageCollectionResult result = service.run("generational-gc");

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.archivedCount()).isEqualTo(1);
        assertThat(result.enqueuedDeleteTaskCount()).isEqualTo(2);
        assertThat(result.markedIndexDeletedCount()).isEqualTo(1);
        assertThat(gcPort.archivedIds).containsExactly("m-cold");
        assertThat(gcPort.markedIds).containsExactly("m-cold");
        assertThat(outboxPort.tasks)
                .extracting(MemoryOutboxPort.MemoryOutboxTask::taskType)
                .containsExactly(
                        MemoryOutboxTaskTypes.VECTOR_DELETE,
                        MemoryOutboxTaskTypes.KEYWORD_DELETE);
    }

    @Test
    void shouldPhysicallyDeleteCandidatesAfterDerivedIndexesAreRemoved() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        gcPort.physicalDeleteCandidates.add(new MemoryGarbageCollectionCandidate(
                "m-purge", "user-1", "tenant-1", "semantic", "ARCHIVED", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(
                        20,
                        Duration.ofDays(7),
                        false,
                        true,
                        true,
                        true,
                        false,
                        Duration.ofDays(90),
                        0.15D,
                        true,
                        Duration.ofDays(30)));

        MemoryGarbageCollectionResult result = service.run("hard-gc");

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.physicallyDeletedCount()).isEqualTo(1);
        assertThat(result.enqueuedDeleteTaskCount()).isZero();
        assertThat(result.markedIndexDeletedCount()).isZero();
        assertThat(gcPort.physicallyDeletedIds).containsExactly("m-purge");
    }

    @Test
    void shouldReportCandidateCountsForEachGarbageCollectionStage() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        gcPort.candidates.add(new MemoryGarbageCollectionCandidate(
                "m-index", "user-1", "tenant-1", "short_term", "OBSOLETE", Instant.now()));
        gcPort.archiveCandidates.add(new MemoryGarbageCollectionCandidate(
                "m-archive", "user-1", "tenant-1", "long_term", "ACTIVE", Instant.now()));
        gcPort.physicalDeleteCandidates.add(new MemoryGarbageCollectionCandidate(
                "m-delete", "user-1", "tenant-1", "semantic", "ARCHIVED", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(
                        20,
                        Duration.ofDays(7),
                        true,
                        true,
                        true,
                        true,
                        true,
                        Duration.ofDays(90),
                        0.15D,
                        true,
                        Duration.ofDays(30)));

        MemoryGarbageCollectionResult result = service.run("gc-observability");

        assertThat(result.scannedCount()).isEqualTo(3);
        assertThat(result.derivedIndexCandidateCount()).isEqualTo(1);
        assertThat(result.archiveCandidateCount()).isEqualTo(1);
        assertThat(result.physicalDeleteCandidateCount()).isEqualTo(1);
    }

    @Test
    void shouldContinueOtherIndexTasksWhenOneIndexTaskCannotBeQueued() {
        RecordingGarbageCollectionPort gcPort = new RecordingGarbageCollectionPort();
        RecordingOutboxPort outboxPort = new RecordingOutboxPort();
        outboxPort.failTaskTypes.add(MemoryOutboxTaskTypes.KEYWORD_DELETE);
        gcPort.candidates.add(new MemoryGarbageCollectionCandidate(
                "m-obsolete", "user-1", "tenant-1", "long_term", "OBSOLETE", Instant.now()));
        MemoryGarbageCollectionService service = new MemoryGarbageCollectionService(
                gcPort,
                outboxPort,
                new MemoryGarbageCollectionOptions(20, Duration.ofDays(7), false, true, true, true));

        MemoryGarbageCollectionResult result = service.run("partial-failure");

        assertThat(result.enqueuedDeleteTaskCount()).isEqualTo(2);
        assertThat(result.markedIndexDeletedCount()).isZero();
        assertThat(result.errors())
                .containsExactly("m-obsolete:KEYWORD_DELETE:outbox down");
        assertThat(outboxPort.tasks)
                .extracting(MemoryOutboxPort.MemoryOutboxTask::taskType)
                .containsExactly(
                        MemoryOutboxTaskTypes.VECTOR_DELETE,
                        MemoryOutboxTaskTypes.GRAPH_DELETE);
        assertThat(gcPort.markedIds).isEmpty();
    }

    private static class RecordingGarbageCollectionPort implements MemoryGarbageCollectionPort {

        final List<MemoryGarbageCollectionCandidate> candidates = new ArrayList<>();
        final List<MemoryGarbageCollectionCandidate> archiveCandidates = new ArrayList<>();
        final List<MemoryGarbageCollectionCandidate> physicalDeleteCandidates = new ArrayList<>();
        final List<String> markedIds = new ArrayList<>();
        final List<String> archivedIds = new ArrayList<>();
        final List<String> physicallyDeletedIds = new ArrayList<>();

        @Override
        public List<MemoryGarbageCollectionCandidate> scanDerivedIndexDeleteCandidates(
                Instant now,
                Duration retention,
                int limit) {
            return candidates.stream().limit(limit).toList();
        }

        @Override
        public List<MemoryGarbageCollectionCandidate> scanLifecycleArchiveCandidates(
                Instant now,
                Duration idleRetention,
                double scoreThreshold,
                int limit) {
            return archiveCandidates.stream().limit(limit).toList();
        }

        @Override
        public int markArchived(List<String> memoryIds, Instant archivedAt, String reason) {
            archivedIds.addAll(memoryIds);
            return memoryIds.size();
        }

        @Override
        public List<MemoryGarbageCollectionCandidate> scanPhysicalDeleteCandidates(
                Instant now,
                Duration retention,
                int limit) {
            return physicalDeleteCandidates.stream().limit(limit).toList();
        }

        @Override
        public int markPhysicallyDeleted(List<String> memoryIds, Instant deletedAt) {
            physicallyDeletedIds.addAll(memoryIds);
            return memoryIds.size();
        }

        @Override
        public int markDerivedIndexesDeleted(List<String> memoryIds, Instant markedAt) {
            markedIds.addAll(memoryIds);
            return memoryIds.size();
        }
    }

    private static class RecordingOutboxPort implements MemoryOutboxPort {

        final List<MemoryOutboxTask> tasks = new ArrayList<>();
        final List<String> failTaskTypes = new ArrayList<>();

        @Override
        public void enqueue(MemoryOutboxTask task) {
            if (failTaskTypes.contains(task.taskType())) {
                throw new RuntimeException("outbox down");
            }
            tasks.add(task);
        }
    }
}
