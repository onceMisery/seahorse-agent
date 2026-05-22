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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * 工具调用审计 JDBC 仓储适配器，负责持久化 Tool Gateway 的请求、策略裁决和完成状态。
 */
public class JdbcToolInvocationAuditRepositoryAdapter implements ToolInvocationAuditPort, ToolInvocationUsagePort {

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

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
