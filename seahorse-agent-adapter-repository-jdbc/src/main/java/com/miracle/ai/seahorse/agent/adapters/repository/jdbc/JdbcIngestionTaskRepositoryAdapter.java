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
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskUpdateValues;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的入库任务仓储适配器。
 */
public class JdbcIngestionTaskRepositoryAdapter implements IngestionTaskRepositoryPort {

    private static final TypeReference<List<NodeLog>> NODE_LOG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String STATUS_RUNNING = "running";
    private static final String SQL_INSERT_TASK = """
            INSERT INTO t_ingestion_task
            (id, pipeline_id, pipeline_version, pipeline_snapshot_json, source_type, source_location,
             source_file_name, status, chunk_count, error_message, logs_json, metadata_json, started_at,
             completed_at, created_by, updated_by, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, NULL, NULL, CURRENT_TIMESTAMP, NULL, ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """;
    private static final String SQL_UPDATE_TASK = """
            UPDATE t_ingestion_task
            SET status = ?, chunk_count = ?, error_message = ?, logs_json = ?, metadata_json = ?,
                completed_at = CURRENT_TIMESTAMP, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String TASK_SELECT_COLUMNS = """
            t.id, t.pipeline_id, t.source_type, t.source_location, t.source_file_name, t.status, t.chunk_count,
            t.pipeline_version, t.pipeline_snapshot_json, t.error_message, t.logs_json, t.metadata_json,
            t.started_at, t.completed_at, t.created_by, t.create_time, t.update_time,
            (
                SELECT COUNT(1)
                FROM t_metadata_quarantine_item q
                WHERE q.job_id = CAST(t.id AS VARCHAR) AND q.resolved = 0
            ) AS unresolved_quarantine_count
            """;
    private static final String SQL_FIND_TASK = """
            SELECT
            """ + TASK_SELECT_COLUMNS + """
            FROM t_ingestion_task t
            WHERE t.id = ? AND t.deleted = 0
            """;
    private static final String SQL_COUNT_PAGE = """
            SELECT COUNT(1)
            FROM t_ingestion_task t
            WHERE t.deleted = 0
            """;
    private static final String SQL_PAGE = """
            SELECT
            """ + TASK_SELECT_COLUMNS + """
            FROM t_ingestion_task t
            WHERE t.deleted = 0
            """;
    private static final String SQL_DELETE_NODES =
            "UPDATE t_ingestion_task_node SET deleted = 1, update_time = CURRENT_TIMESTAMP WHERE task_id = ?";
    private static final String SQL_INSERT_NODE = """
            INSERT INTO t_ingestion_task_node
            (id, task_id, pipeline_id, node_id, node_type, node_order, status, duration_ms,
             input_summary, output_summary, error_code, message, error_message, retry_count,
             downstream_impact, output_json, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """;
    private static final String SQL_LIST_NODES = """
            SELECT id, task_id, pipeline_id, node_id, node_type, node_order, status, duration_ms,
                   input_summary, output_summary, error_code, message, error_message, retry_count,
                   downstream_impact, output_json, create_time, update_time
            FROM t_ingestion_task_node
            WHERE task_id = ? AND deleted = 0
            ORDER BY node_order ASC, id ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcIngestionTaskRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public String createRunningTask(IngestionTaskCreateValues values) {
        IngestionTaskCreateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        String taskId = SnowflakeIds.nextIdString();
        String operator = Objects.requireNonNullElse(safeValues.operator(), "");
        jdbcTemplate.update(SQL_INSERT_TASK,
                toLong(taskId),
                requireText(safeValues.pipelineId(), "pipelineId"),
                safeValues.pipelineVersion(),
                toJson(safeValues.pipelineSnapshot()),
                blankToNull(safeValues.sourceType()),
                blankToNull(safeValues.sourceLocation()),
                blankToNull(safeValues.sourceFileName()),
                STATUS_RUNNING,
                operator,
                operator);
        return taskId;
    }

    @Override
    public void updateTask(String taskId, IngestionTaskUpdateValues values) {
        IngestionTaskUpdateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        jdbcTemplate.update(SQL_UPDATE_TASK,
                requireText(safeValues.status(), "status"),
                safeValues.chunkCount(),
                blankToNull(safeValues.errorMessage()),
                toJson(safeValues.logs()),
                toJson(safeValues.metadata()),
                Objects.requireNonNullElse(safeValues.operator(), ""),
                toLong(requireText(taskId, "taskId")));
    }

    @Override
    public void replaceNodeLogs(String taskId, List<IngestionTaskNodeValues> nodes) {
        String safeTaskId = requireText(taskId, "taskId");
        jdbcTemplate.update(SQL_DELETE_NODES, toLong(safeTaskId));
        for (IngestionTaskNodeValues node : Objects.requireNonNullElse(nodes, List.<IngestionTaskNodeValues>of())) {
            insertNode(node);
        }
    }

    @Override
    public Optional<IngestionTaskRecord> findById(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        Long safeTaskId = parseLong(taskId);
        if (safeTaskId == null) {
            return Optional.empty();
        }
        List<IngestionTaskRecord> records = jdbcTemplate.query(SQL_FIND_TASK, this::toTaskRecord, safeTaskId);
        return records.stream().findFirst();
    }

    @Override
    public List<IngestionTaskNodeRecord> listNodes(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        Long safeTaskId = parseLong(taskId);
        if (safeTaskId == null) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_NODES, this::toNodeRecord, safeTaskId);
    }

    @Override
    public IngestionTaskPage page(long current, long size, String status) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = clampSize(size);
        QueryParts queryParts = buildStatusQuery(status);
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE + queryParts.where(), Long.class,
                queryParts.args().toArray());
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(safeSize);
        args.add((safeCurrent - 1) * safeSize);
        List<IngestionTaskRecord> records = jdbcTemplate.query(
                SQL_PAGE + queryParts.where() + " ORDER BY t.create_time DESC LIMIT ? OFFSET ?",
                this::toTaskRecord,
                args.toArray());
        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return new IngestionTaskPage(records, safeTotal, safeSize, safeCurrent, pages);
    }

    private void insertNode(IngestionTaskNodeValues node) {
        IngestionTaskNodeValues safeNode = Objects.requireNonNull(node, "node must not be null");
        jdbcTemplate.update(SQL_INSERT_NODE,
                toLong(SnowflakeIds.nextIdString()),
                toLong(requireText(safeNode.getTaskId(), "taskId")),
                requireText(safeNode.getPipelineId(), "pipelineId"),
                blankToNull(safeNode.getNodeId()),
                blankToNull(safeNode.getNodeType()),
                safeNode.getNodeOrder(),
                blankToNull(safeNode.getStatus()),
                safeNode.getDurationMs(),
                blankToNull(safeNode.getInputSummary()),
                blankToNull(safeNode.getOutputSummary()),
                blankToNull(safeNode.getErrorCode()),
                blankToNull(safeNode.getMessage()),
                blankToNull(safeNode.getErrorMessage()),
                safeNode.getRetryCount(),
                blankToNull(safeNode.getDownstreamImpact()),
                toJson(safeNode.getOutput()));
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private long toLong(String value) {
        Long parsed = parseLong(value);
        if (parsed == null) {
            throw new IllegalArgumentException("numeric id is required");
        }
        return parsed;
    }

    private IngestionTaskRecord toTaskRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        IngestionTaskRecord record = new IngestionTaskRecord();
        record.setId(resultSet.getString("id"));
        record.setPipelineId(resultSet.getString("pipeline_id"));
        record.setPipelineVersion(resultSet.getInt("pipeline_version"));
        record.setPipelineSnapshot(parseMap(resultSet.getString("pipeline_snapshot_json")));
        record.setSourceType(resultSet.getString("source_type"));
        record.setSourceLocation(resultSet.getString("source_location"));
        record.setSourceFileName(resultSet.getString("source_file_name"));
        record.setStatus(resultSet.getString("status"));
        record.setChunkCount(resultSet.getInt("chunk_count"));
        record.setErrorMessage(resultSet.getString("error_message"));
        record.setLogs(parseLogs(resultSet.getString("logs_json")));
        record.setMetadata(parseMap(resultSet.getString("metadata_json")));
        record.setUnresolvedQuarantineCount(resultSet.getInt("unresolved_quarantine_count"));
        record.setStartedAt(toInstant(resultSet.getTimestamp("started_at")));
        record.setCompletedAt(toInstant(resultSet.getTimestamp("completed_at")));
        record.setCreatedBy(resultSet.getString("created_by"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        return record;
    }

    private IngestionTaskNodeRecord toNodeRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        IngestionTaskNodeRecord record = new IngestionTaskNodeRecord();
        record.setId(resultSet.getString("id"));
        record.setTaskId(resultSet.getString("task_id"));
        record.setPipelineId(resultSet.getString("pipeline_id"));
        record.setNodeId(resultSet.getString("node_id"));
        record.setNodeType(resultSet.getString("node_type"));
        record.setNodeOrder(resultSet.getInt("node_order"));
        record.setStatus(resultSet.getString("status"));
        record.setDurationMs(resultSet.getLong("duration_ms"));
        record.setInputSummary(resultSet.getString("input_summary"));
        record.setOutputSummary(resultSet.getString("output_summary"));
        record.setErrorCode(resultSet.getString("error_code"));
        record.setMessage(resultSet.getString("message"));
        record.setErrorMessage(resultSet.getString("error_message"));
        record.setRetryCount(resultSet.getInt("retry_count"));
        record.setDownstreamImpact(resultSet.getString("downstream_impact"));
        record.setOutput(parseMap(resultSet.getString("output_json")));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        return record;
    }

    private QueryParts buildStatusQuery(String status) {
        if (status == null || status.isBlank()) {
            return new QueryParts("", List.of());
        }
        return new QueryParts(" AND t.status = ?", List.of(status.trim()));
    }

    private List<NodeLog> parseLogs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, NODE_LOG_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("ingestion task json serialization failed", ex);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long clampSize(long size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QueryParts(String where, List<Object> args) {
    }
}
