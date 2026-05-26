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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentRolloutRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveUpdateAndFindRolloutById() {
        DriverManagerDataSource dataSource = dataSource("agent-rollout-id");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRolloutSchema(jdbcTemplate);
        JdbcAgentRolloutRepositoryAdapter adapter = new JdbcAgentRolloutRepositoryAdapter(dataSource);
        AgentVersionRollout running = rollout(
                "rollout-1",
                "tenant-a",
                "agent-1",
                "version-1",
                AgentRolloutStatus.RUNNING,
                null,
                null,
                NOW);

        adapter.save(running);
        adapter.save(running.fail(AgentRolloutFailureCode.GATE_FAILED, NOW.plusSeconds(60)));

        AgentVersionRollout saved = adapter.findById("rollout-1").orElseThrow();
        assertThat(saved.status()).isEqualTo(AgentRolloutStatus.FAILED);
        assertThat(saved.failureCode()).isEqualTo(AgentRolloutFailureCode.GATE_FAILED);
        assertThat(saved.finishedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sa_agent_version_rollout WHERE rollout_id = ?",
                Long.class,
                "rollout-1")).isEqualTo(1L);
    }

    @Test
    void shouldFindLatestByTenantAgentAndVersion() {
        DriverManagerDataSource dataSource = dataSource("agent-rollout-latest");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createRolloutSchema(jdbcTemplate);
        JdbcAgentRolloutRepositoryAdapter adapter = new JdbcAgentRolloutRepositoryAdapter(dataSource);

        adapter.save(rollout("rollout-1", "tenant-a", "agent-1", "version-1",
                AgentRolloutStatus.RUNNING, null, null, NOW));
        adapter.save(rollout("rollout-3", "tenant-a", "agent-1", "version-2",
                AgentRolloutStatus.RUNNING, null, null, NOW.plusSeconds(120)));
        adapter.save(rollout("rollout-2", "tenant-a", "agent-1", "version-1",
                AgentRolloutStatus.PROMOTED, null, "gate-2", NOW.plusSeconds(60)));

        AgentVersionRollout latest = adapter.findLatest("tenant-a", "agent-1", "version-1").orElseThrow();

        assertThat(latest.rolloutId()).isEqualTo("rollout-2");
        assertThat(latest.status()).isEqualTo(AgentRolloutStatus.PROMOTED);
        assertThat(latest.gateReportId()).isEqualTo("gate-2");
        assertThat(adapter.findLatest("tenant-a", "agent-1", "missing")).isEmpty();
    }

    static void createRolloutSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_version_rollout (
                    rollout_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64) NOT NULL,
                    canary_percent INT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    failure_code VARCHAR(64),
                    gate_report_id VARCHAR(64),
                    started_by VARCHAR(64) NOT NULL,
                    started_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_version_rollout_latest
                ON sa_agent_version_rollout(tenant_id, agent_id, version_id, updated_at DESC, rollout_id DESC)
                """);
    }

    private static AgentVersionRollout rollout(String rolloutId,
                                               String tenantId,
                                               String agentId,
                                               String versionId,
                                               AgentRolloutStatus status,
                                               AgentRolloutFailureCode failureCode,
                                               String gateReportId,
                                               Instant updatedAt) {
        Instant finishedAt = status.terminal() ? updatedAt : null;
        return new AgentVersionRollout(
                rolloutId,
                tenantId,
                agentId,
                versionId,
                AgentRolloutLimits.DEFAULT_CANARY_PERCENT,
                status,
                failureCode,
                gateReportId,
                "operator-1",
                NOW,
                updatedAt,
                finishedAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
