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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeChunkValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.UpdateKnowledgeChunkValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcKnowledgeChunkRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcKnowledgeChunkRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:knowledge-chunk;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcKnowledgeChunkRepositoryAdapter(dataSource);
    }

    @Test
    void shouldCreatePageUpdateEnableAndDeleteChunk() {
        insertChunk("chunk-0", 0, "已有内容", 1);

        KnowledgeDocumentChunkContext context = adapter.findDocumentContext("doc-1").orElseThrow();
        KnowledgeChunkRecord created = adapter.create("doc-1",
                new CreateKnowledgeChunkValues("chunk-2", "新增内容", null, "tester"));

        boolean updated = adapter.update("doc-1", "chunk-2",
                new UpdateKnowledgeChunkValues("更新内容", "tester"));
        boolean disabled = adapter.updateEnabled("doc-1", List.of("chunk-2"), false, "tester");
        KnowledgeChunkPage page = adapter.page("doc-1", 1, 10, false);
        boolean deleted = adapter.delete("doc-1", "chunk-2");

        assertThat(context.collectionName()).isEqualTo("collection-a");
        assertThat(created.getChunkIndex()).isEqualTo(1);
        assertThat(updated).isTrue();
        assertThat(disabled).isTrue();
        assertThat(page.records()).extracting(KnowledgeChunkRecord::getId).contains("chunk-2");
        assertThat(deleted).isTrue();
        assertThat(adapter.findChunk("doc-1", "chunk-2")).isEmpty();
    }

    @Test
    void shouldFindChunksByIdsInChunkOrder() {
        insertChunk("chunk-1", 1, "第二段", 1);
        insertChunk("chunk-0", 0, "第一段", 1);

        List<KnowledgeChunkRecord> chunks = adapter.findChunksByIds("doc-1", List.of("chunk-1", "chunk-0"));

        assertThat(chunks).extracting(KnowledgeChunkRecord::getId).containsExactly("chunk-0", "chunk-1");
    }

    private void insertChunk(String id, int index, String content, int enabled) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_chunk
                (id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                 token_count, enabled, created_by, updated_by, create_time, update_time, deleted)
                VALUES (?, 'kb-1', 'doc-1', ?, ?, ?, ?, ?, ?, 'tester', 'tester', ?, ?, 0)
                """, id, index, content, id + "-hash", content.length(), content.length(), enabled, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_chunk");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_base");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(128),
                    embedding_model VARCHAR(128),
                    collection_name VARCHAR(128),
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id VARCHAR(32) PRIMARY KEY,
                    kb_id VARCHAR(32),
                    doc_name VARCHAR(128),
                    status VARCHAR(32),
                    enabled INTEGER,
                    chunk_count INTEGER,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(32) PRIMARY KEY,
                    kb_id VARCHAR(32),
                    doc_id VARCHAR(32),
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
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base
                (id, name, embedding_model, collection_name, deleted)
                VALUES ('kb-1', '知识库', 'embed-a', 'collection-a', 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document
                (id, kb_id, doc_name, status, enabled, chunk_count, deleted)
                VALUES ('doc-1', 'kb-1', '文档', 'success', 1, 0, 0)
                """);
    }
}
