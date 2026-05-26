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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentHandoffRepositoryAdapter implements AgentHandoffRepositoryPort {

    private static final String HANDOFF_COLUMNS = """
            handoff_id, tenant_id, parent_run_id, child_run_id, source_agent_id, target_agent_id,
            status, failure_code, handoff_reason, input_summary_json, context_summary_json,
            created_at, updated_at, finished_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_handoff
            (handoff_id, tenant_id, parent_run_id, child_run_id, source_agent_id, target_agent_id,
             status, failure_code, handoff_reason, input_summary_json, context_summary_json,
             created_at, updated_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_agent_handoff
            SET tenant_id = ?,
                parent_run_id = ?,
                child_run_id = ?,
                source_agent_id = ?,
                target_agent_id = ?,
                status = ?,
                failure_code = ?,
                handoff_reason = ?,
                input_summary_json = ?,
                context_summary_json = ?,
                created_at = ?,
                updated_at = ?,
                finished_at = ?
            WHERE handoff_id = ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_agent_handoff
            WHERE handoff_id = ?
            """.formatted(HANDOFF_COLUMNS);
    private static final String SQL_LIST_BY_PARENT = """
            SELECT %s
            FROM sa_agent_handoff
            WHERE tenant_id = ?
              AND parent_run_id = ?
            ORDER BY created_at ASC, handoff_id ASC
            """.formatted(HANDOFF_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentHandoffRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AgentHandoff save(AgentHandoff handoff) {
        AgentHandoff safeHandoff = Objects.requireNonNull(handoff, "handoff must not be null");
        Optional<AgentHandoff> existing = findById(safeHandoff.handoffId());
        if (existing.isPresent()) {
            return update(safeHandoff);
        }
        insert(safeHandoff);
        return safeHandoff;
    }

    @Override
    public AgentHandoff update(AgentHandoff handoff) {
        AgentHandoff safeHandoff = Objects.requireNonNull(handoff, "handoff must not be null");
        Optional<AgentHandoff> existing = findById(safeHandoff.handoffId());
        if (existing.isPresent() && existing.orElseThrow().status().isTerminal()) {
            return existing.orElseThrow();
        }
        if (existing.isEmpty()) {
            insert(safeHandoff);
            return safeHandoff;
        }
        jdbcTemplate.update(SQL_UPDATE,
                safeHandoff.tenantId(),
                safeHandoff.parentRunId(),
                safeHandoff.childRunId(),
                safeHandoff.sourceAgentId(),
                safeHandoff.targetAgentId(),
                safeHandoff.status().name(),
                failureName(safeHandoff.failureCode()),
                safeHandoff.handoffReason(),
                safeHandoff.inputSummaryJson(),
                safeHandoff.contextSummaryJson(),
                toTimestamp(safeHandoff.createdAt()),
                toTimestamp(safeHandoff.updatedAt()),
                toTimestamp(safeHandoff.finishedAt()),
                safeHandoff.handoffId());
        return safeHandoff;
    }

    @Override
    public Optional<AgentHandoff> findById(String handoffId) {
        if (!hasText(handoffId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapHandoff, handoffId.trim()).stream().findFirst();
    }

    @Override
    public List<AgentHandoff> listByParentRunId(String tenantId, String parentRunId) {
        if (!hasText(tenantId) || !hasText(parentRunId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BY_PARENT, this::mapHandoff, tenantId.trim(), parentRunId.trim());
    }

    private void insert(AgentHandoff handoff) {
        jdbcTemplate.update(SQL_INSERT,
                handoff.handoffId(),
                handoff.tenantId(),
                handoff.parentRunId(),
                handoff.childRunId(),
                handoff.sourceAgentId(),
                handoff.targetAgentId(),
                handoff.status().name(),
                failureName(handoff.failureCode()),
                handoff.handoffReason(),
                handoff.inputSummaryJson(),
                handoff.contextSummaryJson(),
                toTimestamp(handoff.createdAt()),
                toTimestamp(handoff.updatedAt()),
                toTimestamp(handoff.finishedAt()));
    }

    private AgentHandoff mapHandoff(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentHandoff(
                resultSet.getString("handoff_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("parent_run_id"),
                resultSet.getString("child_run_id"),
                resultSet.getString("source_agent_id"),
                resultSet.getString("target_agent_id"),
                AgentHandoffStatus.valueOf(resultSet.getString("status")),
                failureCode(resultSet.getString("failure_code")),
                resultSet.getString("handoff_reason"),
                resultSet.getString("input_summary_json"),
                resultSet.getString("context_summary_json"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")),
                toInstant(resultSet.getTimestamp("finished_at")));
    }

    private String failureName(AgentHandoffFailureCode failureCode) {
        return failureCode == null ? null : failureCode.name();
    }

    private AgentHandoffFailureCode failureCode(String value) {
        return hasText(value) ? AgentHandoffFailureCode.valueOf(value) : null;
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
