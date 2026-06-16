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
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskUpdateValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcIngestionTaskRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcIngestionTaskRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ingestion-task;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcIngestionTaskRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldCreateUpdatePageAndListNodeLogs() {
        String taskId = adapter.createRunningTask(new IngestionTaskCreateValues(
                "pipeline-1",
                "file",
                "guide.pdf",
                "guide.pdf",
                3,
                Map.of("id", "pipeline-1", "version", 3, "nodes", List.of(Map.of("nodeId", "parser"))),
                "tester"));
        NodeLog log = NodeLog.builder()
                .nodeId("parser")
                .nodeType("parser")
                .message("parsed")
                .durationMs(15)
                .success(true)
                .build();
        IngestionTaskNodeValues node = new IngestionTaskNodeValues();
        node.setTaskId(taskId);
        node.setPipelineId("pipeline-1");
        node.setNodeId("parser");
        node.setNodeType("parser");
        node.setNodeOrder(1);
        node.setStatus("success");
        node.setDurationMs(15);
        node.setMessage("parsed");
        node.setOutput(Map.of("length", 10));

        adapter.updateTask(taskId, new IngestionTaskUpdateValues(
                "completed", 2, null, List.of(log), Map.of("fileName", "guide.pdf"), "tester"));
        adapter.replaceNodeLogs(taskId, List.of(node));

        IngestionTaskRecord record = adapter.findById(taskId).orElseThrow();
        IngestionTaskPage page = adapter.page(1, 10, "completed");
        List<IngestionTaskNodeRecord> nodes = adapter.listNodes(taskId);

        assertThat(record.getStatus()).isEqualTo("completed");
        assertThat(record.getPipelineVersion()).isEqualTo(3);
        assertThat(record.getPipelineSnapshot()).containsEntry("id", "pipeline-1");
        assertThat(record.getChunkCount()).isEqualTo(2);
        assertThat(record.getLogs()).hasSize(1);
        assertThat(record.getMetadata()).containsEntry("fileName", "guide.pdf");
        assertThat(page.records()).extracting(IngestionTaskRecord::getId).contains(taskId);
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getOutput()).containsEntry("length", 10);
    }

    @Test
    void shouldPersistNodeGovernanceEvidence() {
        String taskId = adapter.createRunningTask(new IngestionTaskCreateValues(
                "pipeline-1", "file", "broken.pdf", "broken.pdf", "tester"));
        IngestionTaskNodeValues node = new IngestionTaskNodeValues();
        node.setTaskId(taskId);
        node.setPipelineId("pipeline-1");
        node.setNodeId("parse");
        node.setNodeType("parse");
        node.setNodeOrder(2);
        node.setStatus("failed");
        node.setDurationMs(20);
        node.setInputSummary("source=file:broken.pdf bytes=128");
        node.setOutputSummary("chunks=0");
        node.setErrorCode("PARSE_FAILED");
        node.setErrorMessage("cannot parse pdf");
        node.setRetryCount(2);
        node.setDownstreamImpact("chunk/embed/vector-index skipped");

        adapter.replaceNodeLogs(taskId, List.of(node));

        List<IngestionTaskNodeRecord> nodes = adapter.listNodes(taskId);

        assertThat(nodes).singleElement()
                .satisfies(record -> {
                    assertThat(record.getInputSummary()).isEqualTo("source=file:broken.pdf bytes=128");
                    assertThat(record.getOutputSummary()).isEqualTo("chunks=0");
                    assertThat(record.getErrorCode()).isEqualTo("PARSE_FAILED");
                    assertThat(record.getRetryCount()).isEqualTo(2);
                    assertThat(record.getDownstreamImpact()).isEqualTo("chunk/embed/vector-index skipped");
                });
    }

    @Test
    void shouldAttachUnresolvedQuarantineCountToTaskRecords() {
        String taskId = adapter.createRunningTask(new IngestionTaskCreateValues(
                "pipeline-1", "file", "quarantine.pdf", "quarantine.pdf", "tester"));
        insertQuarantineItem("q-open-1", taskId, 0);
        insertQuarantineItem("q-open-2", taskId, 0);
        insertQuarantineItem("q-resolved", taskId, 1);
        insertQuarantineItem("q-other-task", "999999", 0);

        IngestionTaskRecord record = adapter.findById(taskId).orElseThrow();
        IngestionTaskPage page = adapter.page(1, 10, null);

        assertThat(record.getUnresolvedQuarantineCount()).isEqualTo(2);
        assertThat(record.isHasQuarantineItems()).isTrue();
        assertThat(page.records()).filteredOn(task -> task.getId().equals(taskId))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getUnresolvedQuarantineCount()).isEqualTo(2);
                    assertThat(task.isHasQuarantineItems()).isTrue();
                });
    }

    @Test
    void shouldIgnoreDeletedRows() {
        String taskId = adapter.createRunningTask(new IngestionTaskCreateValues(
                "pipeline-1", "file", "deleted.pdf", "deleted.pdf", "tester"));
        jdbcTemplate.update("UPDATE t_ingestion_task SET deleted = 1 WHERE id = ?", taskId);

        assertThat(adapter.findById(taskId)).isEmpty();
        assertThat(adapter.page(1, 10, null).records()).isEmpty();
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_quarantine_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_task_node");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_ingestion_task");
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_task (
                    id VARCHAR(64) PRIMARY KEY,
                    pipeline_id VARCHAR(64),
                    pipeline_version INTEGER NOT NULL DEFAULT 0,
                    pipeline_snapshot_json VARCHAR(4096),
                    source_type VARCHAR(32),
                    source_location VARCHAR(512),
                    source_file_name VARCHAR(256),
                    status VARCHAR(32),
                    chunk_count INTEGER,
                    error_message VARCHAR(512),
                    logs_json VARCHAR(2048),
                    metadata_json VARCHAR(2048),
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ingestion_task_node (
                    id VARCHAR(64) PRIMARY KEY,
                    task_id VARCHAR(64),
                    pipeline_id VARCHAR(64),
                    node_id VARCHAR(64),
                    node_type VARCHAR(64),
                    node_order INTEGER,
                    status VARCHAR(32),
                    duration_ms BIGINT,
                    input_summary VARCHAR(1000),
                    output_summary VARCHAR(1000),
                    error_code VARCHAR(128),
                    message VARCHAR(512),
                    error_message VARCHAR(512),
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    downstream_impact VARCHAR(1000),
                    output_json VARCHAR(2048),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_quarantine_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64),
                    job_id VARCHAR(64),
                    stage VARCHAR(32) NOT NULL,
                    reason_code VARCHAR(64),
                    reason_message VARCHAR(512),
                    source_snapshot VARCHAR(2048),
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    next_retry_time TIMESTAMP,
                    resolved SMALLINT NOT NULL DEFAULT 0,
                    resolved_by VARCHAR(64),
                    resolved_time TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
    }

    private void insertQuarantineItem(String id, String taskId, int resolved) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_quarantine_item(
                    id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                    source_snapshot, retry_count, resolved, create_time, update_time
                ) VALUES (?, 'tenant-1', 'kb-1', 'doc-1', ?, 'metadata_validator',
                    'METADATA_QUARANTINE', 'metadata rejected', '{}', 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, taskId, resolved);
    }
}
