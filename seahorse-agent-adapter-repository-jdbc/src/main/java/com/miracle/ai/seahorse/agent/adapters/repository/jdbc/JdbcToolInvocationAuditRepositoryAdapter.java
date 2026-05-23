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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 工具调用审计 JDBC 仓储适配器，负责持久化 Tool Gateway 的请求、策略裁决和完成状态。
 */
public class JdbcToolInvocationAuditRepositoryAdapter implements ToolInvocationAuditPort,
        ToolInvocationUsagePort,
        ToolInvocationAuditQueryPort {

    private static final String AUDIT_COLUMNS = """
            invocation_id, run_id, step_id, agent_id, version_id, tenant_id, user_id, tool_id,
            idempotency_key, status, policy_decision_id, arguments_summary, result_summary,
            error_message, started_at, finished_at
            """;
    private static final String SQL_INSERT_REQUESTED = """
            INSERT INTO sa_tool_invocation
            (invocation_id, run_id, step_id, agent_id, version_id, tenant_id, user_id, tool_id,
             idempotency_key, status, arguments_summary, started_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_DECISION = """
            UPDATE sa_tool_invocation
            SET status = ?,
                policy_decision_id = ?
            WHERE invocation_id = ?
            """;
    private static final String SQL_UPDATE_COMPLETED = """
            UPDATE sa_tool_invocation
            SET status = ?,
                result_summary = ?,
                error_message = ?,
                finished_at = ?
            WHERE invocation_id = ?
            """;
    private static final String SQL_COUNT_REQUESTED = """
            SELECT COUNT(1)
            FROM sa_tool_invocation
            WHERE run_id = ?
              AND agent_id = ?
              AND version_id = ?
              AND tool_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcToolInvocationAuditRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void recordRequested(ToolInvocationAuditRecord record) {
        ToolInvocationAuditRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        jdbcTemplate.update(SQL_INSERT_REQUESTED,
                safeRecord.invocationId(),
                safeRecord.runId(),
                safeRecord.stepId(),
                safeRecord.agentId(),
                safeRecord.versionId(),
                safeRecord.tenantId(),
                safeRecord.userId(),
                safeRecord.toolId(),
                safeRecord.idempotencyKey(),
                safeRecord.status().name(),
                safeRecord.argumentsSummary(),
                toTimestamp(safeRecord.startedAt()));
    }

    @Override
    public void recordDecision(ToolInvocationAuditDecision decision) {
        ToolInvocationAuditDecision safeDecision = Objects.requireNonNull(decision, "decision must not be null");
        jdbcTemplate.update(SQL_UPDATE_DECISION,
                safeDecision.status().name(),
                safeDecision.policyDecisionId(),
                safeDecision.invocationId());
    }

    @Override
    public void recordCompleted(ToolInvocationAuditCompletion completion) {
        ToolInvocationAuditCompletion safeCompletion =
                Objects.requireNonNull(completion, "completion must not be null");
        jdbcTemplate.update(SQL_UPDATE_COMPLETED,
                safeCompletion.status().name(),
                safeCompletion.resultSummary(),
                safeCompletion.errorMessage(),
                toTimestamp(safeCompletion.finishedAt()),
                safeCompletion.invocationId());
    }

    @Override
    public long countRequestedCalls(String runId, String agentId, String versionId, String toolId) {
        if (!hasText(runId) || !hasText(agentId) || !hasText(versionId) || !hasText(toolId)) {
            return 0L;
        }
        Long count = jdbcTemplate.queryForObject(SQL_COUNT_REQUESTED,
                Long.class,
                runId.trim(),
                agentId.trim(),
                versionId.trim(),
                toolId.trim());
        return count == null ? 0L : count;
    }

    @Override
    public ToolInvocationAuditPage page(ToolInvocationAuditQuery query) {
        ToolInvocationAuditQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        QueryParts parts = buildQueryParts(safeQuery);
        long total = count(parts);
        if (total == 0L) {
            return new ToolInvocationAuditPage(List.of(), 0L, safeQuery.size(), safeQuery.current(), 0L);
        }

        long offset = (safeQuery.current() - 1L) * safeQuery.size();
        List<Object> parameters = new ArrayList<>(parts.parameters());
        parameters.add(safeQuery.size());
        parameters.add(offset);
        List<ToolInvocationAuditEntry> records = jdbcTemplate.query("""
                        SELECT %s
                        FROM sa_tool_invocation
                        %s
                        ORDER BY started_at DESC, invocation_id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(AUDIT_COLUMNS, parts.whereSql()),
                this::mapAuditEntry,
                parameters.toArray());
        long pages = (total + safeQuery.size() - 1L) / safeQuery.size();
        return new ToolInvocationAuditPage(records, total, safeQuery.size(), safeQuery.current(), pages);
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

    private long count(QueryParts parts) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM sa_tool_invocation
                        %s
                        """.formatted(parts.whereSql()),
                Long.class,
                parts.parameters().toArray());
        return count == null ? 0L : count;
    }

    private QueryParts buildQueryParts(ToolInvocationAuditQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        addCondition(conditions, parameters, "tenant_id", query.tenantId());
        addCondition(conditions, parameters, "agent_id", query.agentId());
        addCondition(conditions, parameters, "version_id", query.versionId());
        addCondition(conditions, parameters, "run_id", query.runId());
        addCondition(conditions, parameters, "tool_id", query.toolId());
        if (query.status() != null) {
            conditions.add("status = ?");
            parameters.add(query.status().name());
        }
        String whereSql = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new QueryParts(whereSql, parameters);
    }

    private void addCondition(List<String> conditions, List<Object> parameters, String column, String value) {
        if (!hasText(value)) {
            return;
        }
        conditions.add(column + " = ?");
        parameters.add(value.trim());
    }

    private ToolInvocationAuditEntry mapAuditEntry(ResultSet resultSet, int rowNum) throws SQLException {
        return new ToolInvocationAuditEntry(
                resultSet.getString("invocation_id"),
                resultSet.getString("run_id"),
                resultSet.getString("step_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                resultSet.getString("tool_id"),
                resultSet.getString("idempotency_key"),
                ToolInvocationStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("policy_decision_id"),
                resultSet.getString("arguments_summary"),
                resultSet.getString("result_summary"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("finished_at")));
    }

    private record QueryParts(String whereSql, List<Object> parameters) {

        private QueryParts {
            parameters = List.copyOf(parameters);
        }
    }
}
