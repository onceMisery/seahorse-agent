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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentHandoffRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveListFindAndUpdateTerminalIdempotently() {
        DriverManagerDataSource dataSource = dataSource("agent-handoff");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createHandoffSchema(jdbcTemplate);
        JdbcAgentHandoffRepositoryAdapter adapter = new JdbcAgentHandoffRepositoryAdapter(dataSource);

        AgentHandoff running = adapter.save(handoff("handoff-1", "parent-run-1", NOW)
                .running("child-run-1", NOW.plusSeconds(1)));
        adapter.save(handoff("handoff-2", "parent-run-1", NOW.plusSeconds(2))
                .fail(AgentHandoffFailureCode.POLICY_DENIED, NOW.plusSeconds(3)));
        adapter.save(handoff("handoff-3", "other-parent", NOW.plusSeconds(4)));

        AgentHandoff cancelled = adapter.update(running.cancel(NOW.plusSeconds(5)));
        AgentHandoff unchanged = adapter.update(cancelled.fail(
                AgentHandoffFailureCode.CHILD_RUN_FAILED, NOW.plusSeconds(6)));

        assertThat(unchanged.status()).isEqualTo(AgentHandoffStatus.CANCELLED);
        assertThat(unchanged.failureCode()).isNull();
        assertThat(adapter.findById("handoff-1")).contains(unchanged);
        assertThat(adapter.listByParentRunId("tenant-a", "parent-run-1"))
                .extracting(AgentHandoff::handoffId)
                .containsExactly("handoff-1", "handoff-2");
        assertThat(adapter.listByParentRunId("tenant-a", "missing")).isEmpty();
    }

    private static AgentHandoff handoff(String handoffId, String parentRunId, Instant createdAt) {
        return new AgentHandoff(
                handoffId,
                "tenant-a",
                parentRunId,
                null,
                "source-agent",
                "target-agent",
                AgentHandoffStatus.CREATED,
                null,
                "delegate summary",
                "{\"inputSummary\":\"safe\"}",
                "{\"summary\":\"context\"}",
                createdAt,
                createdAt,
                null);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    static void createHandoffSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_handoff (
                    handoff_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    parent_run_id VARCHAR(64) NOT NULL,
                    child_run_id VARCHAR(64),
                    source_agent_id VARCHAR(64) NOT NULL,
                    target_agent_id VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    failure_code VARCHAR(64),
                    handoff_reason VARCHAR(1000),
                    input_summary_json CLOB NOT NULL,
                    context_summary_json CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_handoff_parent
                ON sa_agent_handoff(tenant_id, parent_run_id, created_at)
                """);
    }
}
