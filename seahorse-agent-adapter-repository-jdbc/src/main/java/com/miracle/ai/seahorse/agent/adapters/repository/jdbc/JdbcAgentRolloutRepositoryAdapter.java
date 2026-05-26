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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentRolloutRepositoryAdapter implements AgentRolloutRepositoryPort {

    private static final String ROLLOUT_COLUMNS = """
            rollout_id, tenant_id, agent_id, version_id, canary_percent, status,
            failure_code, gate_report_id, started_by, started_at, updated_at, finished_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_version_rollout
            (rollout_id, tenant_id, agent_id, version_id, canary_percent, status,
             failure_code, gate_report_id, started_by, started_at, updated_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_agent_version_rollout
            SET tenant_id = ?,
                agent_id = ?,
                version_id = ?,
                canary_percent = ?,
                status = ?,
                failure_code = ?,
                gate_report_id = ?,
                started_by = ?,
                started_at = ?,
                updated_at = ?,
                finished_at = ?
            WHERE rollout_id = ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_agent_version_rollout
            WHERE rollout_id = ?
            """.formatted(ROLLOUT_COLUMNS);
    private static final String SQL_FIND_LATEST = """
            SELECT %s
            FROM sa_agent_version_rollout
            WHERE tenant_id = ?
              AND agent_id = ?
              AND version_id = ?
            ORDER BY updated_at DESC, rollout_id DESC
            LIMIT 1
            """.formatted(ROLLOUT_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentRolloutRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AgentVersionRollout save(AgentVersionRollout rollout) {
        AgentVersionRollout safeRollout = Objects.requireNonNull(rollout, "rollout must not be null");
        if (findById(safeRollout.rolloutId()).isPresent()) {
            jdbcTemplate.update(SQL_UPDATE,
                    safeRollout.tenantId(),
                    safeRollout.agentId(),
                    safeRollout.versionId(),
                    safeRollout.canaryPercent(),
                    safeRollout.status().name(),
                    failureCodeName(safeRollout.failureCode()),
                    safeRollout.gateReportId(),
                    safeRollout.startedBy(),
                    toTimestamp(safeRollout.startedAt()),
                    toTimestamp(safeRollout.updatedAt()),
                    toTimestamp(safeRollout.finishedAt()),
                    safeRollout.rolloutId());
            return safeRollout;
        }
        jdbcTemplate.update(SQL_INSERT,
                safeRollout.rolloutId(),
                safeRollout.tenantId(),
                safeRollout.agentId(),
                safeRollout.versionId(),
                safeRollout.canaryPercent(),
                safeRollout.status().name(),
                failureCodeName(safeRollout.failureCode()),
                safeRollout.gateReportId(),
                safeRollout.startedBy(),
                toTimestamp(safeRollout.startedAt()),
                toTimestamp(safeRollout.updatedAt()),
                toTimestamp(safeRollout.finishedAt()));
        return safeRollout;
    }

    @Override
    public Optional<AgentVersionRollout> findById(String rolloutId) {
        if (!hasText(rolloutId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapRollout, rolloutId.trim()).stream().findFirst();
    }

    @Override
    public Optional<AgentVersionRollout> findLatest(String tenantId, String agentId, String versionId) {
        if (!hasText(tenantId) || !hasText(agentId) || !hasText(versionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        SQL_FIND_LATEST,
                        this::mapRollout,
                        tenantId.trim(),
                        agentId.trim(),
                        versionId.trim())
                .stream()
                .findFirst();
    }

    private AgentVersionRollout mapRollout(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentVersionRollout(
                resultSet.getString("rollout_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                resultSet.getInt("canary_percent"),
                AgentRolloutStatus.valueOf(resultSet.getString("status")),
                failureCode(resultSet.getString("failure_code")),
                resultSet.getString("gate_report_id"),
                resultSet.getString("started_by"),
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("updated_at")),
                toInstant(resultSet.getTimestamp("finished_at")));
    }

    private String failureCodeName(AgentRolloutFailureCode failureCode) {
        return failureCode == null ? null : failureCode.name();
    }

    private AgentRolloutFailureCode failureCode(String value) {
        return hasText(value) ? AgentRolloutFailureCode.valueOf(value) : null;
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
