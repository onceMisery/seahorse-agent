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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcToolApprovalRequestRepositoryAdapterTests {

    private static final Instant REQUESTED_AT = Instant.parse("2026-05-23T00:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-05-23T00:05:00Z");

    @Test
    void shouldSavePendingApprovalRequest() {
        DriverManagerDataSource dataSource = dataSource("tool-approval-request");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createApprovalRequestSchema(jdbcTemplate);
        JdbcToolApprovalRequestRepositoryAdapter adapter = new JdbcToolApprovalRequestRepositoryAdapter(dataSource);

        adapter.save(pendingApproval(
                "approval-1",
                "run-1",
                "invocation-1",
                "tenant-1",
                REQUESTED_AT));

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

    @Test
    void shouldFindAndPagePendingApprovalRequests() {
        DriverManagerDataSource dataSource = dataSource("tool-approval-query");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createApprovalRequestSchema(jdbcTemplate);
        JdbcToolApprovalRequestRepositoryAdapter adapter = new JdbcToolApprovalRequestRepositoryAdapter(dataSource);
        adapter.save(pendingApproval("approval-1", "tenant-1", REQUESTED_AT));
        adapter.save(pendingApproval("approval-2", "tenant-1", REQUESTED_AT.plusSeconds(60)));
        adapter.save(pendingApproval("approval-3", "tenant-2", REQUESTED_AT.plusSeconds(120)));

        Optional<ApprovalRequest> found = adapter.findById("approval-1");
        ApprovalRequestPage page = adapter.page(new ApprovalRequestQuery(
                "tenant-1",
                ApprovalRequestStatus.PENDING,
                1L,
                10L));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().approvalId()).isEqualTo("approval-1");
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records())
                .extracting(ApprovalRequest::approvalId)
                .containsExactly("approval-2", "approval-1");
    }

    @Test
    void shouldFindLatestApprovalByRunAndStep() {
        DriverManagerDataSource dataSource = dataSource("tool-approval-run-step");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createApprovalRequestSchema(jdbcTemplate);
        JdbcToolApprovalRequestRepositoryAdapter adapter = new JdbcToolApprovalRequestRepositoryAdapter(dataSource);
        adapter.save(pendingApproval("approval-old", "run-1", "invocation-old", "tenant-1", REQUESTED_AT));
        adapter.save(pendingApproval("approval-new", "run-1", "invocation-new", "tenant-1", REQUESTED_AT.plusSeconds(60)));
        adapter.save(pendingApproval("approval-other", "run-2", "invocation-other", "tenant-1", REQUESTED_AT.plusSeconds(120)));

        Optional<ApprovalRequest> found = adapter.findLatestByRunIdAndStepId("run-1", "step-1");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().approvalId()).isEqualTo("approval-new");
        assertThat(adapter.findLatestByRunIdAndStepId("run-1", "missing-step")).isEmpty();
    }

    @Test
    void shouldDecidePendingApprovalWithOptimisticStatusUpdate() {
        DriverManagerDataSource dataSource = dataSource("tool-approval-decide");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createApprovalRequestSchema(jdbcTemplate);
        JdbcToolApprovalRequestRepositoryAdapter adapter = new JdbcToolApprovalRequestRepositoryAdapter(dataSource);
        adapter.save(pendingApproval("approval-1", "tenant-1", REQUESTED_AT));

        Optional<ApprovalRequest> decided = adapter.decide(new ApprovalRequestDecision(
                "approval-1",
                ApprovalRequestStatus.PENDING,
                ApprovalRequestStatus.APPROVED,
                "admin-1",
                DECIDED_AT,
                "Looks safe",
                null));
        Optional<ApprovalRequest> staleDecision = adapter.decide(new ApprovalRequestDecision(
                "approval-1",
                ApprovalRequestStatus.PENDING,
                ApprovalRequestStatus.REJECTED,
                "admin-2",
                DECIDED_AT.plusSeconds(1),
                "Too late",
                null));

        assertThat(decided).isPresent();
        assertThat(decided.orElseThrow().status()).isEqualTo(ApprovalRequestStatus.APPROVED);
        assertThat(decided.orElseThrow().decidedBy()).isEqualTo("admin-1");
        assertThat(decided.orElseThrow().decidedAt()).isEqualTo(DECIDED_AT);
        assertThat(decided.orElseThrow().decisionComment()).isEqualTo("Looks safe");
        assertThat(staleDecision).isEmpty();
    }

    @Test
    void shouldModifyArgumentsPreviewWhenDecisionCarriesPreview() {
        DriverManagerDataSource dataSource = dataSource("tool-approval-modify");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createApprovalRequestSchema(jdbcTemplate);
        JdbcToolApprovalRequestRepositoryAdapter adapter = new JdbcToolApprovalRequestRepositoryAdapter(dataSource);
        adapter.save(pendingApproval("approval-1", "tenant-1", REQUESTED_AT));

        Optional<ApprovalRequest> decided = adapter.decide(new ApprovalRequestDecision(
                "approval-1",
                ApprovalRequestStatus.PENDING,
                ApprovalRequestStatus.MODIFIED,
                "admin-1",
                DECIDED_AT,
                "Reduced scope",
                "{\"argumentKeys\":[\"input\"],\"modified\":true}"));

        assertThat(decided).isPresent();
        assertThat(decided.orElseThrow().status()).isEqualTo(ApprovalRequestStatus.MODIFIED);
        assertThat(decided.orElseThrow().argumentsPreviewJson())
                .isEqualTo("{\"argumentKeys\":[\"input\"],\"modified\":true}");
        assertThat(decided.orElseThrow().decisionComment()).isEqualTo("Reduced scope");
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

    private ApprovalRequest pendingApproval(String approvalId, String tenantId, Instant requestedAt) {
        return pendingApproval(
                approvalId,
                "run-" + approvalId,
                "invocation-" + approvalId,
                tenantId,
                requestedAt);
    }

    private ApprovalRequest pendingApproval(String approvalId,
                                            String runId,
                                            String toolInvocationId,
                                            String tenantId,
                                            Instant requestedAt) {
        return new ApprovalRequest(
                approvalId,
                runId,
                "step-1",
                toolInvocationId,
                tenantId,
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
                null);
    }
}
