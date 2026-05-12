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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionStart;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedule;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshScheduleUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDocumentRefreshScheduleAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcDocumentRefreshScheduleAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:document-refresh;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcDocumentRefreshScheduleAdapter(dataSource);
    }

    @Test
    void shouldUpsertFindDueAndWriteExecutionState() {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        adapter.upsert(new DocumentRefreshSchedule(
                null, "doc-1", "kb-1", "0 0/5 * * * ?", true,
                now.minusSeconds(1), null, null, null));

        DocumentRefreshSchedule stored = adapter.findByDocumentId("doc-1").orElseThrow();
        assertThat(adapter.findDueSchedules(now, 10)).extracting(DocumentRefreshSchedule::docId)
                .containsExactly("doc-1");

        String execId = adapter.start(new DocumentRefreshExecutionStart(
                stored.id(), "doc-1", "kb-1", now));
        adapter.finish(new DocumentRefreshExecutionFinish(
                execId, stored.id(), "doc-1", "kb-1", "success", "OK",
                now, now.plusSeconds(2), "remote.txt", 12L, "hash-1", null, null));
        adapter.updateState(new DocumentRefreshScheduleUpdate(
                stored.id(), "success", null, now, now.plusSeconds(300), "hash-1", null, null));

        assertThat(adapter.findDueSchedules(now, 10)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM t_knowledge_document_schedule_exec WHERE id = ?",
                String.class, execId)).isEqualTo("success");
        assertThat(adapter.findByDocumentId("doc-1").orElseThrow().lastContentHash()).isEqualTo("hash-1");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document_schedule_exec");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document_schedule");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document_schedule (
                    id VARCHAR(64) PRIMARY KEY,
                    doc_id VARCHAR(64) NOT NULL UNIQUE,
                    kb_id VARCHAR(64) NOT NULL,
                    cron_expr VARCHAR(64),
                    enabled SMALLINT,
                    next_run_time TIMESTAMP,
                    last_run_time TIMESTAMP,
                    last_success_time TIMESTAMP,
                    last_status VARCHAR(16),
                    last_error VARCHAR(512),
                    last_etag VARCHAR(256),
                    last_modified VARCHAR(256),
                    last_content_hash VARCHAR(128),
                    lock_owner VARCHAR(128),
                    lock_until TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document_schedule_exec (
                    id VARCHAR(64) PRIMARY KEY,
                    schedule_id VARCHAR(64) NOT NULL,
                    doc_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    message VARCHAR(512),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    file_name VARCHAR(512),
                    file_size BIGINT,
                    content_hash VARCHAR(128),
                    etag VARCHAR(256),
                    last_modified VARCHAR(256),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
    }
}
