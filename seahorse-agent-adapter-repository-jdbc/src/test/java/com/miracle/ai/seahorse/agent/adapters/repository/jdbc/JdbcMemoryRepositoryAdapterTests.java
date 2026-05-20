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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcShortTermMemoryRepositoryAdapter shortTermAdapter;
    private JdbcLongTermMemoryRepositoryAdapter longTermAdapter;
    private JdbcSemanticMemoryRepositoryAdapter semanticAdapter;
    private JdbcMemoryQualitySnapshotRepositoryAdapter snapshotAdapter;
    private JdbcMemoryConflictLogRepositoryAdapter conflictAdapter;
    private JdbcProfileMemoryRepositoryAdapter profileAdapter;
    private JdbcCorrectionLedgerRepositoryAdapter correctionAdapter;

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
                });
        assertThat(profileAdapter.listActive("user-1", "default", 10))
                .extracting(fact -> fact.slotKey() + "=" + fact.valueText())
                .containsExactly("identity.occupation=老师");
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

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_short_term_memory");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_long_term_memory");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_semantic_memory");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_quality_snapshot");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_conflict_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user_profile_fact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_memory_correction_ledger");
        jdbcTemplate.execute("""
                CREATE TABLE t_short_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
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
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_long_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
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
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_semantic_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    semantic_key VARCHAR(128),
                    semantic_type VARCHAR(32),
                    value_json TEXT,
                    confidence_level DOUBLE,
                    source_memory_ids TEXT,
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
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
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
