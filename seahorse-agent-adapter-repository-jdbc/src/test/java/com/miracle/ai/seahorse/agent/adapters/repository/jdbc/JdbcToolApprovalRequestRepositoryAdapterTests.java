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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcToolApprovalRequestRepositoryAdapterTests {

    @Test
    void shouldSavePendingApprovalRequest() {
        DriverManagerDataSource dataSource = dataSource("tool-approval-request");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createApprovalRequestSchema(jdbcTemplate);
        JdbcToolApprovalRequestRepositoryAdapter adapter = new JdbcToolApprovalRequestRepositoryAdapter(dataSource);
        Instant requestedAt = Instant.parse("2026-05-23T00:00:00Z");

        adapter.save(new ApprovalRequest(
                "approval-1",
                "run-1",
                "step-1",
                "invocation-1",
                "tenant-1",
                "user-1",
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                "{\"argumentKeys\":[\"input\"]}",
                ApprovalRequestStatus.PENDING,
                requestedAt,
                null,
                null,
                null,
                null));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT approval_id, run_id, step_id, tool_invocation_id, tenant_id, user_id, agent_id,
                       tool_id, approval_type, risk_level, summary, arguments_preview_json, status,
                       requested_at, expires_at, decided_by, decided_at, decision_comment
                FROM sa_approval_request
                WHERE approval_id = ?
                """, "approval-1");

        assertThat(row.get("RUN_ID")).isEqualTo("run-1");
        assertThat(row.get("STEP_ID")).isEqualTo("step-1");
        assertThat(row.get("TOOL_INVOCATION_ID")).isEqualTo("invocation-1");
        assertThat(row.get("TENANT_ID")).isEqualTo("tenant-1");
        assertThat(row.get("USER_ID")).isEqualTo("user-1");
        assertThat(row.get("AGENT_ID")).isEqualTo("agent-1");
        assertThat(row.get("TOOL_ID")).isEqualTo("memory-forget");
        assertThat(row.get("APPROVAL_TYPE")).isEqualTo(ApprovalType.TOOL_EXECUTION.name());
        assertThat(row.get("RISK_LEVEL")).isEqualTo(ToolRiskLevel.HIGH.name());
        assertThat(row.get("SUMMARY")).isEqualTo("Tool memory-forget requires approval");
        assertThat(row.get("ARGUMENTS_PREVIEW_JSON")).isEqualTo("{\"argumentKeys\":[\"input\"]}");
        assertThat(row.get("STATUS")).isEqualTo(ApprovalRequestStatus.PENDING.name());
        assertThat(row.get("REQUESTED_AT")).isNotNull();
        assertThat(row.get("EXPIRES_AT")).isNull();
        assertThat(row.get("DECIDED_BY")).isNull();
        assertThat(row.get("DECIDED_AT")).isNull();
        assertThat(row.get("DECISION_COMMENT")).isNull();
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createApprovalRequestSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_approval_request (
                    approval_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    step_id VARCHAR(64),
                    tool_invocation_id VARCHAR(64),
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64),
                    tool_id VARCHAR(128) NOT NULL,
                    approval_type VARCHAR(32) NOT NULL,
                    risk_level VARCHAR(32) NOT NULL,
                    summary VARCHAR(1000) NOT NULL,
                    arguments_preview_json CLOB,
                    status VARCHAR(32) NOT NULL,
                    requested_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP,
                    decided_by VARCHAR(64),
                    decided_at TIMESTAMP,
                    decision_comment VARCHAR(1000)
                )
                """);
    }
}
