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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentEvalSummaryRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAppendAndFindLatestByTenantAgentVersionAndType() {
        DriverManagerDataSource dataSource = dataSource("agent-eval-latest");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createEvalSummarySchema(jdbcTemplate);
        JdbcAgentEvalSummaryRepositoryAdapter adapter = new JdbcAgentEvalSummaryRepositoryAdapter(dataSource);

        adapter.append(summary("summary-1", "tenant-a", "agent-1", "version-1",
                AgentEvalType.SAFETY, AgentEvalStatus.WARN, NOW));
        adapter.append(summary("summary-2", "tenant-a", "agent-1", "version-2",
                AgentEvalType.SAFETY, AgentEvalStatus.PASS, NOW.plusSeconds(30)));
        adapter.append(summary("summary-3", "tenant-a", "agent-1", "version-1",
                AgentEvalType.TRAJECTORY, AgentEvalStatus.FAIL, NOW.plusSeconds(60)));
        adapter.append(summary("summary-4", "tenant-a", "agent-1", "version-1",
                AgentEvalType.SAFETY, AgentEvalStatus.PASS, NOW.plusSeconds(90)));

        AgentEvalSummary latest = adapter.findLatest(
                        "tenant-a",
                        "agent-1",
                        "version-1",
                        AgentEvalType.SAFETY)
                .orElseThrow();

        assertThat(latest.summaryId()).isEqualTo("summary-4");
        assertThat(latest.status()).isEqualTo(AgentEvalStatus.PASS);
        assertThat(latest.evidenceRefs()).containsExactly("trace:summary-4", "dashboard:summary-4");
        assertThat(adapter.findLatest("tenant-a", "agent-1", "missing", AgentEvalType.SAFETY)).isEmpty();
    }

    @Test
    void shouldKeepHistoryDescendingAndIsolatedByEvalType() {
        DriverManagerDataSource dataSource = dataSource("agent-eval-history");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createEvalSummarySchema(jdbcTemplate);
        JdbcAgentEvalSummaryRepositoryAdapter adapter = new JdbcAgentEvalSummaryRepositoryAdapter(dataSource);

        adapter.append(summary("summary-1", "tenant-a", "agent-1", "version-1",
                AgentEvalType.SAFETY, AgentEvalStatus.WARN, NOW));
        adapter.append(summary("summary-2", "tenant-a", "agent-1", "version-1",
                AgentEvalType.SAFETY, AgentEvalStatus.PASS, NOW.plusSeconds(30)));
        adapter.append(summary("summary-3", "tenant-a", "agent-1", "version-1",
                AgentEvalType.TRAJECTORY, AgentEvalStatus.FAIL, NOW.plusSeconds(60)));

        assertThat(adapter.findHistory(new AgentEvalSummaryQuery(
                        "tenant-a",
                        "agent-1",
                        "version-1",
                        AgentEvalType.SAFETY,
                        1,
                        20))
                .records())
                .extracting(AgentEvalSummary::summaryId)
                .containsExactly("summary-2", "summary-1");
    }

    static void createEvalSummarySchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_eval_summary (
                    summary_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64) NOT NULL,
                    eval_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    score DOUBLE PRECISION NOT NULL,
                    pass_threshold DOUBLE PRECISION NOT NULL,
                    warn_threshold DOUBLE PRECISION NOT NULL,
                    case_count INT NOT NULL,
                    dataset_ref VARCHAR(256),
                    eval_run_ref VARCHAR(256),
                    evidence_json CLOB NOT NULL,
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_eval_summary_latest
                ON sa_agent_eval_summary(tenant_id, agent_id, version_id, eval_type, created_at DESC, summary_id DESC)
                """);
    }

    private static AgentEvalSummary summary(String summaryId,
                                            String tenantId,
                                            String agentId,
                                            String versionId,
                                            AgentEvalType evalType,
                                            AgentEvalStatus status,
                                            Instant createdAt) {
        return new AgentEvalSummary(
                summaryId,
                tenantId,
                agentId,
                versionId,
                evalType,
                status,
                status == AgentEvalStatus.FAIL ? 0.4d : 0.95d,
                0.9d,
                0.7d,
                8,
                "dataset:v1",
                "eval-run:" + summaryId,
                List.of("trace:" + summaryId, "dashboard:" + summaryId),
                "admin-1",
                createdAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
