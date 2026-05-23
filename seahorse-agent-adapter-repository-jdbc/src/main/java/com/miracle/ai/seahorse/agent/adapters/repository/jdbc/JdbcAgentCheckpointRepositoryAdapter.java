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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentCheckpointRepositoryAdapter implements AgentCheckpointRepositoryPort {

    private static final String CHECKPOINT_COLUMNS = """
            checkpoint_id, run_id, step_id, sequence_no, checkpoint_type, state_json, message_history_json,
            context_pack_id, pending_tool_call_json, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_checkpoint
            (checkpoint_id, run_id, step_id, sequence_no, checkpoint_type, state_json, message_history_json,
             context_pack_id, pending_tool_call_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_LATEST_BY_RUN_ID = """
            SELECT %s
            FROM sa_agent_checkpoint
            WHERE run_id = ?
            ORDER BY sequence_no DESC
            LIMIT 1
            """.formatted(CHECKPOINT_COLUMNS);
    private static final String SQL_LIST_BY_RUN_ID = """
            SELECT %s
            FROM sa_agent_checkpoint
            WHERE run_id = ?
            ORDER BY sequence_no ASC
            """.formatted(CHECKPOINT_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentCheckpointRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(AgentCheckpoint checkpoint) {
        AgentCheckpoint safeCheckpoint = Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeCheckpoint.checkpointId(),
                safeCheckpoint.runId(),
                safeCheckpoint.stepId(),
                safeCheckpoint.sequenceNo(),
                safeCheckpoint.checkpointType().name(),
                safeCheckpoint.stateJson(),
                safeCheckpoint.messageHistoryJson(),
                safeCheckpoint.contextPackId(),
                safeCheckpoint.pendingToolCallJson(),
                toTimestamp(safeCheckpoint.createdAt()));
    }

    @Override
    public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
        if (!hasText(runId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_LATEST_BY_RUN_ID, this::mapCheckpoint, runId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public List<AgentCheckpoint> listByRunId(String runId) {
        if (!hasText(runId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BY_RUN_ID, this::mapCheckpoint, runId.trim());
    }

    private AgentCheckpoint mapCheckpoint(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentCheckpoint(
                resultSet.getString("checkpoint_id"),
                resultSet.getString("run_id"),
                resultSet.getString("step_id"),
                resultSet.getLong("sequence_no"),
                AgentCheckpointType.valueOf(resultSet.getString("checkpoint_type")),
                resultSet.getString("state_json"),
                resultSet.getString("message_history_json"),
                resultSet.getString("context_pack_id"),
                resultSet.getString("pending_tool_call_json"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
