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

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryConflictResolutionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelMemoryConflictResolutionServiceTests {

    @Test
    void shouldKeepSelectedMemoryAndRetireTheOtherMemory() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort()
                .put(memory("memory-a", "Keep this memory"))
                .put(memory("memory-b", "Retire this memory"));
        RecordingConflictLogRepository conflicts = conflicts("conflict-keep", "memory-a", "memory-b");
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        RecordingAuditRepository auditRepository = new RecordingAuditRepository();
        KernelMemoryManagementService service = service(shortTerm, conflicts, traceRecorder, auditRepository);

        boolean resolved = service.resolveConflict(new MemoryConflictResolutionCommand(
                "conflict-keep",
                "keep_a",
                "interactive:user-1",
                "chat-ui",
                "",
                "",
                "default"));

        assertTrue(resolved);
        assertTrue(shortTerm.findById("memory-a").isPresent());
        assertFalse(shortTerm.findById("memory-b").isPresent());
        assertEquals("RESOLVED", conflicts.record("conflict-keep").resolutionStatus());
        assertEquals("keep_a", conflicts.record("conflict-keep").resolutionAction());
        assertConflictTrace(traceRecorder.events.get(0), "conflict-keep", "keep_a", "chat-ui");
        assertConflictAudit(auditRepository.saved.get(0), "conflict-keep", "keep_a", "chat-ui");
    }

    @Test
    void shouldDiscardBothUnderlyingMemoriesWhenRequested() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort()
                .put(memory("memory-a", "Discard A"))
                .put(memory("memory-b", "Discard B"));
        RecordingConflictLogRepository conflicts = conflicts("conflict-discard", "memory-a", "memory-b");

        boolean resolved = service(shortTerm, conflicts, new RecordingTraceRecorder(), new RecordingAuditRepository())
                .resolveConflict(new MemoryConflictResolutionCommand(
                        "conflict-discard",
                        "discard",
                        "interactive:user-1",
                        "chat-ui",
                        "",
                        "",
                        "default"));

        assertTrue(resolved);
        assertFalse(shortTerm.findById("memory-a").isPresent());
        assertFalse(shortTerm.findById("memory-b").isPresent());
        assertEquals("discard", conflicts.record("conflict-discard").resolutionAction());
    }

    @Test
    void shouldCreateMergedMemoryAndRetireOriginalMemories() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort()
                .put(memory("memory-a", "Original A"))
                .put(memory("memory-b", "Original B"));
        RecordingConflictLogRepository conflicts = conflicts("conflict-merge", "memory-a", "memory-b");

        boolean resolved = service(shortTerm, conflicts, new RecordingTraceRecorder(), new RecordingAuditRepository())
                .resolveConflict(new MemoryConflictResolutionCommand(
                        "conflict-merge",
                        "merge",
                        "interactive:user-1",
                        "chat-ui",
                        "Merged memory text",
                        "",
                        "default"));

        assertTrue(resolved);
        assertFalse(shortTerm.findById("memory-a").isPresent());
        assertFalse(shortTerm.findById("memory-b").isPresent());
        assertEquals(List.of("Merged memory text"), shortTerm.activeContents());
        assertEquals("merge", conflicts.record("conflict-merge").resolutionAction());
    }

    private static KernelMemoryManagementService service(RecordingShortTermMemoryPort shortTerm,
                                                         RecordingConflictLogRepository conflicts,
                                                         RecordingTraceRecorder traceRecorder,
                                                         RecordingAuditRepository auditRepository) {
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        return new KernelMemoryManagementService(new MemoryManagementServicePorts(
                new RecordingWorkingMemoryPort(),
                shortTerm,
                new RecordingLongTermMemoryPort(),
                new RecordingSemanticMemoryPort(),
                MemoryQualitySnapshotRepositoryPort.empty(),
                conflicts,
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                MemoryOperationLogPort.noop(),
                MemoryOutboxPort.noop(),
                MemoryReviewManagementRepositoryPort.empty(),
                MemoryPolicyConfigPort.defaults(),
                traceRecorder,
                auditLedger));
    }

    private static RecordingConflictLogRepository conflicts(String conflictId, String memoryId1, String memoryId2) {
        RecordingConflictLogRepository repository = new RecordingConflictLogRepository();
        repository.save(new MemoryConflictRecord(
                conflictId,
                "user-1",
                memoryId1,
                memoryId2,
                "CONTRADICTION",
                "HIGH",
                "PENDING",
                "",
                "",
                Instant.EPOCH,
                Instant.EPOCH));
        return repository;
    }

    private static MemoryRecord memory(String id, String content) {
        return new MemoryRecord(
                id,
                "short_term",
                "PROFILE",
                content,
                Map.of("userId", "user-1", "tenantId", "default", "importanceScore", 0.9D),
                Instant.EPOCH);
    }

    private static void assertConflictTrace(MemoryTraceEvent event,
                                            String conflictId,
                                            String action,
                                            String source) {
        assertEquals("memory-conflict", event.component());
        assertEquals("interactive-resolve", event.eventType());
        assertEquals(MemoryTraceEvent.STATUS_SUCCESS, event.status());
        assertEquals(conflictId, event.subjectId());
        assertEquals(source, event.details().get("source"));
        assertEquals(action, event.details().get("action"));
        assertEquals("interactive:user-1", event.details().get("operator"));
    }

    private static void assertConflictAudit(AuditEvent event,
                                            String conflictId,
                                            String action,
                                            String source) {
        assertNotNull(event);
        assertEquals(AuditEventType.MEMORY_CONFLICT_RESOLVED, event.eventType());
        assertEquals(AuditActorType.USER, event.actorType());
        assertEquals("interactive:user-1", event.actorId());
        assertEquals("memory_conflict", event.resourceType());
        assertEquals(conflictId, event.resourceId());
        assertTrue(event.redactedPayload().contains("\"source\":\"" + source + "\""));
        assertTrue(event.redactedPayload().contains("\"action\":\"" + action + "\""));
    }

    private static class RecordingMemoryStore implements MemoryStorePort {

        private final Map<String, MemoryRecord> records = new LinkedHashMap<>();
        private final List<String> deleted = new ArrayList<>();

        RecordingMemoryStore put(MemoryRecord record) {
            records.put(record.id(), record);
            return this;
        }

        @Override
        public Optional<MemoryRecord> findById(String id) {
            if (deleted.contains(id)) {
                return Optional.empty();
            }
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return records.values().stream()
                    .filter(record -> !deleted.contains(record.id()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MemoryRecord record) {
            String id = record.id().isBlank() ? "saved-" + records.size() : record.id();
            records.put(id, new MemoryRecord(
                    id,
                    record.layer(),
                    record.type(),
                    record.content(),
                    record.metadata(),
                    record.updatedAt()));
        }

        @Override
        public boolean deleteById(String id) {
            if (!records.containsKey(id) || deleted.contains(id)) {
                return false;
            }
            deleted.add(id);
            return true;
        }

        List<String> activeContents() {
            return records.values().stream()
                    .filter(record -> !deleted.contains(record.id()))
                    .map(MemoryRecord::content)
                    .toList();
        }
    }

    private static final class RecordingWorkingMemoryPort extends RecordingMemoryStore implements WorkingMemoryPort {
    }

    private static final class RecordingShortTermMemoryPort extends RecordingMemoryStore implements ShortTermMemoryPort {
        @Override
        RecordingShortTermMemoryPort put(MemoryRecord record) {
            super.put(record);
            return this;
        }
    }

    private static final class RecordingLongTermMemoryPort extends RecordingMemoryStore implements LongTermMemoryPort {
    }

    private static final class RecordingSemanticMemoryPort extends RecordingMemoryStore implements SemanticMemoryPort {
    }

    private static final class RecordingConflictLogRepository implements MemoryConflictLogRepositoryPort {

        private final Map<String, MemoryConflictRecord> records = new LinkedHashMap<>();

        @Override
        public Optional<MemoryConflictRecord> findById(String conflictId) {
            return Optional.ofNullable(records.get(conflictId));
        }

        @Override
        public List<MemoryConflictRecord> listByUser(String userId, String status, int limit) {
            return records.values().stream()
                    .filter(record -> userId.equals(record.userId()))
                    .filter(record -> status == null || status.isBlank() || status.equals(record.resolutionStatus()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MemoryConflictRecord record) {
            records.put(record.id(), record);
        }

        @Override
        public boolean resolve(String conflictId, String action, String resolvedBy) {
            MemoryConflictRecord record = records.get(conflictId);
            if (record == null) {
                return false;
            }
            records.put(conflictId, new MemoryConflictRecord(
                    record.id(),
                    record.userId(),
                    record.memoryId1(),
                    record.memoryId2(),
                    record.conflictType(),
                    record.severity(),
                    "RESOLVED",
                    action,
                    resolvedBy,
                    Instant.now(),
                    record.createTime()));
            return true;
        }

        MemoryConflictRecord record(String conflictId) {
            return records.get(conflictId);
        }
    }

    private static final class RecordingTraceRecorder implements MemoryTraceRecorder {

        private final List<MemoryTraceEvent> events = new ArrayList<>();

        @Override
        public void record(MemoryTraceEvent event) {
            events.add(event);
        }

        @Override
        public List<MemoryTraceEvent> listRecent(int limit) {
            return events.stream().limit(limit).toList();
        }
    }

    private static final class RecordingAuditRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> saved = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            saved.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return saved.stream()
                    .filter(event -> auditId.equals(event.auditId()))
                    .findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(saved, saved.size(), query.size(), query.current(), 1L);
        }
    }
}
