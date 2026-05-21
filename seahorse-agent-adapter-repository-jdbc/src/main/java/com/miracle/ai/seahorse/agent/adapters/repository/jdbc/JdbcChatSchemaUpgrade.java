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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * Upgrades legacy chat tables so existing Docker volumes can store modern IDs.
 */
public class JdbcChatSchemaUpgrade {

    private static final int TARGET_LENGTH = 64;

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatSchemaUpgrade(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    public void upgrade() {
        ensureMemoryProfileTables();
        widenColumns("t_conversation", List.of("id", "conversation_id", "user_id"));
        widenColumns("t_conversation_summary", List.of("id", "conversation_id", "user_id", "last_message_id"));
        widenColumns("t_message", List.of("id", "conversation_id", "user_id"));
        widenColumns("t_message_feedback", List.of("id", "message_id", "conversation_id", "user_id"));
        widenColumns("t_rag_trace_run", List.of("id", "trace_id", "conversation_id", "task_id", "user_id"));
        widenColumns("t_rag_trace_node", List.of("id", "trace_id", "node_id", "parent_node_id", "node_type"));
        widenColumns("t_short_term_memory", List.of("id", "user_id", "conversation_id"));
        ensureLayeredMemoryLifecycleColumns();
    }

    private void ensureMemoryProfileTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_operation_log (
                    operation_id VARCHAR(128) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    operation_type VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(32) NOT NULL,
                    target_key VARCHAR(128),
                    request_json JSONB NOT NULL,
                    decision_json JSONB,
                    status VARCHAR(32) NOT NULL,
                    policy_version VARCHAR(64) NOT NULL,
                    error_message TEXT,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_operation_user_time
                ON t_memory_operation_log (user_id, tenant_id, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_outbox (
                    id VARCHAR(128) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    task_type VARCHAR(64) NOT NULL,
                    target_id VARCHAR(128),
                    payload_json JSONB NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    next_retry_time TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_outbox_status
                ON t_memory_outbox (status, next_retry_time, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_review_candidate (
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
                    confidence_level NUMERIC(5, 4) DEFAULT 0,
                    importance_score NUMERIC(5, 4) DEFAULT 0,
                    value_score NUMERIC(5, 4) DEFAULT 0,
                    risk_score NUMERIC(5, 4) DEFAULT 0,
                    reason TEXT,
                    source_message_ids JSONB,
                    candidate_metadata JSONB,
                    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    reviewer_id VARCHAR(64),
                    reviewer_comment TEXT,
                    chosen_content TEXT,
                    chosen_metadata JSONB,
                    reviewed_memory_id VARCHAR(64),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        ensureMemoryReviewCandidateColumns();
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_queue
                ON t_memory_review_candidate (tenant_id, user_id, review_status, update_time)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_operation
                ON t_memory_review_candidate (operation_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_maintenance_run (
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
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_maintenance_run_status_time
                ON t_memory_maintenance_run (status, update_time)
                """);
        addColumnIfMissing("t_memory_maintenance_run", "compaction_scanned_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "compaction_group_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "compaction_fragment_count", "INTEGER NOT NULL DEFAULT 0");
        ensureMemoryEntityAliasTables();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_review_feedback_sample (
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
                    rejected_metadata JSONB,
                    chosen_metadata JSONB,
                    source_message_ids JSONB,
                    reviewed_memory_id VARCHAR(64),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_feedback_candidate
                ON t_memory_review_feedback_sample (candidate_id, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_user_profile_fact (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    slot_key VARCHAR(128) NOT NULL,
                    value_text TEXT NOT NULL,
                    value_json JSONB,
                    confidence_level NUMERIC(4, 3) DEFAULT 0,
                    source_type VARCHAR(64),
                    source_ids JSONB,
                    generation_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    version BIGINT NOT NULL DEFAULT 1,
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    last_referenced_at TIMESTAMP,
                    access_count INTEGER NOT NULL DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        ensureProfileFactColumns();
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_user_profile_active
                ON t_user_profile_fact (user_id, tenant_id, status, slot_key)
                """);
        executePostgresPartialIndexOrPlainIndex("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profile_active_slot
                ON t_user_profile_fact (user_id, tenant_id, slot_key)
                WHERE status = 'ACTIVE' AND deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS uk_user_profile_active_slot
                ON t_user_profile_fact (user_id, tenant_id, slot_key)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_correction_ledger (
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
                    source_message_ids JSONB,
                    effective_generation_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_correction_active
                ON t_memory_correction_ledger (user_id, tenant_id, status, target_kind, target_key)
                """);
        executePostgresPartialIndexOrPlainIndex("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_correction_active_target
                ON t_memory_correction_ledger (user_id, tenant_id, target_kind, target_key)
                WHERE status = 'ACTIVE' AND deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS uk_memory_correction_active_target
                ON t_memory_correction_ledger (user_id, tenant_id, target_kind, target_key)
                """);
    }

    private void ensureMemoryEntityAliasTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_entity_alias (
                    id VARCHAR(128) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    alias_text VARCHAR(256) NOT NULL,
                    normalized_alias VARCHAR(256) NOT NULL,
                    canonical_entity_id VARCHAR(128) NOT NULL,
                    canonical_name VARCHAR(256) NOT NULL,
                    entity_type VARCHAR(64) NOT NULL DEFAULT 'ENTITY',
                    confidence_level NUMERIC(4, 3) DEFAULT 0,
                    source_type VARCHAR(64),
                    source_memory_ids JSONB,
                    metadata_json JSONB,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_alias_lookup
                ON t_memory_entity_alias (user_id, tenant_id, normalized_alias, status)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_entity_relation (
                    id VARCHAR(128) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    memory_id VARCHAR(128) NOT NULL,
                    layer_name VARCHAR(32),
                    memory_type VARCHAR(64),
                    content TEXT,
                    source_entity_id VARCHAR(128) NOT NULL,
                    target_entity_id VARCHAR(128) NOT NULL,
                    relation_type VARCHAR(64) NOT NULL DEFAULT 'MENTIONS',
                    weight NUMERIC(6, 4) DEFAULT 1,
                    confidence_level NUMERIC(4, 3) DEFAULT 1,
                    metadata_json JSONB,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_relation_source
                ON t_memory_entity_relation (user_id, tenant_id, source_entity_id, status)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_relation_target
                ON t_memory_entity_relation (user_id, tenant_id, target_entity_id, status)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_relation_memory
                ON t_memory_entity_relation (user_id, tenant_id, memory_id, status)
                """);
    }

    private void ensureProfileFactColumns() {
        if (!tableExists("t_user_profile_fact")) {
            return;
        }
        addColumnIfMissing("t_user_profile_fact", "version", "BIGINT NOT NULL DEFAULT 1");
        addColumnIfMissing("t_user_profile_fact", "last_referenced_at", "TIMESTAMP");
        addColumnIfMissing("t_user_profile_fact", "access_count", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureMemoryReviewCandidateColumns() {
        if (!tableExists("t_memory_review_candidate")) {
            return;
        }
        addColumnIfMissing("t_memory_review_candidate", "chosen_content", "TEXT");
        addColumnIfMissing("t_memory_review_candidate", "chosen_metadata", "JSONB");
    }

    private void executePostgresPartialIndexOrPlainIndex(String postgresSql, String fallbackSql) {
        try {
            jdbcTemplate.execute(postgresSql);
        } catch (BadSqlGrammarException ex) {
            jdbcTemplate.execute(fallbackSql);
        }
    }

    private void ensureLayeredMemoryLifecycleColumns() {
        ensureLifecycleColumns("t_short_term_memory");
        ensureLifecycleColumns("t_long_term_memory");
        ensureLifecycleColumns("t_semantic_memory");
        ensureVectorLifecycleColumns();
        createLifecycleIndexes();
    }

    private void ensureLifecycleColumns(String tableName) {
        if (!tableExists(tableName)) {
            return;
        }
        addColumnIfMissing(tableName, "tenant_id", "VARCHAR(64) DEFAULT 'default'");
        addColumnIfMissing(tableName, "status", "VARCHAR(32) DEFAULT 'ACTIVE'");
        addColumnIfMissing(tableName, "generation_id", "VARCHAR(64)");
        addColumnIfMissing(tableName, "valid_from", "TIMESTAMP");
        addColumnIfMissing(tableName, "valid_until", "TIMESTAMP");
        addColumnIfMissing(tableName, "last_referenced_at", "TIMESTAMP");
        addColumnIfMissing(tableName, "schema_version", "VARCHAR(32)");
        addColumnIfMissing(tableName, "policy_version", "VARCHAR(64)");
        addColumnIfMissing(tableName, "sensitivity_level", "VARCHAR(32)");
        addColumnIfMissing(tableName, "obsolete_reason", "TEXT");
        addColumnIfMissing(tableName, "derived_indexes_deleted_at", "TIMESTAMP");
    }

    private void ensureVectorLifecycleColumns() {
        if (!tableExists("t_long_term_memory_vector")) {
            return;
        }
        addColumnIfMissing("t_long_term_memory_vector", "tenant_id", "VARCHAR(64) DEFAULT 'default'");
        addColumnIfMissing("t_long_term_memory_vector", "generation_id", "VARCHAR(64)");
        addColumnIfMissing("t_long_term_memory_vector", "status", "VARCHAR(32) DEFAULT 'ACTIVE'");
        addColumnIfMissing("t_long_term_memory_vector", "last_referenced_at", "TIMESTAMP");
        addColumnIfMissing("t_long_term_memory_vector", "access_count", "INTEGER NOT NULL DEFAULT 0");
    }

    private void createLifecycleIndexes() {
        createIndexIfTableExists("t_short_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_stm_lifecycle_user_status
                ON t_short_term_memory (user_id, tenant_id, status, update_time)
                """);
        createIndexIfTableExists("t_long_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_ltm_lifecycle_user_status
                ON t_long_term_memory (user_id, tenant_id, status, update_time)
                """);
        createIndexIfTableExists("t_semantic_memory", """
                CREATE INDEX IF NOT EXISTS idx_sem_lifecycle_user_status
                ON t_semantic_memory (user_id, tenant_id, status, update_time)
                """);
        createIndexIfTableExists("t_short_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_stm_gc_derived_indexes
                ON t_short_term_memory (status, derived_indexes_deleted_at, update_time)
                """);
        createIndexIfTableExists("t_long_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_ltm_gc_derived_indexes
                ON t_long_term_memory (status, derived_indexes_deleted_at, update_time)
                """);
        createIndexIfTableExists("t_semantic_memory", """
                CREATE INDEX IF NOT EXISTS idx_sem_gc_derived_indexes
                ON t_semantic_memory (status, derived_indexes_deleted_at, update_time)
                """);
        createIndexIfTableExists("t_long_term_memory_vector", """
                CREATE INDEX IF NOT EXISTS idx_ltm_vector_lifecycle
                ON t_long_term_memory_vector (user_id, tenant_id, status, update_time)
                """);
    }

    private void createIndexIfTableExists(String tableName, String sql) {
        if (tableExists(tableName)) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private boolean tableExists(String tableName) {
        return !jdbcTemplate.query(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE lower(table_name) = lower(?)
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        tableName)
                .isEmpty();
    }

    private boolean columnExists(String tableName, String columnName) {
        return !jdbcTemplate.query(
                        """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        tableName,
                        columnName)
                .isEmpty();
    }

    private void widenColumns(String tableName, List<String> columns) {
        for (String column : columns) {
            Integer currentLength = jdbcTemplate.query(
                            """
                            SELECT character_maximum_length
                            FROM information_schema.columns
                            WHERE table_name = ? AND column_name = ?
                            """,
                            (rs, rowNum) -> rs.getObject(1, Integer.class),
                            tableName,
                            column)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (currentLength == null || currentLength >= TARGET_LENGTH) {
                continue;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE " + tableName + " ALTER COLUMN " + column + " TYPE VARCHAR(" + TARGET_LENGTH + ")");
        }
    }
}
