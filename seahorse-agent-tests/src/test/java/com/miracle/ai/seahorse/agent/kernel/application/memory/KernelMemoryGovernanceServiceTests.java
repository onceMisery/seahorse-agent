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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryMaintenancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMemoryGovernanceServiceTests {

    @Test
    void shouldPromoteImportantShortTermMemoryAndUpsertSemanticProfile() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort();
        RecordingMemoryPort longTerm = new RecordingMemoryPort();
        RecordingMemoryPort semantic = new RecordingMemoryPort();
        RecordingMemoryEnginePort memoryEngine = new RecordingMemoryEnginePort();
        shortTerm.records.add(new MemoryRecord("m1", "short_term", "PROFILE", "Name is Alice",
                Map.of("userId", "user-1", "importanceScore", 0.55D, "confidenceLevel", 0.9D),
                Instant.now()));

        KernelMemoryGovernanceService service = new KernelMemoryGovernanceService(
                new MemoryGovernanceServicePorts(shortTerm, longTerm, semantic, memoryEngine), 0.6D);

        MemoryGovernanceRunResult result = service.runGovernance("user-1", "manual", true);

        assertThat(result.promotedCount()).isEqualTo(1);
        assertThat(result.semanticUpsertCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(longTerm.records).hasSize(1);
        assertThat(semantic.records).hasSize(1);
        assertThat(memoryEngine.assessedUsers).containsExactly("user-1");
    }

    @Test
    void shouldPersistQualitySnapshotAndPendingConflictsDuringGovernance() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort();
        RecordingMemoryPort longTerm = new RecordingMemoryPort();
        RecordingMemoryPort semantic = new RecordingMemoryPort();
        RecordingMemoryEnginePort memoryEngine = new RecordingMemoryEnginePort();
        RecordingQualitySnapshotRepository qualitySnapshots = new RecordingQualitySnapshotRepository();
        RecordingConflictLogRepository conflicts = new RecordingConflictLogRepository();
        shortTerm.records.add(new MemoryRecord("m1", "short_term", "PROFILE", "I am a student",
                Map.of("userId", "user-1", "semanticKey", "profile:occupation"),
                Instant.now()));
        shortTerm.records.add(new MemoryRecord("m2", "short_term", "PROFILE", "I am a teacher",
                Map.of("userId", "user-1", "semanticKey", "profile:occupation"),
                Instant.now()));
        memoryEngine.report = MemoryQualityReport.builder()
                .userId("user-1")
                .shortTermCount(2)
                .conflictCount(1)
                .singularProfileConflictCount(1)
                .build();
        KernelMemoryGovernanceService service = new KernelMemoryGovernanceService(
                new MemoryGovernanceServicePorts(shortTerm, longTerm, semantic, memoryEngine,
                        com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort.noop(),
                        ShortTermMemoryMaintenancePort.noop(),
                        qualitySnapshots,
                        conflicts),
                0.6D);

        MemoryGovernanceRunResult result = service.runGovernance("user-1", "quality-check", true);

        assertThat(result.errors()).isEmpty();
        assertThat(qualitySnapshots.snapshots).hasSize(1);
        assertThat(qualitySnapshots.snapshots.get(0).snapshot())
                .containsEntry("governancePolicyVersion", "memory-governance-v1")
                .containsEntry("shortTermCount", 2)
                .containsEntry("singularProfileConflictCount", 1);
        assertThat(conflicts.records).hasSize(1);
        assertThat(conflicts.records.get(0).userId()).isEqualTo("user-1");
        assertThat(conflicts.records.get(0).memoryId1()).isEqualTo("m1");
        assertThat(conflicts.records.get(0).memoryId2()).isEqualTo("m2");
        assertThat(conflicts.records.get(0).conflictType()).isEqualTo("SEMANTIC_KEY_CONFLICT");
        assertThat(conflicts.records.get(0).resolutionStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldCleanExpiredOrDecayedShortTermMemories() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort();
        RecordingMemoryPort longTerm = new RecordingMemoryPort();
        RecordingMemoryPort semantic = new RecordingMemoryPort();
        RecordingMemoryEnginePort memoryEngine = new RecordingMemoryEnginePort();
        RecordingShortTermMaintenancePort maintenance = new RecordingShortTermMaintenancePort();
        maintenance.candidates.add(new MemoryRecord("expired-1", "short_term", "FACT", "old",
                Map.of("userId", "user-1"), Instant.now()));
        KernelMemoryGovernanceService service = new KernelMemoryGovernanceService(
                new MemoryGovernanceServicePorts(shortTerm, longTerm, semantic, memoryEngine,
                        com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort.noop(), maintenance),
                0.6D);

        MemoryGovernanceRunResult result = service.runDecay("manual-decay");

        assertThat(result.decayExecuted()).isTrue();
        assertThat(memoryEngine.decayExecuted).isFalse();
        assertThat(maintenance.deletedIds).containsExactly("expired-1");
    }

    @Test
    void shouldNotDeleteWhenDecayDryRunEnabled() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort();
        RecordingMemoryPort longTerm = new RecordingMemoryPort();
        RecordingMemoryPort semantic = new RecordingMemoryPort();
        RecordingMemoryEnginePort memoryEngine = new RecordingMemoryEnginePort();
        RecordingShortTermMaintenancePort maintenance = new RecordingShortTermMaintenancePort();
        maintenance.candidates.add(new MemoryRecord("expired-1", "short_term", "FACT", "old",
                Map.of("userId", "user-1"), Instant.now()));
        KernelMemoryGovernanceService service = new KernelMemoryGovernanceService(
                new MemoryGovernanceServicePorts(shortTerm, longTerm, semantic, memoryEngine,
                        com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort.noop(), maintenance),
                0.6D, false, new MemoryDecayOptions(100, 0.2D, true));

        MemoryGovernanceRunResult result = service.runDecay("dry-run");

        assertThat(result.decayExecuted()).isTrue();
        assertThat(maintenance.deletedIds).isEmpty();
    }

    private static class RecordingShortTermMemoryPort extends RecordingMemoryPort implements ShortTermMemoryPort {
    }

    private static class RecordingShortTermMaintenancePort implements ShortTermMemoryMaintenancePort {

        final List<MemoryRecord> candidates = new ArrayList<>();
        final List<String> deletedIds = new ArrayList<>();

        @Override
        public List<MemoryRecord> scanExpiredOrDecayed(Instant now, double decayThreshold, int limit) {
            return candidates.stream().limit(limit).toList();
        }

        @Override
        public int markDeleted(List<String> memoryIds) {
            deletedIds.addAll(memoryIds);
            return memoryIds.size();
        }
    }

    private static class RecordingMemoryPort implements LongTermMemoryPort, SemanticMemoryPort {

        final List<MemoryRecord> records = new ArrayList<>();

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return records.stream()
                    .filter(record -> conversationId.equals(record.metadata().get("conversationId")))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return records.stream()
                    .filter(record -> userId.equals(record.metadata().get("userId")))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MemoryRecord record) {
            records.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            return records.removeIf(record -> record.id().equals(id));
        }
    }

    private static class RecordingMemoryEnginePort implements MemoryEnginePort {

        final List<String> assessedUsers = new ArrayList<>();
        boolean decayExecuted;
        MemoryQualityReport report;

        @Override
        public com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext loadMemory(
                com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest request) {
            return MemoryEnginePort.noop().loadMemory(request);
        }

        @Override
        public void writeMemory(com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest request) {
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem> retrieveMemories(
                com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest request) {
            return List.of();
        }

        @Override
        public void executeMemoryDecay() {
            decayExecuted = true;
        }

        @Override
        public MemoryQualityReport assessMemoryQuality(String userId) {
            assessedUsers.add(userId);
            if (report != null) {
                return report;
            }
            return MemoryQualityReport.builder().userId(userId).build();
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
            records.add(record);
        }

        @Override
        public boolean resolve(String conflictId, String action, String resolvedBy) {
            return false;
        }
    }
}
