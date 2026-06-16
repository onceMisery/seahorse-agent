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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelinePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineNodePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的入库流水线定义仓储适配器。
 *
 * <p>该适配器读取既有 t_ingestion_pipeline 与 t_ingestion_pipeline_node 表，并映射为
 * seahorse-agent 自有 PipelineDefinition。
 */
public class JdbcPipelineDefinitionRepositoryAdapter implements PipelineDefinitionRepositoryPort,
        IngestionPipelineRepositoryPort {

    private static final String SQL_FIND_PIPELINE = """
            SELECT p.id AS pipeline_id, p.name, p.description, p.version,
                   n.node_id, n.node_type, n.next_node_id, n.settings_json, n.condition_json
            FROM t_ingestion_pipeline p
            LEFT JOIN t_ingestion_pipeline_node n
              ON n.pipeline_id = p.id AND n.deleted = 0
            WHERE p.id = CAST(? AS BIGINT) AND p.deleted = 0
            ORDER BY n.create_time ASC
            """;
    private static final String SQL_INSERT_PIPELINE = """
            INSERT INTO t_ingestion_pipeline
            (id, name, description, version, created_by, updated_by, create_time, update_time, deleted)
            VALUES (?, ?, ?, 1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """;
    private static final String SQL_UPDATE_PIPELINE = """
            UPDATE t_ingestion_pipeline
            SET name = ?, description = ?, version = version + 1, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_PIPELINE = """
            UPDATE t_ingestion_pipeline
            SET deleted = 1, updated_by = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_PIPELINE_NODES =
            "UPDATE t_ingestion_pipeline_node SET deleted = 1, updated_by = ?, update_time = CURRENT_TIMESTAMP WHERE pipeline_id = ?";
    private static final String SQL_INSERT_NODE = """
            INSERT INTO t_ingestion_pipeline_node
            (id, pipeline_id, node_id, node_type, next_node_id, settings_json, condition_json,
             created_by, updated_by, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """;
    private static final String SQL_FIND_RECORD = """
            SELECT p.id AS pipeline_id, p.name, p.description, p.version, p.created_by, p.create_time, p.update_time,
                   n.id AS node_pk, n.node_id, n.node_type, n.next_node_id, n.settings_json, n.condition_json
            FROM t_ingestion_pipeline p
            LEFT JOIN t_ingestion_pipeline_node n
              ON n.pipeline_id = p.id AND n.deleted = 0
            WHERE p.id = ? AND p.deleted = 0
            ORDER BY n.create_time ASC
            """;
    private static final String SQL_COUNT_PAGE = """
            SELECT COUNT(1)
            FROM t_ingestion_pipeline
            WHERE deleted = 0
            """;
    private static final String SQL_PAGE = """
            SELECT id AS pipeline_id, name, description, version, created_by, create_time, update_time
            FROM t_ingestion_pipeline
            WHERE deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPipelineDefinitionRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<PipelineDefinition> findById(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Optional.empty();
        }
        List<PipelineRow> rows = jdbcTemplate.query(SQL_FIND_PIPELINE, this::toPipelineRow,
                numericId(pipelineId, "pipelineId"));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toPipeline(rows));
    }

    @Override
    public IngestionPipelineRecord create(IngestionPipelinePayload payload) {
        IngestionPipelinePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        long pipelineId = SnowflakeIds.nextId();
        String operator = Objects.requireNonNullElse(safePayload.operator(), "");
        jdbcTemplate.update(SQL_INSERT_PIPELINE, pipelineId, requireText(safePayload.name(), "name"),
                Objects.requireNonNullElse(safePayload.description(), ""), operatorId(operator), operatorId(operator));
        String pipelineIdText = Long.toString(pipelineId);
        replaceNodes(pipelineIdText, safePayload.nodes(), operator);
        return queryRecordById(pipelineIdText)
                .orElseThrow(() -> new IllegalStateException("pipeline created but invisible: " + pipelineIdText));
    }

    @Override
    public Optional<IngestionPipelineRecord> findRecordById(String pipelineId) {
        return queryRecordById(pipelineId);
    }

    @Override
    public IngestionPipelinePage page(long current, long size, String keyword) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = clampSize(size);
        QueryParts queryParts = buildPageQuery(keyword);
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_PAGE + queryParts.where(), Long.class,
                queryParts.args().toArray());
        List<Object> args = new ArrayList<>(queryParts.args());
        args.add(safeSize);
        args.add((safeCurrent - 1) * safeSize);
        List<IngestionPipelineRecord> records = jdbcTemplate.query(
                SQL_PAGE + queryParts.where() + " ORDER BY update_time DESC LIMIT ? OFFSET ?",
                this::toPipelineRecordWithoutNodes,
                args.toArray());
        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safeSize - 1) / safeSize;
        return new IngestionPipelinePage(records, safeTotal, safeSize, safeCurrent, pages);
    }

    @Override
    public boolean update(String pipelineId, IngestionPipelinePayload payload) {
        IngestionPipelinePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String safePipelineId = requireText(pipelineId, "pipelineId");
        String operator = Objects.requireNonNullElse(safePayload.operator(), "");
        int updated = jdbcTemplate.update(SQL_UPDATE_PIPELINE,
                requireText(safePayload.name(), "name"),
                Objects.requireNonNullElse(safePayload.description(), ""),
                operatorId(operator),
                numericId(safePipelineId, "pipelineId"));
        if (updated > 0) {
            replaceNodes(safePipelineId, safePayload.nodes(), operator);
        }
        return updated > 0;
    }

    @Override
    public boolean delete(String pipelineId, String operator) {
        String safePipelineId = requireText(pipelineId, "pipelineId");
        long safeOperator = operatorId(operator);
        long safePipelinePk = numericId(safePipelineId, "pipelineId");
        int updated = jdbcTemplate.update(SQL_DELETE_PIPELINE, safeOperator, safePipelinePk);
        if (updated > 0) {
            jdbcTemplate.update(SQL_DELETE_PIPELINE_NODES, safeOperator, safePipelinePk);
        }
        return updated > 0;
    }

    private Optional<IngestionPipelineRecord> queryRecordById(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            return Optional.empty();
        }
        List<PipelineRecordRow> rows = jdbcTemplate.query(SQL_FIND_RECORD, this::toPipelineRecordRow,
                numericId(pipelineId, "pipelineId"));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toPipelineRecord(rows));
    }

    private PipelineDefinition toPipeline(List<PipelineRow> rows) {
        PipelineRow first = rows.get(0);
        return PipelineDefinition.builder()
                .id(first.pipelineId)
                .name(first.name)
                .description(first.description)
                .version(first.version)
                .nodes(toNodeConfigs(rows))
                .build();
    }

    private IngestionPipelineRecord toPipelineRecord(List<PipelineRecordRow> rows) {
        PipelineRecordRow first = rows.get(0);
        IngestionPipelineRecord record = new IngestionPipelineRecord();
        record.setId(first.pipelineId);
        record.setName(first.name);
        record.setDescription(first.description);
        record.setVersion(first.version);
        record.setCreatedBy(first.createdBy);
        record.setCreateTime(first.createTime);
        record.setUpdateTime(first.updateTime);
        record.setNodes(toNodePayloads(rows));
        return record;
    }

    private List<IngestionPipelineNodePayload> toNodePayloads(List<PipelineRecordRow> rows) {
        List<IngestionPipelineNodePayload> nodes = new ArrayList<>();
        for (PipelineRecordRow row : rows) {
            if (row.nodeId == null || row.nodeId.isBlank()) {
                continue;
            }
            nodes.add(new IngestionPipelineNodePayload(
                    row.nodeId,
                    row.nodeType,
                    row.nextNodeId,
                    parseJson(row.settingsJson),
                    parseJson(row.conditionJson)));
        }
        return nodes;
    }

    private List<NodeConfig> toNodeConfigs(List<PipelineRow> rows) {
        Map<String, NodeConfig> nodes = new LinkedHashMap<>();
        for (PipelineRow row : rows) {
            if (row.nodeId == null || row.nodeId.isBlank()) {
                continue;
            }
            nodes.put(row.nodeId, NodeConfig.builder()
                    .nodeId(row.nodeId)
                    .nodeType(row.nodeType)
                    .nextNodeId(row.nextNodeId)
                    .settings(parseJson(row.settingsJson))
                    .condition(parseJson(row.conditionJson))
                    .build());
        }
        return new ArrayList<>(nodes.values());
    }

    private PipelineRow toPipelineRow(ResultSet resultSet, int rowNumber) throws SQLException {
        PipelineRow row = new PipelineRow();
        row.pipelineId = resultSet.getString("pipeline_id");
        row.name = resultSet.getString("name");
        row.description = resultSet.getString("description");
        row.version = resultSet.getInt("version");
        row.nodeId = resultSet.getString("node_id");
        row.nodeType = resultSet.getString("node_type");
        row.nextNodeId = resultSet.getString("next_node_id");
        row.settingsJson = resultSet.getString("settings_json");
        row.conditionJson = resultSet.getString("condition_json");
        return row;
    }

    private PipelineRecordRow toPipelineRecordRow(ResultSet resultSet, int rowNumber) throws SQLException {
        PipelineRecordRow row = new PipelineRecordRow();
        row.pipelineId = resultSet.getString("pipeline_id");
        row.name = resultSet.getString("name");
        row.description = resultSet.getString("description");
        row.version = resultSet.getInt("version");
        row.createdBy = resultSet.getString("created_by");
        row.createTime = toInstant(resultSet.getTimestamp("create_time"));
        row.updateTime = toInstant(resultSet.getTimestamp("update_time"));
        row.nodePk = resultSet.getString("node_pk");
        row.nodeId = resultSet.getString("node_id");
        row.nodeType = resultSet.getString("node_type");
        row.nextNodeId = resultSet.getString("next_node_id");
        row.settingsJson = resultSet.getString("settings_json");
        row.conditionJson = resultSet.getString("condition_json");
        return row;
    }

    private IngestionPipelineRecord toPipelineRecordWithoutNodes(ResultSet resultSet, int rowNumber)
            throws SQLException {
        IngestionPipelineRecord record = new IngestionPipelineRecord();
        record.setId(resultSet.getString("pipeline_id"));
        record.setName(resultSet.getString("name"));
        record.setDescription(resultSet.getString("description"));
        record.setVersion(resultSet.getInt("version"));
        record.setCreatedBy(resultSet.getString("created_by"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        return record;
    }

    private void replaceNodes(String pipelineId, List<IngestionPipelineNodePayload> nodes, String operator) {
        long safePipelineId = numericId(pipelineId, "pipelineId");
        long safeOperator = operatorId(operator);
        jdbcTemplate.update(SQL_DELETE_PIPELINE_NODES, safeOperator, safePipelineId);
        List<IngestionPipelineNodePayload> safeNodes = Objects.requireNonNullElse(nodes, List.of());
        for (IngestionPipelineNodePayload node : safeNodes) {
            jdbcTemplate.update(SQL_INSERT_NODE,
                    SnowflakeIds.nextId(),
                    safePipelineId,
                    numericId(node.nodeId(), "nodeId"),
                    requireText(node.nodeType(), "nodeType"),
                    blankNumericToNull(node.nextNodeId(), "nextNodeId"),
                    toJson(node.settings()),
                    toJson(node.condition()),
                    safeOperator,
                    safeOperator);
        }
    }

    private QueryParts buildPageQuery(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new QueryParts("", List.of());
        }
        return new QueryParts(" AND name LIKE ?", List.of("%" + keyword.trim() + "%"));
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalArgumentException("入库流水线节点 JSON 配置不合法", ex);
        }
    }

    private String toJson(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception ex) {
            throw new IllegalArgumentException("入库流水线节点 JSON 配置不合法", ex);
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private long numericId(String value, String name) {
        String text = requireText(value, name);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a numeric id", ex);
        }
    }

    private Long blankNumericToNull(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return numericId(value, name);
    }

    private long operatorId(String operator) {
        if (operator == null || operator.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(operator.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
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

    private static class PipelineRow {

        String pipelineId;
        String name;
        String description;
        int version;
        String nodeId;
        String nodeType;
        String nextNodeId;
        String settingsJson;
        String conditionJson;
    }

    private static class PipelineRecordRow extends PipelineRow {

        private String nodePk;
        private String createdBy;
        private Instant createTime;
        private Instant updateTime;
    }

    private record QueryParts(String where, List<Object> args) {
    }
}
