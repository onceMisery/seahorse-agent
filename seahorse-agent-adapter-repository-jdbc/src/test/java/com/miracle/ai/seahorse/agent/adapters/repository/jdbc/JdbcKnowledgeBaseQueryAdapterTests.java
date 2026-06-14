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
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class JdbcKnowledgeBaseQueryAdapterTests {

    private JdbcKnowledgeBaseQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        resetSchema(jdbcTemplate);
        adapter = new JdbcKnowledgeBaseQueryAdapter(dataSource);
    }

    @Test
    void searchDocumentsShouldReturnEmptyWhenKeywordBlank() {
        assertThat(adapter.searchDocuments(" ", 10)).isEmpty();
    }

    @Test
    void listSearchableKnowledgeBasesShouldReturnEnabledCollections() {
        assertThat(adapter.listSearchableKnowledgeBases())
                .extracting("id", "collectionName")
                .containsExactly(tuple(1L, "collection-a"));
    }

    @Test
    void listSearchableKnowledgeBasesShouldFilterByEmbeddingModel() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        LocalDateTime now = LocalDateTime.of(2026, 5, 10, 11, 0);
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_base(id, name, collection_name, embedding_model, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, ?, 'default')",
                6L, "Mock Knowledge Base", "collection-mock", "mock", 0, now);
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')",
                60L, 6L, 60L, 0, "mock chunk", "", 10, 1, 0, now);

        assertThat(adapter.listSearchableKnowledgeBases("nomic-embed-text"))
                .extracting("id", "collectionName")
                .containsExactly(tuple(1L, "collection-a"));
        assertThat(adapter.listSearchableKnowledgeBases("mock"))
                .extracting("id", "collectionName")
                .containsExactly(tuple(6L, "collection-mock"));
    }

    @Test
    void searchDocumentsShouldClampLimitAndJoinKnowledgeBaseName() {
        List<KnowledgeDocumentSummary> documents = adapter.searchDocuments("guide", 50);

        assertThat(documents).hasSize(20);
        assertThat(documents.get(0).id()).isEqualTo(25L);
        assertThat(documents.get(0).kbName()).isEqualTo("Product Knowledge Base");
        assertThat(documents.get(19).id()).isEqualTo(6L);
    }

    @Test
    void listChunksByDocIdShouldOrderByChunkIndexAndMapEnabledFlag() {
        List<KnowledgeChunkSummary> chunks = adapter.listChunksByDocId(1L);

        assertThat(chunks).extracting(KnowledgeChunkSummary::id).containsExactly(10L, 11L);
        assertThat(chunks.get(0).enabled()).isTrue();
        assertThat(chunks.get(1).enabled()).isFalse();
    }

    @Test
    void replaceDocumentChunksShouldDeleteOldRowsInsertNewRowsAndUpdateDocumentCount() {
        JdbcKnowledgeChunkRepositoryAdapter chunkRepository = new JdbcKnowledgeChunkRepositoryAdapter(dataSource());

        chunkRepository.replaceDocumentChunks(1L, 1L, List.of(
                VectorChunk.builder().chunkId("100").index(0).content("new first chunk").build(),
                VectorChunk.builder().chunkId("101").index(1).content("new second chunk").build()));

        List<KnowledgeChunkSummary> chunks = adapter.listChunksByDocId(1L);
        Integer chunkCount = new JdbcTemplate(dataSource())
                .queryForObject("SELECT chunk_count FROM t_knowledge_document WHERE id = ?", Integer.class, 1L);
        assertThat(chunks).extracting(KnowledgeChunkSummary::id).containsExactly(100L, 101L);
        assertThat(chunks).allMatch(KnowledgeChunkSummary::enabled);
        assertThat(chunkCount).isEqualTo(2);
    }

    @Test
    void documentRepositoryShouldCreateAndMoveStatus() {
        JdbcKnowledgeDocumentRepositoryAdapter documentRepository =
                new JdbcKnowledgeDocumentRepositoryAdapter(dataSource());

        KnowledgeDocumentRecord document = documentRepository.createPendingDocument(
                new CreateKnowledgeDocumentCommand(
                        1L,
                        "policy.pdf",
                        new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 12L),
                        new KnowledgeDocumentProcessRef("ignored", "pipeline", "1"),
                        "tester"));

        assertThat(document.id()).isNotNull();
        assertThat(document.process().status()).isEqualTo("pending");
        assertThat(documentRepository.markRunning(document.id(), "tester")).isTrue();
        assertThat(documentRepository.markRunning(document.id(), "tester")).isFalse();

        documentRepository.markSuccess(document.id(), 3, "tester");

        KnowledgeDocumentRecord updated = documentRepository.findById(document.id()).orElseThrow();
        Integer chunkCount = new JdbcTemplate(dataSource())
                .queryForObject("SELECT chunk_count FROM t_knowledge_document WHERE id = ?", Integer.class,
                        document.id());
        assertThat(updated.process().status()).isEqualTo("success");
        assertThat(chunkCount).isEqualTo(3);
    }

    @Test
    void pipelineRepositoryShouldLoadPipelineDefinition() {
        JdbcPipelineDefinitionRepositoryAdapter pipelineRepository =
                new JdbcPipelineDefinitionRepositoryAdapter(dataSource(), new ObjectMapper());

        com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition pipeline =
                pipelineRepository.findById("1").orElseThrow();

        assertThat(pipeline.getName()).isEqualTo("Default ingestion pipeline");
        assertThat(pipeline.getNodes()).hasSize(2);
        assertThat(pipeline.getNodes().get(0).getNodeId()).isEqualTo("parser");
        assertThat(pipeline.getNodes().get(0).getSettings().get("parserType").asText()).isEqualTo("tika");
        assertThat(pipeline.getNodes().get(1).getNodeType()).isEqualTo("indexer");
    }

    private void resetSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_chunk");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_base");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_pipeline_node");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_pipeline");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id BIGINT PRIMARY KEY,
                    name varchar(128),
                    embedding_model varchar(128) not null default 'nomic-embed-text',
                    collection_name varchar(128),
                    deleted int,
                    update_time timestamp,
                    tenant_id varchar(64) not null default 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id BIGINT PRIMARY KEY,
                    kb_id BIGINT,
                    doc_name varchar(128),
                    source_type varchar(32),
                    enabled int,
                    chunk_count int,
                    file_url varchar(512),
                    file_type varchar(64),
                    file_size bigint,
                    process_mode varchar(32),
                    pipeline_id varchar(64),
                    status varchar(32),
                    created_by varchar(64),
                    updated_by varchar(64),
                    deleted int,
                    create_time timestamp,
                    update_time timestamp,
                    tenant_id varchar(64) not null default 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id BIGINT PRIMARY KEY,
                    kb_id BIGINT,
                    doc_id BIGINT,
                    chunk_index int,
                    content varchar(512),
                    content_hash varchar(128),
                    char_count int,
                    enabled int,
                    deleted int,
                    update_time timestamp,
                    tenant_id varchar(64) not null default 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_pipeline (
                    id varchar(64) PRIMARY KEY,
                    name varchar(128),
                    description varchar(256),
                    deleted int
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_pipeline_node (
                    id varchar(64) PRIMARY KEY,
                    pipeline_id varchar(64),
                    node_id varchar(64),
                    node_type varchar(64),
                    next_node_id varchar(64),
                    settings_json varchar(512),
                    condition_json varchar(512),
                    deleted int,
                    create_time timestamp
                )
                """);
        seedRows(jdbcTemplate);
    }

    private void seedRows(JdbcTemplate jdbcTemplate) {
        LocalDateTime baseTime = LocalDateTime.of(2026, 5, 10, 10, 0);
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, 'default')",
                1L, "Product Knowledge Base", "collection-a", 0, baseTime);
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, 'default')",
                2L, "Empty Collection", "", 0, baseTime.minusMinutes(1));
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, 'default')",
                3L, "Deleted Knowledge Base", "collection-deleted", 1, baseTime.minusMinutes(2));
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, 'default')",
                4L, "Collection Without Chunks", "collection-empty", 0, baseTime.minusMinutes(3));
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, 'default')",
                5L, "Collection With Disabled Chunks", "collection-disabled", 0, baseTime.minusMinutes(4));
        for (int index = 0; index < 25; index++) {
            jdbcTemplate.update(
                    """
                            INSERT INTO t_knowledge_document(
                                id, kb_id, doc_name, source_type, enabled, chunk_count, file_url, file_type,
                                file_size, process_mode, pipeline_id, status, created_by, updated_by,
                                deleted, create_time, update_time, tenant_id
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')
                            """,
                    index + 1L, 1L, "guide-" + index, "file", 1, 0, "", "pdf",
                    0L, "pipeline", null, "pending", "tester", "tester", 0,
                    baseTime.plusMinutes(index), baseTime.plusMinutes(index));
        }
        jdbcTemplate.update(
                """
                        INSERT INTO t_knowledge_document(
                            id, kb_id, doc_name, source_type, enabled, chunk_count, file_url, file_type,
                            file_size, process_mode, pipeline_id, status, created_by, updated_by,
                            deleted, create_time, update_time, tenant_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')
                        """,
                99L, 1L, "guide-deleted", "file", 1, 0, "", "pdf",
                0L, "pipeline", null, "pending", "tester", "tester", 1,
                baseTime.plusDays(1), baseTime.plusDays(1));
        jdbcTemplate.update(
                """
                        INSERT INTO t_knowledge_document(
                            id, kb_id, doc_name, source_type, enabled, chunk_count, file_url, file_type,
                            file_size, process_mode, pipeline_id, status, created_by, updated_by,
                            deleted, create_time, update_time, tenant_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')
                        """,
                50L, 5L, "disabled-only", "file", 1, 1, "", "pdf",
                0L, "pipeline", null, "pending", "tester", "tester", 0,
                baseTime.plusDays(2), baseTime.plusDays(2));
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')",
                11L, 1L, 1L, 1, "second chunk", "", 12, 0, 0, baseTime.plusMinutes(1));
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')",
                10L, 1L, 1L, 0, "first chunk", "", 11, 1, 0, baseTime.plusMinutes(2));
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')",
                12L, 1L, 1L, 2, "deleted chunk", "", 13, 1, 1, baseTime.plusMinutes(3));
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted, update_time, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'default')",
                13L, 5L, 50L, 0, "disabled chunk", "", 14, 0, 0, baseTime.plusMinutes(4));
        jdbcTemplate.update("INSERT INTO t_ingestion_pipeline(id, name, description, deleted) VALUES (?, ?, ?, ?)",
                "1", "Default ingestion pipeline", "Default", 0);
        jdbcTemplate.update("""
                        INSERT INTO t_ingestion_pipeline_node(
                            id, pipeline_id, node_id, node_type, next_node_id, settings_json,
                            condition_json, deleted, create_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "node-1", "1", "parser", "parser", "indexer",
                "{\"parserType\":\"tika\"}", null, 0, baseTime);
        jdbcTemplate.update("""
                        INSERT INTO t_ingestion_pipeline_node(
                            id, pipeline_id, node_id, node_type, next_node_id, settings_json,
                            condition_json, deleted, create_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "node-2", "1", "indexer", "indexer", null,
                "{}", null, 0, baseTime.plusMinutes(1));
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
