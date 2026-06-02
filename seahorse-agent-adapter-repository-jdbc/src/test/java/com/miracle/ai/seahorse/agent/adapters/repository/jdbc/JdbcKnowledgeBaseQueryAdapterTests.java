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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, "collection-a"));
    }

    @Test
    void searchDocumentsShouldClampLimitAndJoinKnowledgeBaseName() {
        List<KnowledgeDocumentSummary> documents = adapter.searchDocuments("guide", 50);

        assertThat(documents).hasSize(20);
        assertThat(documents.get(0).id()).isEqualTo(24L);
        assertThat(documents.get(0).kbName()).isEqualTo("产品知识库");
        assertThat(documents.get(19).id()).isEqualTo(5L);
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
                VectorChunk.builder().chunkId("20").index(0).content("新的第一段").build(),
                VectorChunk.builder().chunkId("21").index(1).content("新的第二段").build()));

        List<KnowledgeChunkSummary> chunks = adapter.listChunksByDocId(1L);
        Integer chunkCount = new JdbcTemplate(dataSource())
                .queryForObject("SELECT chunk_count FROM t_knowledge_document WHERE id = ?", Integer.class, 1L);
        assertThat(chunks).extracting(KnowledgeChunkSummary::id).containsExactly(20L, 21L);
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
                        new KnowledgeDocumentProcessRef("ignored", "pipeline", "pipeline-1"),
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
                pipelineRepository.findById("pipeline-1").orElseThrow();

        assertThat(pipeline.getName()).isEqualTo("默认入库流水线");
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
                    id varchar(64) PRIMARY KEY,
                    name varchar(128),
                    collection_name varchar(128),
                    deleted int,
                    update_time timestamp
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id varchar(64) PRIMARY KEY,
                    kb_id varchar(64),
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
                    update_time timestamp
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id varchar(64) PRIMARY KEY,
                    kb_id varchar(64),
                    doc_id varchar(64),
                    chunk_index int,
                    content varchar(512),
                    content_hash varchar(128),
                    char_count int,
                    enabled int,
                    deleted int
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
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time) VALUES (?, ?, ?, ?, ?)",
                1L, "产品知识库", "collection-a", 0, baseTime);
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time) VALUES (?, ?, ?, ?, ?)",
                2L, "空集合", "", 0, baseTime.minusMinutes(1));
        jdbcTemplate.update("INSERT INTO t_knowledge_base(id, name, collection_name, deleted, update_time) VALUES (?, ?, ?, ?, ?)",
                3L, "已删除", "collection-deleted", 1, baseTime.minusMinutes(2));
        for (int index = 0; index < 25; index++) {
            jdbcTemplate.update(
                    """
                            INSERT INTO t_knowledge_document(
                                id, kb_id, doc_name, source_type, enabled, chunk_count, file_url, file_type,
                                file_size, process_mode, pipeline_id, status, created_by, updated_by,
                                deleted, create_time, update_time
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    Long.valueOf(index), 1L, "guide-" + index, "file", 1, 0, "", "pdf",
                    0L, "pipeline", null, "pending", "tester", "tester", 0,
                    baseTime.plusMinutes(index), baseTime.plusMinutes(index));
        }
        jdbcTemplate.update(
                """
                        INSERT INTO t_knowledge_document(
                            id, kb_id, doc_name, source_type, enabled, chunk_count, file_url, file_type,
                            file_size, process_mode, pipeline_id, status, created_by, updated_by,
                            deleted, create_time, update_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                100L, 1L, "guide-deleted", "file", 1, 0, "", "pdf",
                0L, "pipeline", null, "pending", "tester", "tester", 1,
                baseTime.plusDays(1), baseTime.plusDays(1));
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                11L, 1L, 1L, 1, "第二段", "", 3, 0, 0);
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                10L, 1L, 1L, 0, "第一段", "", 3, 1, 0);
        jdbcTemplate.update(
                "INSERT INTO t_knowledge_chunk(id, kb_id, doc_id, chunk_index, content, content_hash, char_count, enabled, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                12L, 1L, 1L, 2, "已删除", "", 3, 1, 1);
        jdbcTemplate.update("INSERT INTO t_ingestion_pipeline(id, name, description, deleted) VALUES (?, ?, ?, ?)",
                "pipeline-1", "默认入库流水线", "默认", 0);
        jdbcTemplate.update("""
                        INSERT INTO t_ingestion_pipeline_node(
                            id, pipeline_id, node_id, node_type, next_node_id, settings_json,
                            condition_json, deleted, create_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "node-1", "pipeline-1", "parser", "parser", "indexer",
                "{\"parserType\":\"tika\"}", null, 0, baseTime);
        jdbcTemplate.update("""
                        INSERT INTO t_ingestion_pipeline_node(
                            id, pipeline_id, node_id, node_type, next_node_id, settings_json,
                            condition_json, deleted, create_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "node-2", "pipeline-1", "indexer", "indexer", null,
                "{}", null, 0, baseTime.plusMinutes(1));
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:knowledge;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
