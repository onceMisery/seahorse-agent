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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationType;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMemoryRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcShortTermMemoryRepositoryAdapter shortTermAdapter;
    private JdbcLongTermMemoryRepositoryAdapter longTermAdapter;
    private JdbcSemanticMemoryRepositoryAdapter semanticAdapter;
    private JdbcMemoryQualitySnapshotRepositoryAdapter snapshotAdapter;
    private JdbcMemoryConflictLogRepositoryAdapter conflictAdapter;
    private JdbcProfileMemoryRepositoryAdapter profileAdapter;
    private JdbcCorrectionLedgerRepositoryAdapter correctionAdapter;
    private JdbcMemoryOperationLogRepositoryAdapter operationLogAdapter;
    private JdbcMemoryOutboxRepositoryAdapter outboxAdapter;
    private JdbcMemoryReviewCandidateRepositoryAdapter reviewCandidateAdapter;
    private JdbcMemoryReviewFeedbackRepositoryAdapter reviewFeedbackAdapter;
    private JdbcMemoryMaintenanceRunRepositoryAdapter maintenanceRunAdapter;
    private JdbcMemoryLifecycleRepositoryAdapter lifecycleAdapter;
    private JdbcMemoryKeywordSearchRepositoryAdapter keywordSearchAdapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        createSchema();
        shortTermAdapter = new JdbcShortTermMemoryRepositoryAdapter(dataSource, objectMapper);
        longTermAdapter = new JdbcLongTermMemoryRepositoryAdapter(dataSource, objectMapper);
        semanticAdapter = new JdbcSemanticMemoryRepositoryAdapter(dataSource, objectMapper);
        snapshotAdapter = new JdbcMemoryQualitySnapshotRepositoryAdapter(dataSource, objectMapper);
        conflictAdapter = new JdbcMemoryConflictLogRepositoryAdapter(dataSource);
        profileAdapter = new JdbcProfileMemoryRepositoryAdapter(dataSource, objectMapper);
        correctionAdapter = new JdbcCorrectionLedgerRepositoryAdapter(dataSource, objectMapper);
        operationLogAdapter = new JdbcMemoryOperationLogRepositoryAdapter(dataSource, objectMapper);
        outboxAdapter = new JdbcMemoryOutboxRepositoryAdapter(dataSource, objectMapper);
        reviewCandidateAdapter = new JdbcMemoryReviewCandidateRepositoryAdapter(dataSource, objectMapper);
        reviewFeedbackAdapter = new JdbcMemoryReviewFeedbackRepositoryAdapter(dataSource, objectMapper);
        maintenanceRunAdapter = new JdbcMemoryMaintenanceRunRepositoryAdapter(dataSource, objectMapper);
        lifecycleAdapter = new JdbcMemoryLifecycleRepositoryAdapter(dataSource);
        keywordSearchAdapter = new JdbcMemoryKeywordSearchRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldSaveAndReadLayeredMemories() {
        shortTermAdapter.save(new MemoryRecord("", "short_term", "PROFILE", "Name is Alice",
                Map.of("userId", "user-1", "conversationId", "conv-1", "importanceScore", 0.8D), Instant.now()));
        longTermAdapter.save(new MemoryRecord("", "long_term", "FACT", "Uses Java",
                Map.of("userId", "user-1", "importanceScore", 0.7D), Instant.now()));
        semanticAdapter.save(new MemoryRecord("", "semantic", "PROFILE", "Name is Alice",
                Map.of("userId", "user-1", "semanticKey", "profile:name"), Instant.now()));

        assertThat(shortTermAdapter.listByConversation("conv-1", 10))
                .extracting(MemoryRecord::content)
                .containsExactly("Name is Alice");
        assertThat(longTermAdapter.listByUser("user-1", 10))
                .extracting(MemoryRecord::type)
                .containsExactly("FACT");
        assertThat(semanticAdapter.listByUser("user-1", 10)).hasSize(1);
    }

    @Test
    void shouldSearchLayeredMemoriesByKeywordWithoutReturningObsoleteRows() {
        shortTermAdapter.save(new MemoryRecord("stm-pip", "short_term", "FACT",
                "Pulsar PIP-459 failed during compatibility testing",
                Map.of("userId", "user-1", "tenantId", "default", "conversationId", "conv-1",
                        "importanceScore", 0.6D),
                Instant.now()));
        longTermAdapter.save(new MemoryRecord("ltm-pip", "long_term", "PROJECT_FACT",
                "PIP-459 rollback plan",
                Map.of("userId", "user-1", "tenantId", "default", "importanceScore", 0.9D),
                Instant.now()));
        semanticAdapter.save(new MemoryRecord("sem-other", "semantic", "PROJECT_FACT",
                "Unrelated semantic memory",
                Map.of("userId", "user-1", "tenantId", "default", "semanticKey", "project:other"),
                Instant.now()));
        jdbcTemplate.update("""
                UPDATE t_short_term_memory
                SET status = 'OBSOLETE'
                WHERE id = 'stm-pip'
                """);

        var hits = keywordSearchAdapter.search("user-1", "default", "PIP-459", 10);

        assertThat(hits).extracting(hit -> hit.memoryId())
                .containsExactly("ltm-pip");
        assertThat(hits.get(0).layer()).isEqualTo("LONG_TERM");
        assertThat(hits.get(0).score()).isPositive();
    }

    @Test
    void shouldSaveAndReadQualitySnapshotsAndResolveConflicts() {
        snapshotAdapter.save(new MemoryQualitySnapshot("", "user-1",
                Map.of("conflictCount", 1, "governancePolicyVersion", "memory-governance-v1"),
                Instant.now()));
        conflictAdapter.save(new MemoryConflictRecord("", "user-1", "m1", "m2",
                "CONTRADICTION", "HIGH", "PENDING", "", "", null, Instant.now()));

        assertThat(snapshotAdapter.listByUser("user-1", 10)).hasSize(1);
        assertThat(snapshotAdapter.listByUser("user-1", 10).get(0).snapshot())
                .containsEntry("conflictCount", 1);
        assertThat(conflictAdapter.listByUser("user-1", "PENDING", 10)).hasSize(1);
        String conflictId = conflictAdapter.listByUser("user-1", "PENDING", 10).get(0).id();
        assertThat(conflictAdapter.resolve(conflictId, "keep-latest", "admin")).isTrue();
        assertThat(conflictAdapter.listByUser("user-1", "RESOLVED", 10)).hasSize(1);
    }

    @Test
    void shouldScanAndDeleteExpiredOrDecayedShortTermMemories() {
        jdbcTemplate.update("""
                INSERT INTO t_short_term_memory
                (id, user_id, conversation_id, memory_type, content, metadata_json, source_message_ids,
                 importance_score, access_count, last_access_time, decay_score, expires_time,
                 create_time, update_time, deleted)
                VALUES ('expired-1', 'user-1', 'conv-1', 'FACT', 'expired', '{}', '[]',
                        0.2, 0, CURRENT_TIMESTAMP, 0.5, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
        jdbcTemplate.update("""
                INSERT INTO t_short_term_memory
                (id, user_id, conversation_id, memory_type, content, metadata_json, source_message_ids,
                 importance_score, access_count, last_access_time, decay_score, expires_time,
                 create_time, update_time, deleted)
                VALUES ('decayed-1', 'user-1', 'conv-1', 'FACT', 'decayed', '{}', '[]',
                        0.2, 0, CURRENT_TIMESTAMP, 0.05, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));

        var candidates = shortTermAdapter.scanExpiredOrDecayed(Instant.now(), 0.1D, 10);

        assertThat(candidates).extracting(MemoryRecord::id)
                .containsExactly("expired-1", "decayed-1");
        assertThat(shortTermAdapter.markDeleted(candidates.stream().map(MemoryRecord::id).toList()))
                .isEqualTo(2);
        assertThat(shortTermAdapter.findById("expired-1")).isEmpty();
        assertThat(shortTermAdapter.findById("decayed-1")).isEmpty();
    }

    @Test
    void shouldScanAndMarkDerivedIndexDeleteCandidates() {
        Instant oldUpdateTime = Instant.now().minusSeconds(10L * 24 * 3600);
        jdbcTemplate.update("""
                INSERT INTO t_short_term_memory
                (id, user_id, tenant_id, conversation_id, memory_type, content, metadata_json, source_message_ids,
                 importance_score, access_count, last_access_time, decay_score, expires_time,
                 status, update_time, deleted)
                VALUES ('gc-stm', 'user-1', 'tenant-1', 'conv-1', 'FACT', 'obsolete short', '{}', '[]',
                        0.2, 0, CURRENT_TIMESTAMP, 0.5, CURRENT_TIMESTAMP,
                        'OBSOLETE', ?, 0)
                """, java.sql.Timestamp.from(oldUpdateTime));
        jdbcTemplate.update("""
                INSERT INTO t_long_term_memory
                (id, user_id, tenant_id, memory_category, title, content, source_type, source_ids, tags,
                 importance_score, confidence_level, status, update_time, deleted)
                VALUES ('gc-ltm', 'user-1', 'tenant-1', 'FACT', 'title', 'compacted long', 'short_term', '[]', '{}',
                        0.2, 0.5, 'COMPACTED', ?, 0)
                """, java.sql.Timestamp.from(oldUpdateTime));
        jdbcTemplate.update("""
                INSERT INTO t_semantic_memory
                (id, user_id, tenant_id, semantic_key, semantic_type, value_json, confidence_level, source_memory_ids,
                 status, update_time, deleted)
                VALUES ('active-sem', 'user-1', 'tenant-1', 'project:active', 'FACT', '{}', 0.9, '[]',
                        'ACTIVE', ?, 0)
                """, java.sql.Timestamp.from(oldUpdateTime));

        var candidates = lifecycleAdapter.scanDerivedIndexDeleteCandidates(
                Instant.now(),
                java.time.Duration.ofDays(7),
                10);

        assertThat(candidates)
                .extracting(MemoryGarbageCollectionCandidate::memoryId)
                .containsExactly("gc-stm", "gc-ltm");
        assertThat(lifecycleAdapter.markDerivedIndexesDeleted(
                candidates.stream().map(MemoryGarbageCollectionCandidate::memoryId).toList(),
                Instant.now()))
                .isEqualTo(2);
        assertThat(lifecycleAdapter.scanDerivedIndexDeleteCandidates(
                Instant.now(),
                java.time.Duration.ofDays(7),
                10))
                .isEmpty();
    }

    @Test
    void shouldArchiveColdLowScoreLifecycleCandidates() {
        Instant oldUpdateTime = Instant.now().minusSeconds(120L * 24 * 3600);
        jdbcTemplate.update("""
                INSERT INTO t_long_term_memory
                (id, user_id, tenant_id, memory_category, title, content, source_type, source_ids, tags,
                 importance_score, confidence_level, status, last_referenced_at, update_time, create_time, deleted)
                VALUES ('cold-ltm', 'user-1', 'tenant-1', 'FACT', 'cold', 'low value long memory',
                        'short_term', '[]', '{}', 0.1, 0.1, 'ACTIVE', ?, ?, ?, 0)
                """,
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime));
        jdbcTemplate.update("""
                INSERT INTO t_long_term_memory
                (id, user_id, tenant_id, memory_category, title, content, source_type, source_ids, tags,
                 importance_score, confidence_level, status, last_referenced_at, update_time, create_time, deleted)
                VALUES ('strong-ltm', 'user-1', 'tenant-1', 'FACT', 'strong', 'important long memory',
                        'short_term', '[]', '{}', 0.9, 0.9, 'ACTIVE', ?, ?, ?, 0)
                """,
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime));
        jdbcTemplate.update("""
                INSERT INTO t_semantic_memory
                (id, user_id, tenant_id, semantic_key, semantic_type, value_json, confidence_level, source_memory_ids,
                 status, last_referenced_at, update_time, create_time, deleted)
                VALUES ('cold-sem', 'user-1', 'tenant-1', 'project:cold', 'FACT', '{}', 0.1, '[]',
                        'ACTIVE', ?, ?, ?, 0)
                """,
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime));

        var candidates = lifecycleAdapter.scanLifecycleArchiveCandidates(
                Instant.now(),
                java.time.Duration.ofDays(90),
                0.15D,
                10);

        assertThat(candidates)
                .extracting(MemoryGarbageCollectionCandidate::memoryId)
                .containsExactly("cold-ltm", "cold-sem");
        assertThat(lifecycleAdapter.markArchived(
                candidates.stream().map(MemoryGarbageCollectionCandidate::memoryId).toList(),
                Instant.now(),
                "generational gc archive"))
                .isEqualTo(2);
        assertThat(longTermAdapter.listByUser("user-1", 10))
                .extracting(MemoryRecord::id)
                .containsExactly("strong-ltm");
        assertThat(semanticAdapter.listByUser("user-1", 10)).isEmpty();
        assertThat(lifecycleAdapter.scanDerivedIndexDeleteCandidates(
                Instant.now(),
                java.time.Duration.ZERO,
                10))
                .extracting(MemoryGarbageCollectionCandidate::memoryId)
                .containsExactly("cold-ltm", "cold-sem");
    }

    @Test
    void shouldPhysicallyDeleteInactiveRowsOnlyAfterDerivedIndexesAreRemoved() {
        Instant oldUpdateTime = Instant.now().minusSeconds(40L * 24 * 3600);
        jdbcTemplate.update("""
                INSERT INTO t_long_term_memory
                (id, user_id, tenant_id, memory_category, title, content, source_type, source_ids, tags,
                 importance_score, confidence_level, status, derived_indexes_deleted_at, update_time, deleted)
                VALUES ('purge-ltm', 'user-1', 'tenant-1', 'FACT', 'purge', 'archived long memory',
                        'short_term', '[]', '{}', 0.1, 0.1, 'ARCHIVED', ?, ?, 0)
                """,
                java.sql.Timestamp.from(oldUpdateTime),
                java.sql.Timestamp.from(oldUpdateTime));
        jdbcTemplate.update("""
                INSERT INTO t_semantic_memory
                (id, user_id, tenant_id, semantic_key, semantic_type, value_json, confidence_level, source_memory_ids,
                 status, derived_indexes_deleted_at, update_time, deleted)
                VALUES ('waiting-sem', 'user-1', 'tenant-1', 'project:waiting', 'FACT', '{}', 0.1, '[]',
                        'ARCHIVED', NULL, ?, 0)
                """,
                java.sql.Timestamp.from(oldUpdateTime));

        var candidates = lifecycleAdapter.scanPhysicalDeleteCandidates(
                Instant.now(),
                java.time.Duration.ofDays(30),
                10);

        assertThat(candidates)
                .extracting(MemoryGarbageCollectionCandidate::memoryId)
                .containsExactly("purge-ltm");
        assertThat(lifecycleAdapter.markPhysicallyDeleted(
                candidates.stream().map(MemoryGarbageCollectionCandidate::memoryId).toList(),
                Instant.now()))
                .isEqualTo(1);
        assertThat(longTermAdapter.findById("purge-ltm")).isEmpty();
        Map<String, Object> purgedRow = jdbcTemplate.queryForMap("""
                SELECT status, deleted
                FROM t_long_term_memory
                WHERE id = 'purge-ltm'
                """);
        assertThat(purgedRow).containsEntry("STATUS", "PHYSICAL_DELETED");
        assertThat(((Number) purgedRow.get("DELETED")).intValue()).isEqualTo(1);
        Map<String, Object> waitingRow = jdbcTemplate.queryForMap("""
                SELECT status, deleted
                FROM t_semantic_memory
                WHERE id = 'waiting-sem'
                """);
        assertThat(waitingRow).containsEntry("STATUS", "ARCHIVED");
        assertThat(((Number) waitingRow.get("DELETED")).intValue()).isZero();
    }

    @Test
    void shouldUpsertProfileFactAsStrongFactSource() {
        profileAdapter.upsert(new ProfileFactUpdate(
                "user-1",
                "default",
                "identity.occupation",
                "学生",
                0.9D,
                "explicit_user_memory",
                java.util.List.of("msg-1"),
                "identity.occupation:g1"));
        profileAdapter.upsert(new ProfileFactUpdate(
                "user-1",
                "default",
                "identity.occupation",
                "老师",
                0.95D,
                "explicit_user_correction",
                java.util.List.of("msg-2"),
                "identity.occupation:g2"));

        assertThat(profileAdapter.findActive("user-1", "default", "identity.occupation"))
                .hasValueSatisfying(fact -> {
                    assertThat(fact.valueText()).isEqualTo("老师");
                    assertThat(fact.sourceType()).isEqualTo("explicit_user_correction");
                    assertThat(fact.generationId()).isEqualTo("identity.occupation:g2");
                    assertThat(fact.version()).isEqualTo(2L);
                });
        assertThat(profileAdapter.listActive("user-1", "default", 10))
                .extracting(fact -> fact.slotKey() + "=" + fact.valueText())
                .containsExactly("identity.occupation=老师");
    }

    @Test
    void shouldKeepProfileFactHistoryWhenSlotIsUpdated() {
        profileAdapter.upsert(new ProfileFactUpdate(
                "user-1",
                "default",
                "identity.occupation",
                "student",
                0.9D,
                "explicit_user_memory",
                java.util.List.of("msg-1"),
                "identity.occupation:g1"));
        profileAdapter.upsert(new ProfileFactUpdate(
                "user-1",
                "default",
                "identity.occupation",
                "teacher",
                0.95D,
                "explicit_user_correction",
                java.util.List.of("msg-2"),
                "identity.occupation:g2"));

        assertThat(jdbcTemplate.queryForList("""
                SELECT value_text, status, version
                FROM t_user_profile_fact
                WHERE user_id = 'user-1'
                  AND tenant_id = 'default'
                  AND slot_key = 'identity.occupation'
                ORDER BY version ASC
                """))
                .hasSize(2)
                .satisfies(rows -> {
                    assertThat(rows.get(0)).containsEntry("STATUS", "HISTORICAL")
                            .containsEntry("VERSION", 1L);
                    assertThat(rows.get(1)).containsEntry("STATUS", "ACTIVE")
                            .containsEntry("VERSION", 2L);
                });
    }

    @Test
    void shouldRecordProfileFactReadFeedback() {
        profileAdapter.upsert(new ProfileFactUpdate(
                "user-1",
                "default",
                "preferences.response_style",
                "concise",
                0.9D,
                "explicit_user_memory",
                java.util.List.of("msg-1"),
                "preferences.response_style:g1"));

        Instant referencedAt = Instant.parse("2026-05-20T01:02:03Z");
        profileAdapter.recordRead("user-1", "default", "preferences.response_style", referencedAt);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT access_count, last_referenced_at
                FROM t_user_profile_fact
                WHERE user_id = 'user-1'
                  AND tenant_id = 'default'
                  AND slot_key = 'preferences.response_style'
                  AND status = 'ACTIVE'
                """);
        assertThat(row.get("ACCESS_COUNT")).isEqualTo(1);
        assertThat(row.get("LAST_REFERENCED_AT")).isNotNull();
    }

    @Test
    void shouldUpsertAndListActiveCorrectionRules() {
        correctionAdapter.upsert(new CorrectionCommand(
                "user-1",
                "default",
                "PROFILE_CORRECTION",
                "PROFILE_SLOT",
                "identity.occupation",
                "学生",
                "老师",
                "用户纠正职业画像：学生 -> 老师",
                java.util.List.of("msg-2"),
                "identity.occupation:g2"));

        assertThat(correctionAdapter.listActive("user-1", "default", 10))
                .hasSize(1)
                .first()
                .satisfies(rule -> {
                    assertThat(rule.targetKind()).isEqualTo("PROFILE_SLOT");
                    assertThat(rule.targetKey()).isEqualTo("identity.occupation");
                    assertThat(rule.incorrectValue()).isEqualTo("学生");
                    assertThat(rule.correctValue()).isEqualTo("老师");
                    assertThat(rule.priority()).isEqualTo("HARD_RULE");
                });
    }

    @Test
    void shouldUseOperationLogAsIdempotencyGuard() {
        MemoryOperation operation = new MemoryOperation(
                "op-1",
                "user-1",
                "default",
                MemoryOperationType.ADD,
                "SHORT_TERM_MEMORY",
                "",
                Map.of("messageId", "msg-1"),
                "high_precision_rule_v1",
                Instant.now());

        assertThat(operationLogAdapter.tryStart(operation)).isTrue();
        assertThat(operationLogAdapter.tryStart(operation)).isFalse();

        operationLogAdapter.markCompleted("op-1", MemoryOperationStatus.SUCCEEDED,
                Map.of("memoryType", "PREFERENCE", "action", "ADD"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, operation_type, target_kind, decision_json
                FROM t_memory_operation_log
                WHERE operation_id = 'op-1'
                """);
        assertThat(row.get("STATUS")).isEqualTo("SUCCEEDED");
        assertThat(row.get("OPERATION_TYPE")).isEqualTo("ADD");
        assertThat(row.get("TARGET_KIND")).isEqualTo("SHORT_TERM_MEMORY");
        assertThat(row.get("DECISION_JSON").toString()).contains("PREFERENCE");
    }

    @Test
    void shouldEnqueueAndCompleteMemoryOutboxTasks() {
        outboxAdapter.enqueue(new MemoryOutboxPort.MemoryOutboxTask(
                "outbox-1",
                "VECTOR_UPSERT",
                "stm-1",
                "user-1",
                "default",
                Map.of("memoryId", "stm-1", "embeddingModel", "default"),
                "vector down",
                null,
                Instant.now()));

        assertThat(outboxAdapter.pollPending(10))
                .hasSize(1)
                .first()
                .satisfies(task -> {
                    assertThat(task.id()).isEqualTo("outbox-1");
                    assertThat(task.taskType()).isEqualTo("VECTOR_UPSERT");
                    assertThat(task.targetId()).isEqualTo("stm-1");
                    assertThat(task.payload()).containsEntry("memoryId", "stm-1");
                });

        outboxAdapter.markSucceeded("outbox-1");

        assertThat(outboxAdapter.pollPending(10)).isEmpty();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status
                FROM t_memory_outbox
                WHERE id = 'outbox-1'
                """);
        assertThat(row.get("STATUS")).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldIgnoreDuplicateDerivedIndexDeleteOutboxTasks() {
        outboxAdapter.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorDelete("stm-1", "user-1", "default"));
        outboxAdapter.enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorDelete("stm-1", "user-1", "default"));

        assertThat(outboxAdapter.pollPending(10))
                .hasSize(1)
                .first()
                .satisfies(task -> {
                    assertThat(task.taskType()).isEqualTo("VECTOR_DELETE");
                    assertThat(task.targetId()).isEqualTo("stm-1");
                });
        Integer rowCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_memory_outbox
                WHERE task_type = 'VECTOR_DELETE'
                  AND target_id = 'stm-1'
                """, Integer.class);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void shouldPersistPendingMemoryReviewCandidateWithoutWritingActiveMemory() {
        reviewCandidateAdapter.save(new MemoryReviewCandidate(
                "review-op-1",
                "op-1",
                "default",
                "user-1",
                "conv-1",
                "msg-1",
                MemoryIngestionAction.REVIEW,
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.ambiguous",
                "user might be changing the project stack",
                0.62D,
                0.70D,
                0.70D,
                0.20D,
                "needs_review",
                java.util.List.of("msg-1"),
                Map.of("reviewReason", "low_confidence"),
                Instant.now()));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT user_id, tenant_id, operation_id, requested_action, target_kind, target_key,
                       candidate_content, review_status, candidate_metadata
                FROM t_memory_review_candidate
                WHERE id = 'review-op-1'
                """);

        assertThat(row.get("USER_ID")).isEqualTo("user-1");
        assertThat(row.get("TENANT_ID")).isEqualTo("default");
        assertThat(row.get("OPERATION_ID")).isEqualTo("op-1");
        assertThat(row.get("REQUESTED_ACTION")).isEqualTo("REVIEW");
        assertThat(row.get("TARGET_KIND")).isEqualTo("PROJECT_FACT");
        assertThat(row.get("TARGET_KEY")).isEqualTo("project.ambiguous");
        assertThat(row.get("CANDIDATE_CONTENT")).isEqualTo("user might be changing the project stack");
        assertThat(row.get("REVIEW_STATUS")).isEqualTo("PENDING");
        assertThat(row.get("CANDIDATE_METADATA").toString()).contains("low_confidence");
        assertThat(shortTermAdapter.listByUser("user-1", 10)).isEmpty();
    }

    @Test
    void shouldPageAndApplyMemoryReviewDecision() {
        reviewCandidateAdapter.save(new MemoryReviewCandidate(
                "review-op-1",
                "op-1",
                "default",
                "user-1",
                "conv-1",
                "msg-1",
                MemoryIngestionAction.REVIEW,
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.ambiguous",
                "user might be changing the project stack",
                0.62D,
                0.70D,
                0.70D,
                0.20D,
                "needs_review",
                java.util.List.of("msg-1"),
                Map.of("reviewReason", "low_confidence"),
                Instant.now()));

        var page = reviewCandidateAdapter.pageReviewCandidates(
                new MemoryReviewQuery("default", "user-1", MemoryReviewStatus.PENDING, 1, 10));

        assertThat(page.records()).hasSize(1);
        MemoryReviewRecord pending = page.records().get(0);
        assertThat(pending.candidateId()).isEqualTo("review-op-1");
        assertThat(pending.reviewStatus()).isEqualTo(MemoryReviewStatus.PENDING);
        assertThat(pending.metadata()).containsEntry("reviewReason", "low_confidence");
        assertThat(reviewCandidateAdapter.findReviewItem("review-op-1")).isPresent();

        MemoryReviewRecord claimed = reviewCandidateAdapter.applyReviewDecision(new MemoryReviewDecision(
                "review-op-1",
                MemoryReviewStatus.APPLYING,
                "auditor",
                "approved",
                "",
                Map.of(),
                "",
                ""));
        assertThat(claimed.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLYING);

        MemoryReviewRecord applied = reviewCandidateAdapter.applyReviewDecision(new MemoryReviewDecision(
                "review-op-1",
                MemoryReviewStatus.APPLIED,
                "auditor",
                "approved",
                "approved content",
                Map.of("source", "human"),
                "memory-review-apply-review-op-1",
                "SHORT_TERM"));

        assertThat(applied.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(applied.reviewerId()).isEqualTo("auditor");
        assertThat(applied.reviewComment()).isEqualTo("approved");
        assertThat(applied.chosenContent()).isEqualTo("approved content");
        assertThat(applied.chosenMetadata()).containsEntry("source", "human");
        assertThat(applied.reviewedMemoryId()).isEqualTo("memory-review-apply-review-op-1");
        assertThat(reviewCandidateAdapter.pageReviewCandidates(
                new MemoryReviewQuery("default", "user-1", MemoryReviewStatus.PENDING, 1, 10)).records())
                .isEmpty();
    }

    @Test
    void shouldRejectStaleMemoryReviewDecisionWithoutOverwritingReviewedCandidate() {
        reviewCandidateAdapter.save(new MemoryReviewCandidate(
                "review-op-1",
                "op-1",
                "default",
                "user-1",
                "conv-1",
                "msg-1",
                MemoryIngestionAction.REVIEW,
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.ambiguous",
                "user might be changing the project stack",
                0.62D,
                0.70D,
                0.70D,
                0.20D,
                "needs_review",
                java.util.List.of("msg-1"),
                Map.of("reviewReason", "low_confidence"),
                Instant.now()));

        reviewCandidateAdapter.applyReviewDecision(new MemoryReviewDecision(
                "review-op-1",
                MemoryReviewStatus.APPLYING,
                "auditor-1",
                "approved",
                "",
                Map.of(),
                "",
                ""));
        reviewCandidateAdapter.applyReviewDecision(new MemoryReviewDecision(
                "review-op-1",
                MemoryReviewStatus.APPLIED,
                "auditor-1",
                "approved",
                "approved content",
                Map.of("source", "human"),
                "memory-review-apply-review-op-1",
                "SHORT_TERM"));

        assertThatThrownBy(() -> reviewCandidateAdapter.applyReviewDecision(new MemoryReviewDecision(
                "review-op-1",
                MemoryReviewStatus.REJECTED,
                "auditor-2",
                "stale reject",
                "",
                Map.of(),
                "",
                "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review candidate is not pending");

        MemoryReviewRecord stored = reviewCandidateAdapter.findReviewItem("review-op-1").orElseThrow();
        assertThat(stored.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(stored.reviewerId()).isEqualTo("auditor-1");
        assertThat(stored.reviewComment()).isEqualTo("approved");
        assertThat(stored.chosenContent()).isEqualTo("approved content");
    }

    @Test
    void shouldPersistMemoryReviewFeedbackSample() {
        reviewFeedbackAdapter.save(new MemoryReviewFeedbackSample(
                "feedback-review-op-1",
                "review-op-1",
                "op-1",
                "default",
                "user-1",
                "REVIEW",
                MemoryReviewStatus.REJECTED,
                "auditor",
                "not a stable fact",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.ambiguous",
                "model proposed unstable fact",
                "",
                Map.of("reviewReason", "low_confidence"),
                Map.of("chosen", "ignore"),
                java.util.List.of("msg-1", "msg-2"),
                "",
                "",
                Instant.now()));

        assertThat(reviewFeedbackAdapter.listByCandidate("review-op-1", 10))
                .hasSize(1)
                .first()
                .satisfies(sample -> {
                    assertThat(sample.sampleId()).isEqualTo("feedback-review-op-1");
                    assertThat(sample.reviewStatus()).isEqualTo(MemoryReviewStatus.REJECTED);
                    assertThat(sample.rejectedContent()).isEqualTo("model proposed unstable fact");
                    assertThat(sample.chosenContent()).isEmpty();
                    assertThat(sample.rejectedMetadata()).containsEntry("reviewReason", "low_confidence");
                    assertThat(sample.chosenMetadata()).containsEntry("chosen", "ignore");
                    assertThat(sample.sourceMessageIds()).containsExactly("msg-1", "msg-2");
                });
    }

    @Test
    void shouldPersistAndPageMaintenanceRuns() {
        maintenanceRunAdapter.save(new MemoryMaintenanceRunRecord(
                "run-1",
                "manual-maintenance",
                MemoryMaintenanceRunRecord.STATUS_SUCCEEDED_WITH_WARNINGS,
                true,
                true,
                true,
                4,
                2,
                7,
                2,
                3,
                1,
                false,
                java.util.List.of("ALIAS_UNAVAILABLE"),
                java.util.List.of("gc:transient"),
                Instant.EPOCH,
                Instant.EPOCH));

        var page = maintenanceRunAdapter.pageMaintenanceRuns(new MemoryMaintenanceRunQuery(
                MemoryMaintenanceRunRecord.STATUS_SUCCEEDED_WITH_WARNINGS,
                1,
                10));

        assertThat(page.records()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).runId()).isEqualTo("run-1");
        assertThat(page.records().get(0).compactionScannedCount()).isEqualTo(4);
        assertThat(page.records().get(0).compactionGroupCount()).isEqualTo(2);
        assertThat(page.records().get(0).compactionFragmentCount()).isEqualTo(7);
        assertThat(page.records().get(0).skippedTasks()).containsExactly("ALIAS_UNAVAILABLE");
        assertThat(page.records().get(0).errors()).containsExactly("gc:transient");
    }

    @Test
    void shouldFilterObsoleteMemoriesAndRecordLifecycleFeedback() {
        jdbcTemplate.update("""
                INSERT INTO t_short_term_memory
                (id, user_id, tenant_id, conversation_id, memory_type, content, metadata_json, source_message_ids,
                 importance_score, access_count, last_access_time, decay_score, expires_time, status,
                 generation_id, last_referenced_at, schema_version, policy_version, sensitivity_level,
                 obsolete_reason, create_time, update_time, deleted)
                VALUES ('active-1', 'user-1', 'default', 'conv-1', 'PROFILE', 'active student',
                        '{"profileSlot":"identity.occupation"}', '[]',
                        0.8, 0, CURRENT_TIMESTAMP, 0.5, ?,
                        'ACTIVE', 'identity.occupation:g1', NULL, '1', 'policy-v1', 'LOW', NULL,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));
        jdbcTemplate.update("""
                INSERT INTO t_short_term_memory
                (id, user_id, tenant_id, conversation_id, memory_type, content, metadata_json, source_message_ids,
                 importance_score, access_count, last_access_time, decay_score, expires_time, status,
                 generation_id, last_referenced_at, schema_version, policy_version, sensitivity_level,
                 obsolete_reason, create_time, update_time, deleted)
                VALUES ('obsolete-1', 'user-1', 'default', 'conv-1', 'PROFILE', 'obsolete student',
                        '{"profileSlot":"identity.occupation"}', '[]',
                        0.8, 0, CURRENT_TIMESTAMP, 0.5, ?,
                        'OBSOLETE', 'identity.occupation:old', NULL, '1', 'policy-v1', 'LOW',
                        'profile slot updated',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, java.sql.Timestamp.from(Instant.now().plusSeconds(3600)));

        assertThat(shortTermAdapter.listByUser("user-1", 10))
                .extracting(MemoryRecord::id)
                .containsExactly("active-1");

        Instant referencedAt = Instant.parse("2026-05-20T00:00:00Z");
        lifecycleAdapter.recordRead("short_term", "active-1", referencedAt);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT access_count, last_referenced_at, status
                FROM t_short_term_memory
                WHERE id = 'active-1'
                """);
        assertThat(row.get("ACCESS_COUNT")).isEqualTo(1);
        assertThat(row.get("LAST_REFERENCED_AT")).isNotNull();
        assertThat(row.get("STATUS")).isEqualTo("REFERENCED");
    }

    @Test
    void shouldMarkProfileSlotFragmentsObsoleteAcrossLayeredStores() {
        shortTermAdapter.save(new MemoryRecord("stm-profile", "short_term", "PROFILE", "student",
                Map.of("userId", "user-1", "tenantId", "default", "profileSlot", "identity.occupation",
                        "importanceScore", 0.8D),
                Instant.now()));
        longTermAdapter.save(new MemoryRecord("ltm-profile", "long_term", "PROFILE", "student",
                Map.of("userId", "user-1", "tenantId", "default", "profileSlot", "identity.occupation",
                        "importanceScore", 0.8D),
                Instant.now()));
        semanticAdapter.save(new MemoryRecord("sem-profile", "semantic", "PROFILE", "student",
                Map.of("userId", "user-1", "tenantId", "default", "semanticKey", "identity.occupation",
                        "profileSlot", "identity.occupation"),
                Instant.now()));

        int updated = lifecycleAdapter.markObsoleteByProfileSlot(
                "user-1", "default", "identity.occupation", "identity.occupation:g2", "profile slot updated");

        assertThat(updated).isEqualTo(3);
        assertThat(shortTermAdapter.listByUser("user-1", 10)).isEmpty();
        assertThat(longTermAdapter.listByUser("user-1", 10)).isEmpty();
        assertThat(semanticAdapter.listByUser("user-1", 10)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT obsolete_reason
                FROM t_semantic_memory
                WHERE id = 'sem-profile'
                """, String.class)).isEqualTo("profile slot updated");
    }

    @Test
    void shouldGroupCompactionCandidatesByCanonicalEntityId() {
        shortTermAdapter.save(new MemoryRecord("stm-entity-1", "short_term", "ENTITY_FACT", "OceanBase uses LSM trees",
                Map.of("userId", "user-1", "tenantId", "default", "canonicalEntityId", "entity-oceanbase",
                        "canonicalName", "OceanBase", "importanceScore", 0.8D),
                Instant.now()));
        longTermAdapter.save(new MemoryRecord("ltm-entity-1", "long_term", "ENTITY_FACT", "OB is OceanBase",
                Map.of("userId", "user-1", "tenantId", "default", "canonicalEntityId", "entity-oceanbase",
                        "canonicalName", "OceanBase"),
                Instant.now()));
        shortTermAdapter.save(new MemoryRecord("stm-entity-other", "short_term", "ENTITY_FACT", "TiDB is separate",
                Map.of("userId", "user-1", "tenantId", "default", "canonicalEntityId", "entity-tidb",
                        "canonicalName", "TiDB", "importanceScore", 0.7D),
                Instant.now()));

        List<MemoryCompactionCandidate> candidates = lifecycleAdapter.scanCompactionCandidates(10, 2);

        assertThat(candidates).hasSize(1);
        MemoryCompactionCandidate candidate = candidates.get(0);
        assertThat(candidate.groupKey()).isEqualTo("canonicalEntityId:entity-oceanbase");
        assertThat(candidate.strategy()).isEqualTo("canonicalEntityId");
        assertThat(candidate.fragments()).extracting(fragment -> fragment.memoryId())
                .containsExactlyInAnyOrder("stm-entity-1", "ltm-entity-1");
    }

    @Test
    void shouldScanAndMarkCompactionCandidatesWithoutTouchingWorkingMemory() {
        shortTermAdapter.save(new MemoryRecord("stm-compact-1", "short_term", "PROJECT_FACT", "alpha first",
                Map.of("userId", "user-1", "tenantId", "default", "semanticKey", "project.alpha",
                        "importanceScore", 0.8D),
                Instant.now()));
        shortTermAdapter.save(new MemoryRecord("stm-compact-2", "short_term", "PROJECT_FACT", "alpha second",
                Map.of("userId", "user-1", "tenantId", "default", "semanticKey", "project.alpha",
                        "importanceScore", 0.7D),
                Instant.now()));
        longTermAdapter.save(new MemoryRecord("ltm-single", "long_term", "PROJECT_FACT", "beta single",
                Map.of("userId", "user-1", "tenantId", "default", "semanticKey", "project.beta"),
                Instant.now()));

        List<MemoryCompactionCandidate> candidates = lifecycleAdapter.scanCompactionCandidates(10, 2);

        assertThat(candidates).hasSize(1);
        MemoryCompactionCandidate candidate = candidates.get(0);
        assertThat(candidate.groupKey()).isEqualTo("semanticKey:project.alpha");
        assertThat(candidate.userId()).isEqualTo("user-1");
        assertThat(candidate.fragments()).extracting(fragment -> fragment.memoryId())
                .containsExactlyInAnyOrder("stm-compact-1", "stm-compact-2");

        int marked = lifecycleAdapter.markCompacted(candidate, "master-1", Instant.EPOCH);

        assertThat(marked).isEqualTo(2);
        assertThat(shortTermAdapter.listByUser("user-1", 10))
                .extracting(MemoryRecord::id)
                .doesNotContain("stm-compact-1", "stm-compact-2");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT obsolete_reason
                FROM t_short_term_memory
                WHERE id = 'stm-compact-1'
                """, String.class)).isEqualTo("compacted into master-1");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_short_term_memory");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_long_term_memory");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_semantic_memory");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_quality_snapshot");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_conflict_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user_profile_fact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_correction_ledger");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_operation_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_outbox");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_review_candidate");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_review_feedback_sample");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_maintenance_run");
        jdbcTemplate.execute("""
                CREATE TABLE t_short_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    tenant_id VARCHAR(64) DEFAULT 'default',
                    conversation_id VARCHAR(64),
                    memory_type VARCHAR(32),
                    content TEXT,
                    metadata_json TEXT,
                    source_message_ids TEXT,
                    importance_score DOUBLE,
                    access_count INTEGER,
                    last_access_time TIMESTAMP,
                    decay_score DOUBLE,
                    expires_time TIMESTAMP,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    generation_id VARCHAR(64),
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    last_referenced_at TIMESTAMP,
                    schema_version VARCHAR(32),
                    policy_version VARCHAR(64),
                    sensitivity_level VARCHAR(32),
                    obsolete_reason TEXT,
                    derived_indexes_deleted_at TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_long_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    tenant_id VARCHAR(64) DEFAULT 'default',
                    memory_category VARCHAR(32),
                    title VARCHAR(128),
                    content TEXT,
                    source_type VARCHAR(32),
                    source_ids TEXT,
                    tags TEXT,
                    importance_score DOUBLE,
                    confidence_level DOUBLE,
                    embedding_model VARCHAR(64),
                    vector_ref_id VARCHAR(64),
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    generation_id VARCHAR(64),
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    last_referenced_at TIMESTAMP,
                    schema_version VARCHAR(32),
                    policy_version VARCHAR(64),
                    sensitivity_level VARCHAR(32),
                    obsolete_reason TEXT,
                    derived_indexes_deleted_at TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_semantic_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    tenant_id VARCHAR(64) DEFAULT 'default',
                    semantic_key VARCHAR(128),
                    semantic_type VARCHAR(32),
                    value_json TEXT,
                    confidence_level DOUBLE,
                    source_memory_ids TEXT,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    generation_id VARCHAR(64),
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    last_referenced_at TIMESTAMP,
                    schema_version VARCHAR(32),
                    policy_version VARCHAR(64),
                    sensitivity_level VARCHAR(32),
                    obsolete_reason TEXT,
                    derived_indexes_deleted_at TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_quality_snapshot (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    snapshot_json TEXT,
                    create_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_operation_log (
                    operation_id VARCHAR(128) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    operation_type VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(32) NOT NULL,
                    target_key VARCHAR(128),
                    request_json TEXT NOT NULL,
                    decision_json TEXT,
                    status VARCHAR(32) NOT NULL,
                    policy_version VARCHAR(64) NOT NULL,
                    error_message TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_outbox (
                    id VARCHAR(128) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    task_type VARCHAR(64) NOT NULL,
                    target_id VARCHAR(128),
                    payload_json TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    next_retry_time TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_review_candidate (
                    id VARCHAR(64) PRIMARY KEY,
                    operation_id VARCHAR(128),
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    conversation_id VARCHAR(64),
                    message_id VARCHAR(64),
                    requested_action VARCHAR(32) NOT NULL,
                    target_layer VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(64),
                    target_key VARCHAR(128),
                    candidate_content TEXT NOT NULL,
                    confidence_level DOUBLE,
                    importance_score DOUBLE,
                    value_score DOUBLE,
                    risk_score DOUBLE,
                    reason TEXT,
                    source_message_ids TEXT,
                    candidate_metadata TEXT,
                    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    reviewer_id VARCHAR(64),
                    reviewer_comment TEXT,
                    chosen_content TEXT,
                    chosen_metadata TEXT,
                    reviewed_memory_id VARCHAR(64),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_review_feedback_sample (
                    id VARCHAR(128) PRIMARY KEY,
                    candidate_id VARCHAR(64) NOT NULL,
                    operation_id VARCHAR(128),
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    requested_action VARCHAR(32) NOT NULL,
                    review_status VARCHAR(32) NOT NULL,
                    reviewer_id VARCHAR(64),
                    reviewer_comment TEXT,
                    target_layer VARCHAR(32),
                    target_kind VARCHAR(64),
                    target_key VARCHAR(128),
                    rejected_content TEXT,
                    chosen_content TEXT,
                    rejected_metadata TEXT,
                    chosen_metadata TEXT,
                    source_message_ids TEXT,
                    reviewed_memory_id VARCHAR(64),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_maintenance_run (
                    id VARCHAR(128) PRIMARY KEY,
                    reason VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    compaction_requested SMALLINT NOT NULL DEFAULT 0,
                    alias_requested SMALLINT NOT NULL DEFAULT 0,
                    gc_requested SMALLINT NOT NULL DEFAULT 0,
                    compaction_scanned_count INTEGER NOT NULL DEFAULT 0,
                    compaction_group_count INTEGER NOT NULL DEFAULT 0,
                    compaction_fragment_count INTEGER NOT NULL DEFAULT 0,
                    gc_scanned_count INTEGER NOT NULL DEFAULT 0,
                    gc_enqueued_count INTEGER NOT NULL DEFAULT 0,
                    gc_marked_count INTEGER NOT NULL DEFAULT 0,
                    gc_dry_run SMALLINT NOT NULL DEFAULT 0,
                    skipped_tasks TEXT,
                    errors TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_conflict_log (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    memory_id_1 VARCHAR(64),
                    memory_id_2 VARCHAR(64),
                    conflict_type VARCHAR(32),
                    severity VARCHAR(32),
                    resolution_status VARCHAR(32),
                    resolution_action VARCHAR(128),
                    resolved_by VARCHAR(64),
                    resolved_at TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_user_profile_fact (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    slot_key VARCHAR(128) NOT NULL,
                    value_text TEXT NOT NULL,
                    value_json TEXT,
                    confidence_level DOUBLE,
                    source_type VARCHAR(64),
                    source_ids TEXT,
                    generation_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    version BIGINT NOT NULL DEFAULT 1,
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    last_referenced_at TIMESTAMP,
                    access_count INTEGER NOT NULL DEFAULT 0,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_correction_ledger (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    correction_type VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(32) NOT NULL,
                    target_key VARCHAR(128) NOT NULL,
                    incorrect_value TEXT,
                    correct_value TEXT,
                    rule_text TEXT NOT NULL,
                    priority VARCHAR(32) NOT NULL DEFAULT 'HARD_RULE',
                    source_message_ids TEXT,
                    effective_generation_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}
