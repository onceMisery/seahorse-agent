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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceRunResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceTaskOutcome;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolutionRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DefaultMemoryMaintenanceServiceTests {

    @Test
    void shouldRunGarbageCollectionWhenRequestedAndEnabled() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                false,
                false,
                true);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                false,
                false,
                true));

        assertThat(garbageCollectionService.reasons).containsExactly("manual-maintenance");
        assertThat(result.reason()).isEqualTo("manual-maintenance");
        assertThat(result.garbageCollectionResult()).isNotNull();
        assertThat(result.garbageCollectionResult().reason()).isEqualTo("manual-maintenance");
        assertThat(result.skippedTasks()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldSkipUnavailableMaintenanceTracksWithoutRunningGarbageCollectionWhenDisabled() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                false,
                false,
                false);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                true,
                true,
                true));

        assertThat(garbageCollectionService.reasons).isEmpty();
        assertThat(result.garbageCollectionResult()).isNull();
        assertThat(result.skippedTasks()).containsExactly(
                MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE,
                MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE,
                MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED);
        assertThat(result.aliasResolutionResult()).isNull();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldRunAliasResolutionWhenRequestedAndEnabled() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        RecordingAliasResolutionService aliasResolutionService = new RecordingAliasResolutionService();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                null,
                aliasResolutionService,
                MemoryMaintenanceRunRepositoryPort.noop(),
                false,
                true,
                false);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                false,
                true,
                false));

        assertThat(aliasResolutionService.reasons).containsExactly("manual-maintenance");
        assertThat(result.aliasResolutionResult()).isNotNull();
        assertThat(result.aliasResolutionResult().reason()).isEqualTo("manual-maintenance");
        assertThat(result.aliasResolutionResult().scannedCount()).isEqualTo(2);
        assertThat(result.aliasResolutionResult().normalizedCount()).isEqualTo(1);
        assertThat(result.aliasResolutionResult().dictionaryMatchCount()).isEqualTo(0);
        assertThat(result.aliasResolutionResult().skippedCount()).isEqualTo(0);
        assertThat(result.skippedTasks()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldPersistAliasResolutionCountsInMaintenanceRunRecord() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        RecordingAliasResolutionService aliasResolutionService = new RecordingAliasResolutionService();
        RecordingMaintenanceRunRepository repository = new RecordingMaintenanceRunRepository();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                null,
                aliasResolutionService,
                repository,
                false,
                true,
                false);

        service.runMaintenance(new MemoryMaintenanceRunCommand(
                "scheduled-alias-maintenance",
                false,
                true,
                false));

        assertThat(repository.records).hasSize(1);
        MemoryMaintenanceRunRecord record = repository.records.get(0);
        assertThat(record.aliasScannedCount()).isEqualTo(2);
        assertThat(record.aliasNormalizedCount()).isEqualTo(1);
        assertThat(record.aliasDictionaryMatchCount()).isEqualTo(0);
        assertThat(record.aliasSkippedCount()).isEqualTo(0);
    }

    @Test
    void shouldSkipAliasResolutionCleanlyWhenUnavailable() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                false,
                true,
                false);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                false,
                true,
                false));

        assertThat(result.aliasResolutionResult()).isNull();
        assertThat(result.skippedTasks()).containsExactly(MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldPersistMaintenanceRunRecordWithSkippedTasks() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        RecordingMaintenanceRunRepository repository = new RecordingMaintenanceRunRepository();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                repository,
                false,
                false,
                false);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                true,
                true,
                true));

        assertThat(result.skippedTasks()).containsExactly(
                MemoryMaintenanceRunResult.SKIP_COMPACTION_UNAVAILABLE,
                MemoryMaintenanceRunResult.SKIP_ALIAS_UNAVAILABLE,
                MemoryMaintenanceRunResult.SKIP_GARBAGE_COLLECTION_DISABLED);
        assertThat(repository.records).hasSize(1);
        MemoryMaintenanceRunRecord record = repository.records.get(0);
        assertThat(record.runId()).isNotBlank();
        assertThat(record.reason()).isEqualTo("manual-maintenance");
        assertThat(record.status()).isEqualTo(MemoryMaintenanceRunRecord.STATUS_SUCCEEDED_WITH_WARNINGS);
        assertThat(record.compactionRequested()).isTrue();
        assertThat(record.aliasRequested()).isTrue();
        assertThat(record.garbageCollectionRequested()).isTrue();
        assertThat(record.skippedTasks()).containsExactlyElementsOf(result.skippedTasks());
        assertThat(record.errors()).isEmpty();
    }

    @Test
    void shouldRunCompactionWhenRequestedAndEnabled() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        RecordingCompactionService compactionService = new RecordingCompactionService();
        RecordingMaintenanceRunRepository repository = new RecordingMaintenanceRunRepository();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                compactionService,
                repository,
                true,
                false,
                false);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                true,
                false,
                false));

        assertThat(compactionService.reasons).containsExactly("manual-maintenance");
        assertThat(result.compactionResult()).isNotNull();
        assertThat(result.compactionResult().compactedGroupCount()).isEqualTo(1);
        assertThat(result.skippedTasks()).isEmpty();
        assertThat(repository.records).hasSize(1);
        MemoryMaintenanceRunRecord record = repository.records.get(0);
        assertThat(record.status()).isEqualTo(MemoryMaintenanceRunRecord.STATUS_SUCCEEDED);
        assertThat(record.compactionScannedCount()).isEqualTo(1);
        assertThat(record.compactionGroupCount()).isEqualTo(1);
        assertThat(record.compactionFragmentCount()).isEqualTo(2);
    }

    @Test
    void shouldRecordTraceEventForMaintenanceRun() {
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                null,
                null,
                MemoryMaintenanceRunRepositoryPort.noop(),
                traceRecorder,
                false,
                false,
                true);

        service.runMaintenance(new MemoryMaintenanceRunCommand(
                "manual-maintenance",
                false,
                false,
                true));

        assertThat(traceRecorder.events).hasSize(1);
        assertThat(traceRecorder.events.get(0).component()).isEqualTo("memory-maintenance");
        assertThat(traceRecorder.events.get(0).eventType()).isEqualTo("run-maintenance");
        assertThat(traceRecorder.events.get(0).status()).isEqualTo(MemoryTraceEvent.STATUS_SUCCESS);
        assertThat(traceRecorder.events.get(0).details())
                .containsEntry("gcDerivedIndexCandidateCount", 1)
                .containsEntry("gcArchiveCandidateCount", 0)
                .containsEntry("gcPhysicalDeleteCandidateCount", 0);
    }

    @Test
    void shouldContinueMaintenanceAndExposeTaskOutcomesWhenOneTrackFails() {
        ThrowingCompactionService compactionService = new ThrowingCompactionService();
        RecordingAliasResolutionService aliasResolutionService = new RecordingAliasResolutionService();
        RecordingGarbageCollectionService garbageCollectionService = new RecordingGarbageCollectionService();
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        DefaultMemoryMaintenanceService service = new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                compactionService,
                aliasResolutionService,
                MemoryMaintenanceRunRepositoryPort.noop(),
                traceRecorder,
                true,
                true,
                true);

        MemoryMaintenanceRunResult result = service.runMaintenance(new MemoryMaintenanceRunCommand(
                "scheduled-maintenance",
                true,
                true,
                true));

        assertThat(compactionService.reasons).containsExactly("scheduled-maintenance");
        assertThat(aliasResolutionService.reasons).containsExactly("scheduled-maintenance");
        assertThat(garbageCollectionService.reasons).containsExactly("scheduled-maintenance");
        assertThat(result.errors()).containsExactly("compaction:compaction boom");
        assertThat(result.taskOutcomes())
                .extracting(MemoryMaintenanceTaskOutcome::task, MemoryMaintenanceTaskOutcome::status)
                .containsExactly(
                        tuple(MemoryMaintenanceTaskOutcome.TASK_COMPACTION, MemoryMaintenanceTaskOutcome.STATUS_FAILED),
                        tuple(MemoryMaintenanceTaskOutcome.TASK_ALIAS, MemoryMaintenanceTaskOutcome.STATUS_SUCCEEDED),
                        tuple(MemoryMaintenanceTaskOutcome.TASK_GARBAGE_COLLECTION,
                                MemoryMaintenanceTaskOutcome.STATUS_SUCCEEDED));
        assertThat(traceRecorder.events).hasSize(1);
        assertThat(traceRecorder.events.get(0).status()).isEqualTo(MemoryTraceEvent.STATUS_FAILED);
        assertThat(traceRecorder.events.get(0).details().get("taskOutcomes")).isEqualTo(result.taskOutcomes());
    }

    private static class RecordingGarbageCollectionService extends MemoryGarbageCollectionService {

        private final List<String> reasons = new ArrayList<>();

        private RecordingGarbageCollectionService() {
            super(MemoryGarbageCollectionPort.noop(),
                    MemoryOutboxPort.noop(),
                    MemoryGarbageCollectionOptions.vectorOnly());
        }

        @Override
        public MemoryGarbageCollectionResult run(String reason) {
            reasons.add(reason);
            return new MemoryGarbageCollectionResult(
                    reason,
                    1,
                    1,
                    1,
                    false,
                    List.of(),
                    Instant.EPOCH);
        }
    }

    private static class RecordingCompactionService extends MemoryCompactionService {

        private final List<String> reasons = new ArrayList<>();

        private RecordingCompactionService() {
            super();
        }

        @Override
        public MemoryCompactionResult run(String reason) {
            reasons.add(reason);
            return new MemoryCompactionResult(reason, 1, 1, 2, List.of(), Instant.EPOCH);
        }
    }

    private static class ThrowingCompactionService extends MemoryCompactionService {

        private final List<String> reasons = new ArrayList<>();

        private ThrowingCompactionService() {
            super();
        }

        @Override
        public MemoryCompactionResult run(String reason) {
            reasons.add(reason);
            throw new IllegalStateException("compaction boom");
        }
    }

    private static class RecordingAliasResolutionService extends MemoryAliasResolutionService {

        private final List<String> reasons = new ArrayList<>();

        private RecordingAliasResolutionService() {
            super(MemoryAliasPort.noop(), MemoryAliasResolutionOptions.defaults());
        }

        @Override
        public MemoryAliasResolutionRunResult run(String reason) {
            reasons.add(reason);
            return new MemoryAliasResolutionRunResult(reason, 2, 1, 0, 0, List.of(), Instant.EPOCH);
        }
    }

    private static class RecordingTraceRecorder implements MemoryTraceRecorder {

        private final List<MemoryTraceEvent> events = new ArrayList<>();

        @Override
        public void record(MemoryTraceEvent event) {
            events.add(event);
        }

        @Override
        public List<MemoryTraceEvent> listRecent(int limit) {
            return List.copyOf(events);
        }
    }

    private static class RecordingMaintenanceRunRepository implements MemoryMaintenanceRunRepositoryPort {

        private final List<MemoryMaintenanceRunRecord> records = new ArrayList<>();

        @Override
        public void save(MemoryMaintenanceRunRecord record) {
            records.add(record);
        }

        @Override
        public MemoryMaintenanceRunPage pageMaintenanceRuns(MemoryMaintenanceRunQuery query) {
            return new MemoryMaintenanceRunPage(records, records.size(), query.size(), query.current(), 1);
        }
    }
}
