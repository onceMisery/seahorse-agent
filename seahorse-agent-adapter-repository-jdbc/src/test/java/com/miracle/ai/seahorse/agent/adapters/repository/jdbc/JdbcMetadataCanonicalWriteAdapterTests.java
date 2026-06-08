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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMetadataCanonicalWriteAdapterTests {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataCanonicalWriteRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-canonical-write;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        adapter = new JdbcMetadataCanonicalWriteRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldWriteCanonicalMetadataToKnowledgeDocumentJson() throws Exception {
        createDocumentTable(true);
        createChunkTable(true);
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, deleted) VALUES ('doc-1', 0)");
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_chunk(id, doc_id, metadata_json, deleted)
                VALUES ('chunk-1', 'doc-1', '{"source_type":"file","department":"OLD"}', 0)
                """);

        adapter.writeDocumentMetadata("doc-1", Map.of(
                "department", "FIN",
                "securityLevel", "internal"));

        String metadataJson = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM t_knowledge_document WHERE id = 'doc-1'", String.class);
        Map<String, Object> metadata = objectMapper.readValue(metadataJson, MAP_TYPE);
        assertThat(metadata)
                .containsEntry("department", "FIN")
                .containsEntry("securityLevel", "internal");
        String chunkMetadataJson = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM t_knowledge_chunk WHERE id = 'chunk-1'", String.class);
        Map<String, Object> chunkMetadata = objectMapper.readValue(chunkMetadataJson, MAP_TYPE);
        assertThat(chunkMetadata)
                .containsEntry("source_type", "file")
                .containsEntry("department", "FIN")
                .containsEntry("securityLevel", "internal");
    }

    @Test
    void shouldIgnoreCanonicalWriteWhenDocumentMetadataColumnMissing() {
        createDocumentTable(false);
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, deleted) VALUES ('doc-1', 0)");

        adapter.writeDocumentMetadata("doc-1", Map.of("department", "FIN"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_document WHERE id = 'doc-1'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private void createDocumentTable(boolean withMetadataJson) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        String metadataColumn = withMetadataJson ? "metadata_json VARCHAR(2048)," : "";
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id VARCHAR(64) PRIMARY KEY,
                    %s
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """.formatted(metadataColumn));
    }

    private void createChunkTable(boolean withMetadataJson) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_chunk");
        String metadataColumn = withMetadataJson ? "metadata_json VARCHAR(2048)," : "";
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(64) PRIMARY KEY,
                    doc_id VARCHAR(64),
                    %s
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """.formatted(metadataColumn));
    }
}
