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

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
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
import java.util.Map;

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
        insertChunk(10L, 0, "existing content", 1);

        KnowledgeDocumentChunkContext context = adapter.findDocumentContext(1L).orElseThrow();
        KnowledgeChunkRecord created = adapter.create(1L,
                new CreateKnowledgeChunkValues(20L, "new content", null, "tester"));

        boolean updated = adapter.update(1L, 20L,
                new UpdateKnowledgeChunkValues("updated content", "tester"));
        boolean disabled = adapter.updateEnabled(1L, List.of(20L), false, "tester");
        KnowledgeChunkPage page = adapter.page(1L, 1, 10, false);
        boolean deleted = adapter.delete(1L, 20L);

        assertThat(context.collectionName()).isEqualTo("collection-a");
        assertThat(created.getChunkIndex()).isEqualTo(1);
        assertThat(updated).isTrue();
        assertThat(disabled).isTrue();
        assertThat(page.records()).extracting(KnowledgeChunkRecord::getId).contains(20L);
        assertThat(deleted).isTrue();
        assertThat(adapter.findChunk(1L, 20L)).isEmpty();
    }

    @Test
    void shouldFindChunksByIdsInChunkOrder() {
        insertChunk(11L, 1, "second chunk", 1);
        insertChunk(10L, 0, "first chunk", 1);

        List<KnowledgeChunkRecord> chunks = adapter.findChunksByIds(1L, List.of(11L, 10L));

        assertThat(chunks).extracting(KnowledgeChunkRecord::getId).containsExactly(10L, 11L);
    }

    @Test
    void shouldWriteChunkMetadataJsonWhenColumnExists() {
        adapter.replaceDocumentChunks(1L, 1L, List.of(
                VectorChunk.builder()
                        .chunkId("30")
                        .index(0)
                        .content("metadata chunk")
                        .metadata(Map.of("department", "FIN", "securityLevel", "internal"))
                        .build()));

        String metadataJson = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM t_knowledge_chunk WHERE id = 30", String.class);

        assertThat(metadataJson)
                .contains("\"department\":\"FIN\"")
                .contains("\"securityLevel\":\"internal\"");
    }

    private void insertChunk(Long id, int index, String content, int enabled) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_chunk
                (id, kb_id, doc_id, chunk_index, content, content_hash, char_count,
                 token_count, enabled, created_by, updated_by, create_time, update_time, deleted, tenant_id)
                VALUES (?, 1, 1, ?, ?, ?, ?, ?, ?, 'tester', 'tester', ?, ?, 0, 'default')
                """, id, index, content, id + "-hash", content.length(), content.length(), enabled, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_chunk");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_base");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(128),
                    embedding_model VARCHAR(128),
                    collection_name VARCHAR(128),
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id BIGINT PRIMARY KEY,
                    kb_id BIGINT,
                    doc_name VARCHAR(128),
                    status VARCHAR(32),
                    enabled INTEGER,
                    chunk_count INTEGER,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id BIGINT PRIMARY KEY,
                    kb_id BIGINT,
                    doc_id BIGINT,
                    chunk_index INTEGER,
                    content VARCHAR(512),
                    content_hash VARCHAR(128),
                    char_count INTEGER,
                    metadata_json VARCHAR(2048),
                    token_count INTEGER,
                    enabled INTEGER,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base
                (id, name, embedding_model, collection_name, deleted, tenant_id)
                VALUES (1, 'Knowledge Base', 'embed-a', 'collection-a', 0, 'default')
                """);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document
                (id, kb_id, doc_name, status, enabled, chunk_count, deleted, tenant_id)
                VALUES (1, 1, 'Document', 'success', 1, 0, 0, 'default')
                """);
    }
}
