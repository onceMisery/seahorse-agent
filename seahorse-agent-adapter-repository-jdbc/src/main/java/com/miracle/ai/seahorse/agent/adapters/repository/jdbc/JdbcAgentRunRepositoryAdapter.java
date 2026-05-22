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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentRunRepositoryAdapter implements AgentRunRepositoryPort {

    private static final String SQL_INSERT_RUN = """
            INSERT INTO sa_agent_run
            (run_id, agent_id, version_id, tenant_id, user_id, conversation_id, trigger_type, input_summary,
             status, trace_id, token_input, token_output, cost_total, error_code, error_message, started_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_RUN = """
            UPDATE sa_agent_run
            SET agent_id = ?,
                version_id = ?,
                tenant_id = ?,
                user_id = ?,
                conversation_id = ?,
                trigger_type = ?,
                input_summary = ?,
                status = ?,
                trace_id = ?,
                token_input = ?,
                token_output = ?,
                cost_total = ?,
                error_code = ?,
                error_message = ?,
                started_at = ?,
                finished_at = ?
            WHERE run_id = ?
            """;
    private static final String SQL_FIND_RUN = """
            SELECT run_id, agent_id, version_id, tenant_id, user_id, conversation_id, trigger_type, input_summary,
                   status, trace_id, token_input, token_output, cost_total, error_code, error_message, started_at,
                   finished_at
            FROM sa_agent_run
            WHERE run_id = ?
            """;
    private static final String SQL_INSERT_STEP = """
            INSERT INTO sa_agent_step
            (step_id, run_id, step_no, step_type, status, input_json, output_json,
             error_code, error_message, started_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_LIST_STEPS = """
            SELECT step_id, run_id, step_no, step_type, status, input_json, output_json,
                   error_code, error_message, started_at, finished_at
            FROM sa_agent_step
            WHERE run_id = ?
            ORDER BY step_no ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentRunRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void createRun(AgentRun run) {
        AgentRun safeRun = Objects.requireNonNull(run, "run must not be null");
        jdbcTemplate.update(SQL_INSERT_RUN,
                safeRun.runId(),
                safeRun.agentId(),
                safeRun.versionId(),
                safeRun.tenantId(),
                safeRun.userId(),
                safeRun.conversationId(),
                safeRun.triggerType().name(),
                safeRun.inputSummary(),
                safeRun.status().name(),
                safeRun.traceId(),
                safeRun.tokenInput(),
                safeRun.tokenOutput(),
                safeRun.costTotal(),
                safeRun.errorCode(),
                safeRun.errorMessage(),
                toTimestamp(safeRun.startedAt()),
                toTimestamp(safeRun.finishedAt()));
    }

    @Override
    public void updateRun(AgentRun run) {
        AgentRun safeRun = Objects.requireNonNull(run, "run must not be null");
        jdbcTemplate.update(SQL_UPDATE_RUN,
                safeRun.agentId(),
                safeRun.versionId(),
                safeRun.tenantId(),
                safeRun.userId(),
                safeRun.conversationId(),
                safeRun.triggerType().name(),
                safeRun.inputSummary(),
                safeRun.status().name(),
                safeRun.traceId(),
                safeRun.tokenInput(),
                safeRun.tokenOutput(),
                safeRun.costTotal(),
                safeRun.errorCode(),
                safeRun.errorMessage(),
                toTimestamp(safeRun.startedAt()),
                toTimestamp(safeRun.finishedAt()),
                safeRun.runId());
    }

    @Override
    public Optional<AgentRun> findRunById(String runId) {
        if (!hasText(runId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_RUN, this::mapRun, runId.trim()).stream().findFirst();
    }

    @Override
    public void appendStep(AgentStep step) {
        AgentStep safeStep = Objects.requireNonNull(step, "step must not be null");
        jdbcTemplate.update(SQL_INSERT_STEP,
                safeStep.stepId(),
                safeStep.runId(),
                safeStep.stepNo(),
                safeStep.stepType().name(),
                safeStep.status().name(),
                safeStep.inputJson(),
                safeStep.outputJson(),
                safeStep.errorCode(),
                safeStep.errorMessage(),
                toTimestamp(safeStep.startedAt()),
                toTimestamp(safeStep.finishedAt()));
    }

    @Override
    public List<AgentStep> listSteps(String runId) {
        if (!hasText(runId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_STEPS, this::mapStep, runId.trim());
    }

    private AgentRun mapRun(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentRun(
                resultSet.getString("run_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                resultSet.getString("conversation_id"),
                AgentRunTriggerType.valueOf(resultSet.getString("trigger_type")),
                resultSet.getString("input_summary"),
                AgentRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("trace_id"),
                resultSet.getLong("token_input"),
                resultSet.getLong("token_output"),
                resultSet.getBigDecimal("cost_total"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("finished_at")));
    }

    private AgentStep mapStep(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentStep(
                resultSet.getString("step_id"),
                resultSet.getString("run_id"),
                resultSet.getInt("step_no"),
                AgentStepType.valueOf(resultSet.getString("step_type")),
                AgentStepStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("input_json"),
                resultSet.getString("output_json"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("finished_at")));
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
