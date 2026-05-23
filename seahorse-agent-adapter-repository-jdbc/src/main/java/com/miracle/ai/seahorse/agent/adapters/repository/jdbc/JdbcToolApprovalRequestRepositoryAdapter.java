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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * 工具审批请求 JDBC 仓储适配器，负责把 Tool Gateway 产生的待审批记录持久化。
 */
public class JdbcToolApprovalRequestRepositoryAdapter implements ToolApprovalRequestRepositoryPort {

    private static final String SQL_INSERT = """
            INSERT INTO sa_approval_request
            (approval_id, run_id, step_id, tool_invocation_id, tenant_id, user_id, agent_id, tool_id,
             approval_type, risk_level, summary, arguments_preview_json, status, requested_at, expires_at,
             decided_by, decided_at, decision_comment)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
