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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentRunRepositoryAdapterTests {

    @Test
    void shouldCreateUpdateAndFindRun() {
        DriverManagerDataSource dataSource = dataSource("agent-run-crud");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter adapter = new JdbcAgentRunRepositoryAdapter(dataSource);
        Instant startedAt = Instant.parse("2026-05-23T00:00:00Z");
        AgentRun run = new AgentRun("run-1", "agent-1", "version-1", null, "tenant-a", "user-1",
                "conversation-1", AgentRunTriggerType.CHAT, "summary", AgentRunStatus.RUNNING, "trace-1",
                3L, 5L, new BigDecimal("0.010000"), null, null, startedAt, null,
                "{\"agentVersion\":{\"versionId\":\"version-1\"}}");

        adapter.createRun(run);
        adapter.updateRun(run.withStatus(AgentRunStatus.SUCCEEDED, null, null, startedAt.plusSeconds(30)));
        AgentRun found = adapter.findRunById("run-1").orElseThrow();

        assertThat(found.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(found.finishedAt()).isEqualTo(startedAt.plusSeconds(30));
        assertThat(found.costTotal()).isEqualByComparingTo("0.010000");
        assertThat(found.metadataJson()).contains("\"versionId\":\"version-1\"");
    }

    @Test
    @EnabledIfSystemProperty(named = "seahorse.postgres.contract", matches = "true")
    void shouldPersistRunMetadataAsPostgresJsonb() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getProperty("seahorse.postgres.url", "jdbc:postgresql://localhost:5432/seahorse"),
                System.getProperty("seahorse.postgres.username", "seahorse"),
                System.getProperty("seahorse.postgres.password", "seahorse"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcAgentRunRepositoryAdapter adapter = new JdbcAgentRunRepositoryAdapter(dataSource);
        String runId = "agent-run-jsonb-contract-" + UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-21T00:00:00Z");
        try {
            adapter.createRun(new AgentRun(
                    runId,
                    "agent-jsonb",
                    "version-jsonb",
                    "rollout-jsonb",
                    "tenant-a",
                    "user-jsonb",
                    "conversation-jsonb",
                    AgentRunTriggerType.CHAT,
                    "jsonb contract",
                    AgentRunStatus.RUNNING,
                    "trace-jsonb",
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    null,
                    null,
                    startedAt,
                    null,
                    "{\"prompt\":{\"source\":\"nacos\"}}"));

            String jsonType = jdbcTemplate.queryForObject("""
                    SELECT jsonb_typeof(metadata_json)
                    FROM sa_agent_run
                    WHERE run_id = ?
                    """, String.class, runId);
            String promptSource = jdbcTemplate.queryForObject("""
                    SELECT metadata_json -> 'prompt' ->> 'source'
                    FROM sa_agent_run
                    WHERE run_id = ?
                    """, String.class, runId);

            assertThat(jsonType).isEqualTo("object");
            assertThat(promptSource).isEqualTo("nacos");
        } finally {
            jdbcTemplate.update("DELETE FROM sa_agent_run WHERE run_id = ?", runId);
        }
    }

    @Test
    void shouldPageRunsWithFiltersInRecentOrder() {
        DriverManagerDataSource dataSource = dataSource("agent-run-page");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter adapter = new JdbcAgentRunRepositoryAdapter(dataSource);
        Instant startedAt = Instant.parse("2026-05-23T00:00:00Z");
        adapter.createRun(new AgentRun("run-older", "agent-1", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "older", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt, null));
        adapter.createRun(new AgentRun("run-newer", "agent-1", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "newer", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(60), null));
        adapter.createRun(new AgentRun("run-failed", "agent-1", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "failed", AgentRunStatus.FAILED, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(120), null));
        adapter.createRun(new AgentRun("run-other-agent", "agent-2", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "other", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(180), null));

        AgentRunPage page = adapter.page(new AgentRunQuery(
                "agent-1",
                "run-",
                "RUNNING",
                null,
                null,
                1L,
                10L));

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.pages()).isEqualTo(1L);
        assertThat(page.records()).extracting(AgentRun::runId).containsExactly("run-newer", "run-older");
    }

    @Test
    void shouldPageRunsByTenantAndStartedWindow() {
        DriverManagerDataSource dataSource = dataSource("agent-run-window");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter adapter = new JdbcAgentRunRepositoryAdapter(dataSource);
        Instant startedAt = Instant.parse("2026-05-23T00:00:00Z");
        adapter.createRun(new AgentRun("run-in-window", "agent-1", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "in", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(60), null));
        adapter.createRun(new AgentRun("run-other-tenant", "agent-1", "version-1", "tenant-b", "user-1",
                null, AgentRunTriggerType.API, "tenant", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(60), null));
        adapter.createRun(new AgentRun("run-too-old", "agent-1", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "old", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.minusSeconds(60), null));
        adapter.createRun(new AgentRun("run-failed", "agent-1", "version-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "failed", AgentRunStatus.FAILED, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(60), null));

        AgentRunPage page = adapter.page(new AgentRunQuery(
                "tenant-a",
                "agent-1",
                null,
                "RUNNING",
                startedAt,
                startedAt.plusSeconds(120),
                1L,
                10L));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).extracting(AgentRun::runId).containsExactly("run-in-window");
    }

    @Test
    void shouldPageRunsByExactRolloutId() {
        DriverManagerDataSource dataSource = dataSource("agent-run-rollout");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRunSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter adapter = new JdbcAgentRunRepositoryAdapter(dataSource);
        Instant startedAt = Instant.parse("2026-05-23T00:00:00Z");
        adapter.createRun(new AgentRun("run-rollout-1", "agent-1", "version-1", "rollout-1", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "rollout", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(60), null));
        adapter.createRun(new AgentRun("run-rollout-2", "agent-1", "version-1", "rollout-2", "tenant-a", "user-1",
                null, AgentRunTriggerType.API, "other rollout", AgentRunStatus.RUNNING, null,
                0L, 0L, BigDecimal.ZERO, null, null, startedAt.plusSeconds(90), null));

        AgentRunPage page = adapter.page(new AgentRunQuery(
                "tenant-a",
                "agent-1",
                null,
                "rollout-1",
                "RUNNING",
                startedAt,
                startedAt.plusSeconds(120),
                1L,
                10L));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).extracting(AgentRun::runId).containsExactly("run-rollout-1");
        assertThat(page.records().get(0).rolloutId()).isEqualTo("rollout-1");
    }

    @Test
    void shouldAppendAndListStepsInStepOrder() {
        DriverManagerDataSource dataSource = dataSource("agent-step");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createStepSchema(jdbcTemplate);
        JdbcAgentRunRepositoryAdapter adapter = new JdbcAgentRunRepositoryAdapter(dataSource);
        Instant now = Instant.parse("2026-05-23T00:00:00Z");

        adapter.appendStep(new AgentStep("step-2", "run-1", 2, AgentStepType.TOOL_CALL, AgentStepStatus.SUCCEEDED,
                "{\"tool\":\"search\"}", "{\"ok\":true}", null, null, now.plusSeconds(2), now.plusSeconds(3)));
        adapter.appendStep(new AgentStep("step-1", "run-1", 1, AgentStepType.MODEL_TURN, AgentStepStatus.SUCCEEDED,
                "{\"prompt\":\"hi\"}", "{\"answer\":\"call\"}", null, null, now, now.plusSeconds(1)));
        adapter.appendStep(new AgentStep("step-other", "run-2", 1, AgentStepType.MODEL_TURN, AgentStepStatus.SUCCEEDED,
                null, null, null, null, now, now.plusSeconds(1)));

        List<AgentStep> steps = adapter.listSteps("run-1");

        assertThat(steps).extracting(AgentStep::stepId).containsExactly("step-1", "step-2");
        assertThat(steps.get(1).stepType()).isEqualTo(AgentStepType.TOOL_CALL);
        assertThat(steps.get(1).outputJson()).isEqualTo("{\"ok\":true}");
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createRunSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_run (
                    run_id VARCHAR(64) PRIMARY KEY,
                    agent_id VARCHAR(64),
                    version_id VARCHAR(64),
                    rollout_id VARCHAR(64),
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    conversation_id VARCHAR(64),
                    trigger_type VARCHAR(32) NOT NULL,
                    input_summary VARCHAR(1000),
                    status VARCHAR(32) NOT NULL,
                    trace_id VARCHAR(64),
                    token_input BIGINT NOT NULL DEFAULT 0,
                    token_output BIGINT NOT NULL DEFAULT 0,
                    cost_total DECIMAL(18,6) NOT NULL DEFAULT 0,
                    error_code VARCHAR(128),
                    error_message VARCHAR(1000),
                    started_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP,
                    metadata_json CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_run_agent_status
                ON sa_agent_run(agent_id, status, started_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_run_user
                ON sa_agent_run(tenant_id, user_id, started_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_run_rollout
                ON sa_agent_run(tenant_id, agent_id, rollout_id, started_at)
                """);
    }

    static void createStepSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_step (
                    step_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    step_no INT NOT NULL,
                    step_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    input_json CLOB,
                    output_json CLOB,
                    error_code VARCHAR(128),
                    error_message VARCHAR(1000),
                    started_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP,
                    UNIQUE(run_id, step_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_step_run
                ON sa_agent_step(run_id, step_no)
                """);
    }
}
