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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.workflow.ExecutionStepAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowVisualizationRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcWorkflowVisualizationRepositoryAdapter implements WorkflowVisualizationRepositoryPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String STEP_COLUMNS = """
            step_id, run_id, step_type, status, started_at, completed_at,
            duration_ms, result_data_json, position_x, position_y
            """;
    private static final String SQL_INSERT_STEP = """
            INSERT INTO t_agent_execution_steps
            (step_id, run_id, step_type, status, started_at, completed_at,
             duration_ms, result_data_json, position_x, position_y)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_RUN_ID = """
            SELECT %s
            FROM t_agent_execution_steps
            WHERE run_id = ?
            ORDER BY started_at ASC
            """.formatted(STEP_COLUMNS);
    private static final String SQL_UPDATE_STATUS = """
            UPDATE t_agent_execution_steps
            SET status = ?, completed_at = ?, duration_ms = ?
            WHERE step_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowVisualizationRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcWorkflowVisualizationRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public List<ExecutionStepAggregate> findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_FIND_BY_RUN_ID, this::mapStep, runId.trim());
    }

    @Override
    public void saveStep(ExecutionStepAggregate step) {
        ExecutionStepAggregate safe = Objects.requireNonNull(step, "step must not be null");
        jdbcTemplate.update(SQL_INSERT_STEP,
                safe.stepId(),
                safe.runId(),
                safe.stepType(),
                safe.status(),
                toTimestamp(safe.startedAt()),
                toTimestamp(safe.completedAt()),
                safe.durationMs(),
                toJson(safe.resultData()),
                safe.positionX(),
                safe.positionY());
    }

    @Override
    public void updateStepStatus(String stepId, String status, Instant completedAt, Long durationMs) {
        jdbcTemplate.update(SQL_UPDATE_STATUS,
                status,
                toTimestamp(completedAt),
                durationMs,
                stepId);
    }

    private ExecutionStepAggregate mapStep(ResultSet rs, int rowNum) throws SQLException {
        return new ExecutionStepAggregate(
                rs.getString("step_id"),
                rs.getString("run_id"),
                rs.getString("step_type"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("completed_at")),
                toNullableLong(rs.getObject("duration_ms")),
                fromJson(rs.getString("result_data_json")),
                toNullableInt(rs.getObject("position_x")),
                toNullableInt(rs.getObject("position_y")));
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private Long toNullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    private Integer toNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        return null;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
