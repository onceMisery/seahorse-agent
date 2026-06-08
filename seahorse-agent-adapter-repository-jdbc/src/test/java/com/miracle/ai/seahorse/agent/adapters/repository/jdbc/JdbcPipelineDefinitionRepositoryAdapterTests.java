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
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelinePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineNodePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPipelineDefinitionRepositoryAdapterTests {

    private ObjectMapper objectMapper;
    private JdbcTemplate jdbcTemplate;
    private JdbcPipelineDefinitionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ingestion-pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        objectMapper = new ObjectMapper();
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcPipelineDefinitionRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldCreateQueryPageUpdateDeleteAndLoadDefinition() {
        IngestionPipelinePayload createPayload = new IngestionPipelinePayload(
                "Native Pipeline",
                "Created by seahorse",
                List.of(node("1", "parser", "2", "tika")),
                "tester");

        IngestionPipelineRecord created = adapter.create(createPayload);
        IngestionPipelineRecord queried = adapter.findRecordById(created.getId()).orElseThrow();
        IngestionPipelinePage page = adapter.page(1, 10, "Native");
        PipelineDefinition definition = adapter.findById(created.getId()).orElseThrow();

        assertThat(created.getId()).isNotBlank();
        assertThat(queried.getName()).isEqualTo("Native Pipeline");
        assertThat(queried.getNodes()).hasSize(1);
        assertThat(page.records()).extracting(IngestionPipelineRecord::getId).contains(created.getId());
        assertThat(definition.getNodes()).extracting("nodeId").containsExactly("1");

        IngestionPipelinePayload updatePayload = new IngestionPipelinePayload(
                "Updated Pipeline",
                "Updated by seahorse",
                List.of(node("3", "fetcher", null, "http")),
                "tester");
        boolean updated = adapter.update(created.getId(), updatePayload);
        IngestionPipelineRecord afterUpdate = adapter.findRecordById(created.getId()).orElseThrow();
        Integer activeNodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_ingestion_pipeline_node WHERE pipeline_id = ? AND deleted = 0",
                Integer.class,
                created.getId());

        assertThat(updated).isTrue();
        assertThat(afterUpdate.getName()).isEqualTo("Updated Pipeline");
        assertThat(afterUpdate.getNodes()).extracting(IngestionPipelineNodePayload::nodeId)
                .containsExactly("3");
        assertThat(activeNodes).isEqualTo(1);

        boolean deleted = adapter.delete(created.getId(), "tester");

        assertThat(deleted).isTrue();
        assertThat(adapter.findRecordById(created.getId())).isEmpty();
        assertThat(adapter.findById(created.getId())).isEmpty();
    }

    @Test
    void shouldKeepExistingPipelineDefinitionContract() {
        PipelineDefinition definition = adapter.findById("1").orElseThrow();

        assertThat(definition.getName()).isEqualTo("Default Pipeline");
        assertThat(definition.getNodes()).hasSize(2);
        assertThat(definition.getNodes().get(0).getSettings().get("parserType").asText())
                .isEqualTo("tika");
    }

    @Test
    void shouldCastNodeJsonFieldsForPostgresJsonbColumns() throws Exception {
        java.lang.reflect.Field insertNodeSql =
                JdbcPipelineDefinitionRepositoryAdapter.class.getDeclaredField("SQL_INSERT_NODE");
        insertNodeSql.setAccessible(true);

        assertThat((String) insertNodeSql.get(null))
                .contains("CAST(? AS JSONB), CAST(? AS JSONB)");
    }

    private IngestionPipelineNodePayload node(String nodeId, String type, String nextNodeId, String provider) {
        return new IngestionPipelineNodePayload(
                nodeId,
                type,
                nextNodeId,
                objectMapper.createObjectNode().put("provider", provider),
                null);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_pipeline_node");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_pipeline");
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_pipeline (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(128),
                    description VARCHAR(256),
                    created_by BIGINT,
                    updated_by BIGINT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_pipeline_node (
                    id BIGINT PRIMARY KEY,
                    pipeline_id BIGINT,
                    node_id BIGINT,
                    node_type VARCHAR(64),
                    next_node_id BIGINT,
                    settings_json VARCHAR(512),
                    condition_json VARCHAR(512),
                    created_by BIGINT,
                    updated_by BIGINT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        seedRows();
    }

    private void seedRows() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_ingestion_pipeline
                (id, name, description, created_by, updated_by, create_time, update_time, deleted)
                VALUES (1, 'Default Pipeline', 'Default', 0, 0, ?, ?, 0)
                """, now, now);
        jdbcTemplate.update("""
                INSERT INTO t_ingestion_pipeline_node
                (id, pipeline_id, node_id, node_type, next_node_id, settings_json, condition_json,
                 created_by, updated_by, create_time, update_time, deleted)
                VALUES (11, 1, 1, 'parser', 2,
                        '{"parserType":"tika"}', null, 0, 0, ?, ?, 0)
                """, now, now);
        jdbcTemplate.update("""
                INSERT INTO t_ingestion_pipeline_node
                (id, pipeline_id, node_id, node_type, next_node_id, settings_json, condition_json,
                 created_by, updated_by, create_time, update_time, deleted)
                VALUES (12, 1, 2, 'indexer', null,
                        '{}', null, 0, 0, ?, ?, 0)
                """, now, now);
    }
}
