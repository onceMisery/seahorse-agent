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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecisionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 工具审批请求 JDBC 仓储适配器，负责把 Tool Gateway 产生的待审批记录持久化。
 */
public class JdbcToolApprovalRequestRepositoryAdapter implements ToolApprovalRequestRepositoryPort,
        ApprovalRequestQueryPort,
        ApprovalRequestDecisionPort {

    private static final String APPROVAL_COLUMNS = """
            approval_id, run_id, step_id, tool_invocation_id, tenant_id, user_id, agent_id, tool_id,
            approval_type, risk_level, summary, arguments_preview_json, status, requested_at, expires_at,
            decided_by, decided_at, decision_comment
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_approval_request
            (approval_id, run_id, step_id, tool_invocation_id, tenant_id, user_id, agent_id, tool_id,
             approval_type, risk_level, summary, arguments_preview_json, status, requested_at, expires_at,
             decided_by, decided_at, decision_comment)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_approval_request
            WHERE approval_id = ?
            """.formatted(APPROVAL_COLUMNS);
    private static final String SQL_FIND_LATEST_BY_RUN_AND_STEP = """
            SELECT %s
            FROM sa_approval_request
            WHERE run_id = ?
              AND step_id = ?
            ORDER BY requested_at DESC, approval_id DESC
            LIMIT 1
            """.formatted(APPROVAL_COLUMNS);
    private static final String SQL_DECIDE = """
            UPDATE sa_approval_request
            SET status = ?,
                decided_by = ?,
                decided_at = ?,
                decision_comment = ?,
                arguments_preview_json = COALESCE(?, arguments_preview_json)
            WHERE approval_id = ?
              AND status = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcToolApprovalRequestRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(ApprovalRequest request) {
        ApprovalRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeRequest.approvalId(),
                safeRequest.runId(),
                safeRequest.stepId(),
                safeRequest.toolInvocationId(),
                safeRequest.tenantId(),
                safeRequest.userId(),
                safeRequest.agentId(),
                safeRequest.toolId(),
                safeRequest.approvalType().name(),
                safeRequest.riskLevel().name(),
                safeRequest.summary(),
                safeRequest.argumentsPreviewJson(),
                safeRequest.status().name(),
                toTimestamp(safeRequest.requestedAt()),
                toTimestamp(safeRequest.expiresAt()),
                safeRequest.decidedBy(),
                toTimestamp(safeRequest.decidedAt()),
                safeRequest.decisionComment());
    }

    @Override
    public Optional<ApprovalRequest> findById(String approvalId) {
        if (!hasText(approvalId)) {
            return Optional.empty();
        }
        List<ApprovalRequest> records = jdbcTemplate.query(SQL_FIND_BY_ID,
                this::mapApprovalRequest,
                approvalId.trim());
        return records.stream().findFirst();
    }

    @Override
    public Optional<ApprovalRequest> findLatestByRunIdAndStepId(String runId, String stepId) {
        if (!hasText(runId) || !hasText(stepId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_LATEST_BY_RUN_AND_STEP,
                this::mapApprovalRequest,
                runId.trim(),
                stepId.trim()).stream().findFirst();
    }

    @Override
    public ApprovalRequestPage page(ApprovalRequestQuery query) {
        ApprovalRequestQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        QueryParts parts = buildQueryParts(safeQuery);
        long total = count(parts);
        if (total == 0L) {
            return new ApprovalRequestPage(List.of(), 0L, safeQuery.size(), safeQuery.current(), 0L);
        }

        long offset = (safeQuery.current() - 1L) * safeQuery.size();
        List<Object> parameters = new ArrayList<>(parts.parameters());
        parameters.add(safeQuery.size());
        parameters.add(offset);
        List<ApprovalRequest> records = jdbcTemplate.query("""
                        SELECT %s
                        FROM sa_approval_request
                        %s
                        ORDER BY requested_at DESC, approval_id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(APPROVAL_COLUMNS, parts.whereSql()),
                this::mapApprovalRequest,
                parameters.toArray());
        long pages = (total + safeQuery.size() - 1L) / safeQuery.size();
        return new ApprovalRequestPage(records, total, safeQuery.size(), safeQuery.current(), pages);
    }

    @Override
    public Optional<ApprovalRequest> decide(ApprovalRequestDecision decision) {
        ApprovalRequestDecision safeDecision = Objects.requireNonNull(decision, "decision must not be null");
        int updated = jdbcTemplate.update(SQL_DECIDE,
                safeDecision.toStatus().name(),
                safeDecision.decidedBy(),
                toTimestamp(safeDecision.decidedAt()),
                safeDecision.decisionComment(),
                safeDecision.argumentsPreviewJson(),
                safeDecision.approvalId(),
                safeDecision.fromStatus().name());
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(safeDecision.approvalId());
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
                        FROM sa_approval_request
                        %s
                        """.formatted(parts.whereSql()),
                Long.class,
                parts.parameters().toArray());
        return count == null ? 0L : count;
    }

    private QueryParts buildQueryParts(ApprovalRequestQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        addCondition(conditions, parameters, "tenant_id", query.tenantId());
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

    private ApprovalRequest mapApprovalRequest(ResultSet resultSet, int rowNum) throws SQLException {
        return new ApprovalRequest(
                resultSet.getString("approval_id"),
                resultSet.getString("run_id"),
                resultSet.getString("step_id"),
                resultSet.getString("tool_invocation_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("tool_id"),
                ApprovalType.valueOf(resultSet.getString("approval_type")),
                ToolRiskLevel.valueOf(resultSet.getString("risk_level")),
                resultSet.getString("summary"),
                resultSet.getString("arguments_preview_json"),
                ApprovalRequestStatus.valueOf(resultSet.getString("status")),
                toInstant(resultSet.getTimestamp("requested_at")),
                toInstant(resultSet.getTimestamp("expires_at")),
                resultSet.getString("decided_by"),
                toInstant(resultSet.getTimestamp("decided_at")),
                resultSet.getString("decision_comment"));
    }

    private record QueryParts(String whereSql, List<Object> parameters) {

        private QueryParts {
            parameters = List.copyOf(parameters);
        }
    }
}
