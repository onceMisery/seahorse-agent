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
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
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

    private void executePostgresPartialIndexOrPlainIndex(String postgresSql, String fallbackSql) {
        try {
            jdbcTemplate.execute(postgresSql);
        } catch (BadSqlGrammarException ex) {
            jdbcTemplate.execute(fallbackSql);
        }
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
