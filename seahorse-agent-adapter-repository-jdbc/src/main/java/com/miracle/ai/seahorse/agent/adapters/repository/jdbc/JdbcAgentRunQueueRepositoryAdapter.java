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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQueueRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JdbcAgentRunQueueRepositoryAdapter implements AgentRunQueueRepositoryPort {

    private static final String RUN_COLUMNS = """
            r.run_id, r.agent_id, r.version_id, r.rollout_id, r.tenant_id, r.user_id, r.conversation_id, r.trigger_type,
            r.input_summary, r.status, r.trace_id, r.token_input, r.token_output, r.cost_total, r.error_code,
            r.error_message, r.started_at, r.finished_at
            """;
    private static final String SQL_FIND_RUNNABLE = """
            SELECT %s
            FROM sa_agent_run r
            LEFT JOIN sa_agent_run_lease l ON l.run_id = r.run_id
            WHERE r.tenant_id = ?
              AND r.status IN (?, ?, ?)
              AND (l.run_id IS NULL OR l.lease_until <= ?)
            ORDER BY r.started_at ASC, r.run_id ASC
            LIMIT ?
            """.formatted(RUN_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentRunQueueRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<AgentRun> findRunnable(String tenantId, int limit, Instant now) {
        if (!hasText(tenantId) || limit <= 0) {
            return List.of();
        }
        Instant safeNow = Objects.requireNonNullElseGet(now, Instant::now);
        return jdbcTemplate.query(SQL_FIND_RUNNABLE,
                this::mapRun,
                tenantId.trim(),
                AgentRunStatus.CREATED.name(),
                AgentRunStatus.RUNNING.name(),
                AgentRunStatus.RETRYING.name(),
                toTimestamp(safeNow),
                limit);
    }

    private AgentRun mapRun(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentRun(
                resultSet.getString("run_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                resultSet.getString("rollout_id"),
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
