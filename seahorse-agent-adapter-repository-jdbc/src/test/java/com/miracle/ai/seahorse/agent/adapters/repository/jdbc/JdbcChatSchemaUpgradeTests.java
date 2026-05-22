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

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcChatSchemaUpgradeTests {

    @Test
    void shouldKeepInitSqlAlignedWithReviewSchemaUpgrade() throws Exception {
        String initSql = Files.readString(Path.of("..", "resources", "database", "seahorse_init.sql"));

        assertThat(initSql).contains("CREATE TABLE t_memory_review_candidate");
        assertThat(initSql).contains("CREATE INDEX idx_memory_review_queue");
        assertThat(initSql).contains("CREATE INDEX idx_memory_review_operation");
        assertThat(initSql).contains("CREATE TABLE t_memory_review_feedback_sample");
        assertThat(initSql).contains("CREATE INDEX idx_memory_review_feedback_candidate");
        assertThat(initSql).contains("CREATE TABLE t_memory_trace_event");
        assertThat(initSql).contains("CREATE INDEX idx_memory_trace_recent");
        assertThat(initSql).contains("CREATE TABLE t_memory_keyword_index");
        assertThat(initSql).contains("CREATE UNIQUE INDEX uk_memory_keyword_memory");
        assertThat(initSql).contains("CREATE INDEX idx_memory_keyword_lookup");
    }

    @Test
    void shouldUpgradeEmptyDatabaseWithoutLayeredMemoryTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:chat-schema-upgrade-empty;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        new JdbcChatSchemaUpgrade(dataSource).upgrade();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(tableExists(jdbcTemplate, "t_user_profile_fact")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_correction_ledger")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_operation_log")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_outbox")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_review_candidate")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_review_feedback_sample")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_trace_event")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_maintenance_run")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_memory_maintenance_run", "compaction_scanned_count")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_memory_maintenance_run", "compaction_group_count")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_memory_maintenance_run", "compaction_fragment_count")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_entity_alias")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_entity_relation")).isTrue();
        assertThat(tableExists(jdbcTemplate, "t_memory_keyword_index")).isTrue();
    }

    @Test
    void shouldBackfillMaintenanceCompactionColumnsForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:chat-schema-upgrade-maintenance-run;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_memory_maintenance_run (
                    id VARCHAR(128) PRIMARY KEY,
                    reason VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    compaction_requested SMALLINT NOT NULL DEFAULT 0,
                    alias_requested SMALLINT NOT NULL DEFAULT 0,
                    gc_requested SMALLINT NOT NULL DEFAULT 0,
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

        new JdbcChatSchemaUpgrade(dataSource).upgrade();

        assertThat(columnExists(jdbcTemplate, "t_memory_maintenance_run", "compaction_scanned_count")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_memory_maintenance_run", "compaction_group_count")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_memory_maintenance_run", "compaction_fragment_count")).isTrue();
    }

    @Test
    void shouldBackfillProfileFactLifecycleColumnsForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:chat-schema-upgrade-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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

        new JdbcChatSchemaUpgrade(dataSource).upgrade();

        assertThat(columnExists(jdbcTemplate, "t_user_profile_fact", "version")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_user_profile_fact", "last_referenced_at")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_user_profile_fact", "access_count")).isTrue();
    }

    @Test
    void shouldBackfillVectorLifecycleColumnsForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:chat-schema-upgrade-vector;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_long_term_memory_vector (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    content TEXT NOT NULL,
                    embedding TEXT NOT NULL,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);

        new JdbcChatSchemaUpgrade(dataSource).upgrade();

        assertThat(columnExists(jdbcTemplate, "t_long_term_memory_vector", "tenant_id")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_long_term_memory_vector", "generation_id")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_long_term_memory_vector", "status")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_long_term_memory_vector", "last_referenced_at")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_long_term_memory_vector", "access_count")).isTrue();
    }

    @Test
    void shouldBackfillDerivedIndexDeletionMarkerForLayeredMemoryTables() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:chat-schema-upgrade-memory-gc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_short_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    content TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_long_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    content TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_semantic_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    semantic_key VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);

        new JdbcChatSchemaUpgrade(dataSource).upgrade();

        assertThat(columnExists(jdbcTemplate, "t_short_term_memory", "derived_indexes_deleted_at")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_long_term_memory", "derived_indexes_deleted_at")).isTrue();
        assertThat(columnExists(jdbcTemplate, "t_semantic_memory", "derived_indexes_deleted_at")).isTrue();
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
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

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
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
}
