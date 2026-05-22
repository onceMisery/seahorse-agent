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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMemoryObservabilityServiceTests {

    @Test
    void shouldAggregateHealthReportFromMemoryManagementSources() {
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        profilePort.facts.add(new ProfileFact("pf-1", "user-1", "default", "identity.occupation", "学生",
                0.95D, "explicit", "gen-1", "ACTIVE", Instant.EPOCH));
        RecordingCorrectionLedgerPort correctionPort = new RecordingCorrectionLedgerPort();
        correctionPort.rules.add(new CorrectionRule("cr-1", "user-1", "default", "PROFILE_CORRECTION",
                "PROFILE_SLOT", "identity.occupation", "teacher", "student",
                "occupation correction", "HARD_RULE", "gen-1", "ACTIVE", Instant.EPOCH));
        RecordingOperationLogPort operationLog = new RecordingOperationLogPort();
        operationLog.records.add(operation("op-1", "SUCCEEDED", Map.of("status", "ACCEPTED")));
        operationLog.records.add(operation("op-2", "REJECTED", Map.of("status", "REJECTED", "reason", "high_risk")));
        operationLog.records.add(operation("op-3", "FAILED", Map.of("status", "FAILED", "reason", "schema_error")));
        RecordingConflictLogRepository conflicts = new RecordingConflictLogRepository();
        conflicts.records.add(new MemoryConflictRecord("conflict-1", "user-1", "m1", "m2",
                "SEMANTIC_KEY_CONFLICT", "MEDIUM", "PENDING", "", "", Instant.EPOCH, Instant.EPOCH));
        RecordingMemoryOutboxPort outbox = new RecordingMemoryOutboxPort();
        outbox.tasks.add(new MemoryOutboxPort.MemoryOutboxTask("outbox-1", "VECTOR_UPSERT", "m1",
                "user-1", "default", Map.of(), "vector down", null, Instant.EPOCH));
        RecordingQualitySnapshotRepository snapshots = new RecordingQualitySnapshotRepository();
        snapshots.snapshots.add(new MemoryQualitySnapshot("snapshot-1", "user-1",
                Map.of("shortTermCount", 3, "longTermCount", 2, "semanticCount", 1, "conflictCount", 1),
                Instant.EPOCH));
        RecordingReviewRepository reviews = new RecordingReviewRepository();
        reviews.records.add(review("review-1", "user-1", "default", MemoryReviewStatus.PENDING));
        reviews.records.add(review("review-2", "user-1", "default", MemoryReviewStatus.PENDING));
        reviews.records.add(review("review-3", "user-1", "default", MemoryReviewStatus.REJECTED));
        RecordingPolicyConfigPort policyConfigPort = new RecordingPolicyConfigPort(MemoryPolicyConfig.defaults());
        KernelMemoryManagementService service = service(profilePort, correctionPort, operationLog,
                conflicts, outbox, snapshots, reviews, policyConfigPort);

        var report = service.memoryHealth("user-1", "default");

        assertThat(report.userId()).isEqualTo("user-1");
        assertThat(report.tenantId()).isEqualTo("default");
        assertThat(report.profileFactCount()).isEqualTo(1);
        assertThat(report.correctionRuleCount()).isEqualTo(1);
        assertThat(report.pendingConflictCount()).isEqualTo(1);
        assertThat(report.outboxBacklogCount()).isEqualTo(1);
        assertThat(report.pendingReviewCount()).isEqualTo(2);
        assertThat(report.operationCounts())
                .containsEntry("SUCCEEDED", 1L)
                .containsEntry("REJECTED", 1L)
                .containsEntry("FAILED", 1L);
        assertThat(report.acceptRate()).isEqualTo(1D / 3D);
        assertThat(report.rejectRate()).isEqualTo(1D / 3D);
        assertThat(report.schemaFailureCount()).isEqualTo(1);
        assertThat(report.policyBlockCount()).isEqualTo(1);
        assertThat(report.profileCompleteness()).isEqualTo(0.25D);
        assertThat(report.conflictDensity()).isEqualTo(1D / 6D);
        assertThat(report.traceEventCount()).isZero();
        assertThat(report.traceFailureCount()).isZero();
        assertThat(report.traceComponentCounts()).isEmpty();
        assertThat(report.alerts())
                .contains("memory.outbox.backlog", "memory.schema.failures", "memory.profile.low-completeness");
    }

    @Test
    void shouldAggregateTraceSummaryFromTraceRecorder() {
        InMemoryTraceRecorder traceRecorder = new InMemoryTraceRecorder();
        traceRecorder.record(new MemoryTraceEvent(
                "trace-1",
                "default",
                "user-1",
                "conversation-1",
                "session-1",
                "memory-aggregation",
                "flush-ready",
                MemoryTraceEvent.STATUS_SUCCESS,
                "snapshot-1",
                "snapshot",
                Map.of("trigger", "FORCE_TURNS"),
                Instant.EPOCH));
        traceRecorder.record(new MemoryTraceEvent(
                "trace-2",
                "default",
                "user-1",
                "conversation-1",
                "session-1",
                "memory-maintenance",
                "run-maintenance",
                MemoryTraceEvent.STATUS_FAILED,
                "manual-maintenance",
                "run",
                Map.of("reason", "manual-maintenance"),
                Instant.EPOCH));

        KernelMemoryManagementService service = service(
                new RecordingProfileMemoryPort(),
                new RecordingCorrectionLedgerPort(),
                new RecordingOperationLogPort(),
                new RecordingConflictLogRepository(),
                new RecordingMemoryOutboxPort(),
                new RecordingQualitySnapshotRepository(),
                MemoryReviewManagementRepositoryPort.empty(),
                new RecordingPolicyConfigPort(MemoryPolicyConfig.defaults()),
                traceRecorder);

        var report = service.memoryHealth("user-1", "default");

        assertThat(report.traceEventCount()).isEqualTo(2);
        assertThat(report.traceFailureCount()).isEqualTo(1);
        assertThat(report.traceComponentCounts())
                .containsEntry("memory-aggregation", 1L)
                .containsEntry("memory-maintenance", 1L);
    }

    @Test
    void shouldExposeAndUpdateRuntimePolicyConfig() {
        RecordingPolicyConfigPort policyConfigPort = new RecordingPolicyConfigPort(MemoryPolicyConfig.defaults());
        KernelMemoryManagementService service = service(
                new RecordingProfileMemoryPort(),
                new RecordingCorrectionLedgerPort(),
                new RecordingOperationLogPort(),
                new RecordingConflictLogRepository(),
                new RecordingMemoryOutboxPort(),
                new RecordingQualitySnapshotRepository(),
                MemoryReviewManagementRepositoryPort.empty(),
                policyConfigPort);

        MemoryPolicyConfig updated = service.updatePolicyConfig(MemoryPolicyConfig.defaults()
                .withCaptureAcceptThreshold(0.55D)
                .withTokenBudget(1800)
                .withReviewEnabled(true)
                .withTrackEnabled("business_doc", false)
                .withGreyReleaseKey("tenant-default"));

        assertThat(updated.captureAcceptThreshold()).isEqualTo(0.55D);
        assertThat(updated.tokenBudget()).isEqualTo(1800);
        assertThat(updated.reviewEnabled()).isTrue();
        assertThat(updated.enabledTracks()).containsEntry("business_doc", false);
        assertThat(service.memoryPolicyConfig()).isEqualTo(updated);
    }

    private KernelMemoryManagementService service(RecordingProfileMemoryPort profilePort,
                                                  RecordingCorrectionLedgerPort correctionPort,
                                                  RecordingOperationLogPort operationLog,
                                                  RecordingConflictLogRepository conflicts,
                                                  RecordingMemoryOutboxPort outbox,
                                                  RecordingQualitySnapshotRepository snapshots,
                                                  MemoryReviewManagementRepositoryPort reviewRepository,
                                                  RecordingPolicyConfigPort policyConfigPort) {
        return service(profilePort, correctionPort, operationLog, conflicts, outbox, snapshots, reviewRepository,
                policyConfigPort, MemoryTraceRecorder.noop());
    }

    private KernelMemoryManagementService service(RecordingProfileMemoryPort profilePort,
                                                  RecordingCorrectionLedgerPort correctionPort,
                                                  RecordingOperationLogPort operationLog,
                                                  RecordingConflictLogRepository conflicts,
                                                  RecordingMemoryOutboxPort outbox,
                                                  RecordingQualitySnapshotRepository snapshots,
                                                  MemoryReviewManagementRepositoryPort reviewRepository,
                                                  RecordingPolicyConfigPort policyConfigPort,
                                                  MemoryTraceRecorder traceRecorder) {
        return new KernelMemoryManagementService(new MemoryManagementServicePorts(
                new RecordingMemoryPort(),
                new RecordingShortTermMemoryPort(),
                new RecordingLongTermMemoryPort(),
                new RecordingSemanticMemoryPort(),
                snapshots,
                conflicts,
                profilePort,
                correctionPort,
                operationLog,
                outbox,
                reviewRepository,
                policyConfigPort,
                traceRecorder));
    }

    private MemoryOperationRecord operation(String operationId, String status, Map<String, Object> decision) {
        return new MemoryOperationRecord(operationId, "user-1", "default", "ADD", "SHORT_TERM_MEMORY",
                "", Map.of(), decision, status, "policy-v1", "", Instant.EPOCH, Instant.EPOCH);
    }

    private MemoryReviewRecord review(String id, String userId, String tenantId, MemoryReviewStatus status) {
        return new MemoryReviewRecord(
                id,
                "op-" + id,
                tenantId,
                userId,
                "conv-1",
                "msg-" + id,
                "REVIEW",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.state",
                "candidate " + id,
                0.8D,
                0.7D,
                0.7D,
                0.2D,
                "needs review",
                List.of("msg-" + id),
                Map.of(),
                status,
                "",
                "",
                "",
                Map.of(),
                "",
                "",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static class RecordingMemoryPort implements WorkingMemoryPort {

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return List.of();
        }

        @Override
        public void save(MemoryRecord record) {
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class RecordingShortTermMemoryPort extends RecordingMemoryPort implements ShortTermMemoryPort {
    }

    private static class RecordingLongTermMemoryPort extends RecordingMemoryPort implements LongTermMemoryPort {
    }

    private static class RecordingSemanticMemoryPort extends RecordingMemoryPort implements SemanticMemoryPort {
    }

    private static class RecordingProfileMemoryPort implements ProfileMemoryPort {

        final List<ProfileFact> facts = new ArrayList<>();

        @Override
        public Optional<ProfileFact> findActive(String userId, String tenantId, String slotKey) {
            return facts.stream()
                    .filter(fact -> userId.equals(fact.userId()))
                    .filter(fact -> tenantId.equals(fact.tenantId()))
                    .filter(fact -> slotKey.equals(fact.slotKey()))
                    .findFirst();
        }

        @Override
        public List<ProfileFact> listActive(String userId, String tenantId, int limit) {
            return facts.stream()
                    .filter(fact -> userId.equals(fact.userId()))
                    .filter(fact -> tenantId.equals(fact.tenantId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void upsert(ProfileFactUpdate update) {
        }
    }

    private static class RecordingCorrectionLedgerPort implements CorrectionLedgerPort {

        final List<CorrectionRule> rules = new ArrayList<>();

        @Override
        public List<CorrectionRule> listActive(String userId, String tenantId, int limit) {
            return rules.stream()
                    .filter(rule -> userId.equals(rule.userId()))
                    .filter(rule -> tenantId.equals(rule.tenantId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void upsert(CorrectionCommand command) {
        }
    }

    private static class RecordingOperationLogPort implements MemoryOperationLogPort {

        final List<MemoryOperationRecord> records = new ArrayList<>();

        @Override
        public boolean tryStart(MemoryOperation operation) {
            return true;
        }

        @Override
        public void markCompleted(String operationId, MemoryOperationStatus status, Map<String, Object> decision) {
        }

        @Override
        public void markFailed(String operationId, String errorMessage) {
        }

        @Override
        public List<MemoryOperationRecord> listByUser(String userId, String tenantId, String status, int limit) {
            return records.stream()
                    .filter(record -> userId.equals(record.userId()))
                    .filter(record -> tenantId.equals(record.tenantId()))
                    .filter(record -> status == null || status.isBlank() || status.equals(record.status()))
                    .limit(limit)
                    .toList();
        }
    }

    private static class RecordingConflictLogRepository implements MemoryConflictLogRepositoryPort {

        final List<MemoryConflictRecord> records = new ArrayList<>();

        @Override
        public List<MemoryConflictRecord> listByUser(String userId, String status, int limit) {
            return records.stream()
                    .filter(record -> userId.equals(record.userId()))
                    .filter(record -> status == null || status.isBlank() || status.equals(record.resolutionStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MemoryConflictRecord record) {
        }

        @Override
        public boolean resolve(String conflictId, String action, String resolvedBy) {
            return false;
        }
    }

    private static class RecordingMemoryOutboxPort implements MemoryOutboxPort {

        final List<MemoryOutboxTask> tasks = new ArrayList<>();

        @Override
        public void enqueue(MemoryOutboxTask task) {
            tasks.add(task);
        }

        @Override
        public List<MemoryOutboxTask> pollPending(int limit) {
            return tasks.stream().limit(limit).toList();
        }
    }

    private static class RecordingQualitySnapshotRepository implements MemoryQualitySnapshotRepositoryPort {

        final List<MemoryQualitySnapshot> snapshots = new ArrayList<>();

        @Override
        public List<MemoryQualitySnapshot> listByUser(String userId, int limit) {
            return snapshots.stream()
                    .filter(snapshot -> userId.equals(snapshot.userId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MemoryQualitySnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }

    private static class RecordingReviewRepository implements MemoryReviewManagementRepositoryPort {

        final List<MemoryReviewRecord> records = new ArrayList<>();

        @Override
        public void save(MemoryReviewCandidate candidate) {
            records.add(MemoryReviewRecord.pending(candidate));
        }

        @Override
        public MemoryReviewPage pageReviewCandidates(MemoryReviewQuery query) {
            List<MemoryReviewRecord> pageRecords = records.stream()
                    .filter(record -> query.tenantId().equals(record.tenantId()))
                    .filter(record -> query.userId().isBlank() || query.userId().equals(record.userId()))
                    .filter(record -> query.reviewStatus() == null || query.reviewStatus() == record.reviewStatus())
                    .skip(query.offset())
                    .limit(query.size())
                    .toList();
            long total = records.stream()
                    .filter(record -> query.tenantId().equals(record.tenantId()))
                    .filter(record -> query.userId().isBlank() || query.userId().equals(record.userId()))
                    .filter(record -> query.reviewStatus() == null || query.reviewStatus() == record.reviewStatus())
                    .count();
            long pages = total == 0L ? 0L : (total + query.size() - 1L) / query.size();
            return new MemoryReviewPage(pageRecords, total, query.size(), query.current(), pages);
        }

        @Override
        public Optional<MemoryReviewRecord> findReviewItem(String candidateId) {
            return records.stream()
                    .filter(record -> candidateId.equals(record.candidateId()))
                    .findFirst();
        }

        @Override
        public MemoryReviewRecord applyReviewDecision(MemoryReviewDecision decision) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static class RecordingPolicyConfigPort implements MemoryPolicyConfigPort {

        private MemoryPolicyConfig current;

        private RecordingPolicyConfigPort(MemoryPolicyConfig initialConfig) {
            this.current = initialConfig;
        }

        @Override
        public MemoryPolicyConfig current() {
            return current;
        }

        @Override
        public MemoryPolicyConfig update(MemoryPolicyConfig config) {
            current = config;
            return current;
        }
    }

    private static final class InMemoryTraceRecorder implements MemoryTraceRecorder {

        private final List<MemoryTraceEvent> events = new ArrayList<>();

        @Override
        public void record(MemoryTraceEvent event) {
            events.add(event);
        }

        @Override
        public List<MemoryTraceEvent> listRecent(int limit) {
            int safeLimit = limit <= 0 ? events.size() : Math.min(limit, events.size());
            return events.stream()
                    .skip(Math.max(0, events.size() - safeLimit))
                    .toList();
        }
    }
}
