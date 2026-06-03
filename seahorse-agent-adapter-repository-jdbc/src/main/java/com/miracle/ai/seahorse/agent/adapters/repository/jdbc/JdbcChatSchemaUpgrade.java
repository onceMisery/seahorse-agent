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
        ensureConversationAttachmentTable();
        ensureMemoryProfileTables();
        widenColumns("t_conversation", List.of("id", "conversation_id", "user_id"));
        widenColumns("t_conversation_summary", List.of("id", "conversation_id", "user_id", "last_message_id"));
        widenColumns("t_message", List.of("id", "conversation_id", "user_id"));
        ensureMessageRunLinkage();
        ensureAgentSkillTables();
        widenColumns("t_message_feedback", List.of("id", "message_id", "conversation_id", "user_id"));
        widenColumns("t_rag_trace_run", List.of("id", "trace_id", "conversation_id", "task_id", "user_id"));
        widenColumns("t_rag_trace_node", List.of("id", "trace_id", "node_id", "parent_node_id", "node_type"));
        widenColumns("t_short_term_memory", List.of("id", "user_id", "conversation_id"));
        ensureLayeredMemoryLifecycleColumns();
    }

    private void ensureAgentSkillTables() {
        if (tableExists("sa_agent_version")) {
            addColumnIfMissing("sa_agent_version", "skill_set_json", "TEXT NOT NULL DEFAULT '{}'");
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_agent_skill (
                    pk_id BIGSERIAL PRIMARY KEY,
                    skill_name VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    category VARCHAR(32) NOT NULL,
                    source VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    enabled SMALLINT NOT NULL DEFAULT 1,
                    latest_revision_id VARCHAR(128),
                    description TEXT NOT NULL,
                    tags_json TEXT NOT NULL DEFAULT '[]',
                    allowed_tools_json TEXT NOT NULL DEFAULT '[]',
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    UNIQUE(tenant_id, skill_name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_tenant_status
                ON sa_agent_skill (tenant_id, status, enabled)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_agent_skill_revision (
                    pk_id BIGSERIAL PRIMARY KEY,
                    revision_id VARCHAR(128) NOT NULL UNIQUE,
                    skill_name VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    revision_no BIGINT NOT NULL,
                    content_hash VARCHAR(128) NOT NULL,
                    content TEXT NOT NULL,
                    frontmatter_json TEXT NOT NULL,
                    scan_decision VARCHAR(32) NOT NULL,
                    scan_result_json TEXT NOT NULL,
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    UNIQUE(tenant_id, skill_name, revision_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_revision_skill
                ON sa_agent_skill_revision (tenant_id, skill_name, revision_no)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_agent_skill_binding (
                    pk_id BIGSERIAL PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    skill_name VARCHAR(128) NOT NULL,
                    revision_id VARCHAR(128) NOT NULL,
                    inject_mode VARCHAR(32) NOT NULL DEFAULT 'METADATA_AND_BODY',
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_binding_agent
                ON sa_agent_skill_binding (tenant_id, agent_id, deleted)
                """);
    }

    private void ensureConversationAttachmentTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_conversation_attachment (
                    pk_id BIGSERIAL PRIMARY KEY,
                    attachment_id VARCHAR(64) NOT NULL UNIQUE,
                    conversation_id VARCHAR(64) NOT NULL,
                    message_id VARCHAR(64),
                    user_id VARCHAR(64) NOT NULL,
                    file_name VARCHAR(256) NOT NULL,
                    mime_type VARCHAR(128) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    storage_ref VARCHAR(1000) NOT NULL,
                    parse_status VARCHAR(32) NOT NULL,
                    resource_ref_json TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_conversation_attachment_user
                ON sa_conversation_attachment (conversation_id, user_id, created_at)
                """);
    }

    private void ensureMessageRunLinkage() {
        if (!tableExists("t_message")) {
            return;
        }
        addColumnIfMissing("t_message", "agent_run_id", "VARCHAR(64)");
        createIndexIfTableExists("t_message", """
                CREATE INDEX IF NOT EXISTS idx_message_agent_run
                ON t_message (agent_run_id, user_id, create_time)
                """);
    }

    private void ensureMemoryProfileTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_operation_log (
                    pk_id BIGSERIAL PRIMARY KEY,
                    operation_id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
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
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    task_type VARCHAR(64) NOT NULL,
                    target_id VARCHAR(64),
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
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    operation_id VARCHAR(128),
                    user_id BIGINT NOT NULL,
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
                    reviewed_memory_id VARCHAR(128),
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
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    reason VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    compaction_requested SMALLINT NOT NULL DEFAULT 0,
                    alias_requested SMALLINT NOT NULL DEFAULT 0,
                    gc_requested SMALLINT NOT NULL DEFAULT 0,
                    compaction_scanned_count INTEGER NOT NULL DEFAULT 0,
                    compaction_group_count INTEGER NOT NULL DEFAULT 0,
                    compaction_fragment_count INTEGER NOT NULL DEFAULT 0,
                    alias_scanned_count INTEGER NOT NULL DEFAULT 0,
                    alias_normalized_count INTEGER NOT NULL DEFAULT 0,
                    alias_dictionary_match_count INTEGER NOT NULL DEFAULT 0,
                    alias_skipped_count INTEGER NOT NULL DEFAULT 0,
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
        addColumnIfMissing("t_memory_maintenance_run", "alias_scanned_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_normalized_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_dictionary_match_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_skipped_count", "INTEGER NOT NULL DEFAULT 0");
        ensureMemoryEntityAliasTables();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_review_feedback_sample (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    candidate_id VARCHAR(128) NOT NULL,
                    operation_id VARCHAR(128),
                    user_id BIGINT NOT NULL,
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
                    reviewed_memory_id VARCHAR(128),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_feedback_candidate
                ON t_memory_review_feedback_sample (candidate_id, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_trace_event (
                    id BIGINT PRIMARY KEY,
                    trace_id VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id BIGINT,
                    conversation_id BIGINT,
                    session_id VARCHAR(128),
                    component VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32),
                    subject_id VARCHAR(128),
                    subject_type VARCHAR(64),
                    details_json JSONB,
                    occurred_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_trace_recent
                ON t_memory_trace_event (occurred_at, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_trace_filters
                ON t_memory_trace_event (tenant_id, user_id, component, status, occurred_at)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_aggregation_buffer (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id BIGINT NOT NULL,
                    conversation_id BIGINT NOT NULL,
                    session_id VARCHAR(128) NOT NULL,
                    turn_count INTEGER NOT NULL DEFAULT 0,
                    total_tokens INTEGER NOT NULL DEFAULT 0,
                    turns_json JSONB NOT NULL,
                    version BIGINT NOT NULL DEFAULT 1,
                    first_activity_at TIMESTAMP NOT NULL,
                    last_activity_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_aggregation_session
                ON t_memory_aggregation_buffer (tenant_id, user_id, session_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_aggregation_scan
                ON t_memory_aggregation_buffer (last_activity_at, update_time)
                """);
        ensureMemoryAggregationBufferColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_user_profile_fact (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
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
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_conflict_log (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    memory_id_1 VARCHAR(64) NOT NULL,
                    memory_id_2 VARCHAR(64) NOT NULL,
                    conflict_type VARCHAR(32) NOT NULL,
                    severity VARCHAR(16) NOT NULL,
                    resolution_status VARCHAR(16) NOT NULL,
                    resolution_action VARCHAR(32),
                    resolved_by VARCHAR(32),
                    resolved_at TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_conflict_user_status
                ON t_memory_conflict_log (user_id, resolution_status, create_time DESC)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_quality_snapshot (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    snapshot_json JSONB NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_quality_snapshot_user_time
                ON t_memory_quality_snapshot (user_id, create_time DESC)
                """);
        ensureMemoryBusinessIdColumnTypes();
    }

    private void ensureMemoryEntityAliasTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_entity_alias (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
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
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    memory_id VARCHAR(64) NOT NULL,
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
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_keyword_index (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    memory_id VARCHAR(64) NOT NULL,
                    layer_name VARCHAR(32),
                    memory_type VARCHAR(64),
                    content TEXT,
                    metadata_json JSONB,
                    source_update_time TIMESTAMP,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_keyword_memory
                ON t_memory_keyword_index (user_id, tenant_id, memory_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_keyword_lookup
                ON t_memory_keyword_index (user_id, tenant_id, status, update_time)
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

    private void ensureMemoryAggregationBufferColumns() {
        if (!tableExists("t_memory_aggregation_buffer")) {
            return;
        }
        addColumnIfMissing("t_memory_aggregation_buffer", "version", "BIGINT NOT NULL DEFAULT 1");
    }

    private void ensureMemoryBusinessIdColumnTypes() {
        alterColumnToVarcharIfExists("t_memory_operation_log", "operation_id", 128);
        alterColumnToVarcharIfExists("t_memory_outbox", "id", 128);
        alterColumnToVarcharIfExists("t_memory_outbox", "target_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "id", 128);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "operation_id", 128);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "conversation_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "message_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "reviewer_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "reviewed_memory_id", 128);
        alterColumnToVarcharIfExists("t_memory_maintenance_run", "id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "candidate_id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "operation_id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "reviewer_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "reviewed_memory_id", 128);
        alterColumnToVarcharIfExists("t_memory_trace_event", "trace_id", 128);
        alterColumnToVarcharIfExists("t_memory_trace_event", "subject_id", 128);
        alterColumnToVarcharIfExists("t_user_profile_fact", "generation_id", 64);
        alterColumnToVarcharIfExists("t_memory_correction_ledger", "effective_generation_id", 64);
        alterColumnToVarcharIfExists("t_memory_entity_alias", "id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_alias", "canonical_entity_id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "memory_id", 64);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "source_entity_id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "target_entity_id", 128);
        alterColumnToVarcharIfExists("t_memory_keyword_index", "memory_id", 64);
        alterColumnToVarcharIfExists("t_memory_conflict_log", "id", 128);
        alterColumnToVarcharIfExists("t_memory_conflict_log", "memory_id_1", 64);
        alterColumnToVarcharIfExists("t_memory_conflict_log", "memory_id_2", 64);
        alterColumnToVarcharIfExists("t_memory_quality_snapshot", "id", 128);
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

    private void alterColumnToVarcharIfExists(String tableName, String columnName, int length) {
        if (!columnExists(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                    + " TYPE VARCHAR(" + length + ") USING " + columnName + "::VARCHAR");
        } catch (Exception ignored) {
            // Some embedded databases do not support PostgreSQL USING syntax; leave init SQL as the source of truth.
        }
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
            try {
                List<Integer> results = jdbcTemplate.query(
                        """
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_name = ? AND column_name = ?
                        """,
                        (rs, rowNum) -> rs.getObject(1, Integer.class),
                        tableName,
                        column);
                
                if (results.isEmpty()) {
                    continue;
                }
                
                Integer currentLength = results.get(0);
                if (currentLength == null || currentLength >= TARGET_LENGTH) {
                    continue;
                }
                jdbcTemplate.execute(
                        "ALTER TABLE " + tableName + " ALTER COLUMN " + column + " TYPE VARCHAR(" + TARGET_LENGTH + ")");
            } catch (Exception e) {
                // Skip if column is not VARCHAR type (e.g., BIGINT) or any other error
                continue;
            }
        }
    }
}
