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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentUpdateValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcKnowledgeDocumentRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcKnowledgeDocumentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:knowledge-document;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcKnowledgeDocumentRepositoryAdapter(dataSource);
    }

    @Test
    void shouldQueryPageUpdateEnableDeleteAndListLogs() {
        KnowledgeDocumentDetail detail = adapter.findDetailById(1L).orElseThrow();
        KnowledgeDocumentPage page = adapter.page(1L, 1, 10, "success", "Guide");
        KnowledgeDocumentChunkLogPage logs = adapter.chunkLogs(1L, 1, 10);
        KnowledgeDocumentUpdateValues values = new KnowledgeDocumentUpdateValues();
        values.setDocName("Updated Guide");
        values.setProcessMode("pipeline");
        values.setPipelineId("pipeline-1");
        values.setOperator("tester");

        boolean updated = adapter.update(1L, values);
        String updatedDocName = adapter.findDetailById(1L).orElseThrow().getDocName();
        boolean disabled = adapter.updateEnabled(1L, false, "tester");
        int disabledChunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_chunk WHERE doc_id = 1 AND enabled = 0",
                Integer.class);
        boolean deleted = adapter.delete(1L, "tester");

        assertThat(detail.getCollectionName()).isEqualTo("collection-a");
        assertThat(page.records()).extracting(KnowledgeDocumentDetail::getId).containsExactly(1L);
        assertThat(logs.records()).hasSize(1);
        assertThat(logs.records().get(0).getOtherDuration()).isEqualTo(30L);
        assertThat(updated).isTrue();
        assertThat(updatedDocName).isEqualTo("Updated Guide");
        assertThat(disabled).isTrue();
        assertThat(disabledChunks).isEqualTo(2);
        assertThat(deleted).isTrue();
        assertThat(adapter.findDetailById(1L)).isEmpty();
        assertThat(adapter.listEnabledChunks(1L)).isEmpty();
    }

    @Test
    void shouldReadChunkMetadataWhenGovernanceColumnExists() {
        jdbcTemplate.execute("ALTER TABLE t_knowledge_chunk ADD COLUMN metadata_json VARCHAR(2048)");
        jdbcTemplate.update("""
                UPDATE t_knowledge_chunk
                SET metadata_json = '{"department":"研发","acl_subjects":["user-1"]}'
                WHERE id = 10
                """);

        List<KnowledgeChunkRecord> records = adapter.listEnabledChunks(1L);

        KnowledgeChunkRecord chunk = records.stream()
                .filter(record -> Long.valueOf(10L).equals(record.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(chunk.getMetadata()).containsEntry("department", "研发");
        assertThat(chunk.getMetadata().get("acl_subjects")).asList().contains("user-1");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document_chunk_log");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_chunk");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_pipeline");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_base");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(128),
                    embedding_model VARCHAR(128),
                    collection_name VARCHAR(128),
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64),
                    doc_name VARCHAR(128),
                    source_type VARCHAR(32),
                    source_location VARCHAR(256),
                    schedule_enabled INTEGER,
                    schedule_cron VARCHAR(128),
                    enabled INTEGER,
                    chunk_count INTEGER,
                    file_url VARCHAR(512),
                    file_type VARCHAR(64),
                    file_size BIGINT,
                    chunk_strategy VARCHAR(64),
                    process_mode VARCHAR(32),
                    chunk_config VARCHAR(512),
                    pipeline_id VARCHAR(64),
                    status VARCHAR(32),
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64),
                    chunk_index INTEGER,
                    content VARCHAR(512),
                    content_hash VARCHAR(128),
                    char_count INTEGER,
                    token_count INTEGER,
                    enabled INTEGER,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_pipeline (
                    id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(128),
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document_chunk_log (
                    id VARCHAR(64) PRIMARY KEY,
                    doc_id VARCHAR(64),
                    status VARCHAR(32),
                    process_mode VARCHAR(32),
                    chunk_strategy VARCHAR(64),
                    pipeline_id VARCHAR(64),
                    extract_duration BIGINT,
                    chunk_duration BIGINT,
                    embed_duration BIGINT,
                    persist_duration BIGINT,
                    total_duration BIGINT,
                    chunk_count INTEGER,
                    error_message VARCHAR(512),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        seedRows();
    }

    private void seedRows() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base
                (id, name, embedding_model, collection_name, deleted)
                VALUES (1, 'Knowledge Base', 'embedding-a', 'collection-a', 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO t_ingestion_pipeline(id, name, deleted)
                VALUES ('pipeline-1', 'Default Pipeline', 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document
                (id, kb_id, doc_name, source_type, source_location, schedule_enabled,
                 schedule_cron, enabled, chunk_count, file_url, file_type, file_size,
                 chunk_strategy, process_mode, chunk_config, pipeline_id, status,
                 created_by, updated_by, create_time, update_time, deleted)
                VALUES
                (1, 1, 'Guide', 'file', null, 0, null, 1, 2,
                 'local://guide.pdf', 'pdf', 12, null, 'pipeline', null, 'pipeline-1',
                 'success', 'tester', 'tester', ?, ?, 0)
                """, now, now);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_chunk
                (id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                 token_count, enabled, created_by, updated_by, create_time, update_time, deleted)
                VALUES (10, 1, 1, 0, 'first', 'hash-0',
                        5, 5, 1, 'tester', 'tester', ?, ?, 0)
                """, now, now);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_chunk
                (id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                 token_count, enabled, created_by, updated_by, create_time, update_time, deleted)
                VALUES (11, 1, 1, 1, 'second', 'hash-1',
                        6, 6, 1, 'tester', 'tester', ?, ?, 0)
                """, now, now);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document_chunk_log
                (id, doc_id, status, process_mode, chunk_strategy, pipeline_id,
                 extract_duration, chunk_duration, embed_duration, persist_duration,
                 total_duration, chunk_count, error_message, start_time, end_time,
                 create_time, update_time)
                VALUES (100, 1, 'success', 'pipeline', null, 'pipeline-1',
                        0, 50, 0, 20, 100, 2, null, ?, ?, ?, ?)
                """, now, now, now, now);
    }
}
