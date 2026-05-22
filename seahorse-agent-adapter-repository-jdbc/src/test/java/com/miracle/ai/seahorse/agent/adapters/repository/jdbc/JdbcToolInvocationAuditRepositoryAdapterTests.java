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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcToolInvocationAuditRepositoryAdapterTests {

    @Test
    void shouldRecordToolInvocationLifecycle() {
        DriverManagerDataSource dataSource = dataSource("tool-invocation-audit");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createToolInvocationSchema(jdbcTemplate);
        JdbcToolInvocationAuditRepositoryAdapter adapter = new JdbcToolInvocationAuditRepositoryAdapter(dataSource);
        Instant startedAt = Instant.parse("2026-05-23T00:00:00Z");
        Instant finishedAt = startedAt.plusSeconds(2);

        adapter.recordRequested(new ToolInvocationAuditRecord(
                "invocation-1",
                "run-1",
                "step-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "weather",
                "run-1:call-1",
                ToolInvocationStatus.REQUESTED,
                "keys=[city], size=1",
                startedAt));
        adapter.recordDecision(new ToolInvocationAuditDecision(
                "invocation-1",
                "decision-1",
                ToolInvocationStatus.ALLOWED));
        adapter.recordCompleted(new ToolInvocationAuditCompletion(
                "invocation-1",
                ToolInvocationStatus.SUCCEEDED,
                "length=11",
                null,
                finishedAt));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT invocation_id, run_id, step_id, agent_id, version_id, tenant_id, user_id, tool_id,
                       idempotency_key, status, policy_decision_id, arguments_summary, result_summary,
                       error_message, started_at, finished_at
                FROM sa_tool_invocation
                WHERE invocation_id = ?
                """, "invocation-1");

        assertThat(row.get("RUN_ID")).isEqualTo("run-1");
        assertThat(row.get("TOOL_ID")).isEqualTo("weather");
        assertThat(row.get("STATUS")).isEqualTo(ToolInvocationStatus.SUCCEEDED.name());
        assertThat(row.get("POLICY_DECISION_ID")).isEqualTo("decision-1");
        assertThat(row.get("ARGUMENTS_SUMMARY")).isEqualTo("keys=[city], size=1");
        assertThat(row.get("RESULT_SUMMARY")).isEqualTo("length=11");
        assertThat(row.get("ERROR_MESSAGE")).isNull();
        assertThat(row.get("STARTED_AT")).isNotNull();
        assertThat(row.get("FINISHED_AT")).isNotNull();
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createToolInvocationSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_tool_invocation (
                    invocation_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    step_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64),
                    version_id VARCHAR(64),
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    tool_id VARCHAR(128) NOT NULL,
                    idempotency_key VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    policy_decision_id VARCHAR(64),
                    arguments_summary CLOB,
                    result_summary CLOB,
                    error_message VARCHAR(1000),
                    started_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP
                )
                """);
    }
}
