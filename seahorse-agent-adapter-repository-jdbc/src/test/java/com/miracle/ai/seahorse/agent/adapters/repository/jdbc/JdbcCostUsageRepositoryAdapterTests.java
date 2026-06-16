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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCostUsageRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldAppendAndAggregateUsageByTenantAgentRunAndWindow() {
        DriverManagerDataSource dataSource = dataSource("cost-usage");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createCostUsageSchema(jdbcTemplate);
        JdbcCostUsageRepositoryAdapter adapter = new JdbcCostUsageRepositoryAdapter(dataSource);

        adapter.append(record("usage-1", "tenant-a", "agent-1", "run-1", 100L, 1L, 0.25d, NOW));
        adapter.append(record("usage-2", "tenant-a", "agent-1", "run-1", 50L, 2L, 0.10d, NOW.plusSeconds(60)));
        adapter.append(record("usage-3", "tenant-a", "agent-2", "run-2", 999L, 1L, 9.99d, NOW.plusSeconds(90)));

        CostUsageAggregate aggregate = adapter.aggregate(new CostUsageQuery(
                "tenant-a",
                "agent-1",
                "run-1",
                NOW.minusSeconds(1),
                NOW.plusSeconds(120)));

        assertThat(aggregate.totalTokens()).isEqualTo(150L);
        assertThat(aggregate.totalCalls()).isEqualTo(3L);
        assertThat(aggregate.totalCost()).isEqualTo(0.35d);
        assertThat(aggregate.recordCount()).isEqualTo(2L);

        assertThatThrownBy(() -> adapter.append(record(
                "usage-1",
                "tenant-a",
                "agent-1",
                "run-1",
                1L,
                1L,
                0.01d,
                NOW.plusSeconds(120))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldAggregateUsageByExactRolloutId() {
        DriverManagerDataSource dataSource = dataSource("cost-usage-rollout");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createCostUsageSchema(jdbcTemplate);
        JdbcCostUsageRepositoryAdapter adapter = new JdbcCostUsageRepositoryAdapter(dataSource);

        adapter.append(record("usage-rollout-1", "tenant-a", "agent-1", "run-1", "rollout-1",
                100L, 1L, 0.25d, NOW));
        adapter.append(record("usage-rollout-2", "tenant-a", "agent-1", "run-2", "rollout-2",
                999L, 9L, 9.99d, NOW.plusSeconds(30)));

        CostUsageAggregate aggregate = adapter.aggregate(new CostUsageQuery(
                "tenant-a",
                "agent-1",
                null,
                "rollout-1",
                NOW.minusSeconds(1),
                NOW.plusSeconds(120)));

        assertThat(aggregate.totalTokens()).isEqualTo(100L);
        assertThat(aggregate.totalCalls()).isEqualTo(1L);
        assertThat(aggregate.totalCost()).isEqualTo(0.25d);
        assertThat(aggregate.recordCount()).isEqualTo(1L);
    }

    @Test
    void shouldRejectInvalidSourceAndNegativeUsageAtDatabaseBoundary() {
        DriverManagerDataSource dataSource = dataSource("cost-usage-invalid");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createCostUsageSchema(jdbcTemplate);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sa_cost_usage_record
                        (usage_id, tenant_id, agent_id, run_id, user_id, tool_id, model_id,
                         source, tokens, calls, cost, reason_ref, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "invalid-source",
                "tenant-a",
                "agent-1",
                "run-1",
                "user-1",
                null,
                "model-1",
                "UNKNOWN",
                1L,
                1L,
                0.01d,
                null,
                NOW))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sa_cost_usage_record
                        (usage_id, tenant_id, agent_id, run_id, user_id, tool_id, model_id,
                         source, tokens, calls, cost, reason_ref, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "negative-tokens",
                "tenant-a",
                "agent-1",
                "run-1",
                "user-1",
                null,
                "model-1",
                CostUsageSource.MODEL.name(),
                -1L,
                1L,
                0.01d,
                null,
                NOW))
                .isInstanceOf(RuntimeException.class);
    }

    static void createCostUsageSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_cost_usage_record (
                    usage_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64),
                    run_id VARCHAR(64),
                    user_id VARCHAR(64),
                    tool_id VARCHAR(128),
                    model_id VARCHAR(128),
                    rollout_id VARCHAR(64),
                    source VARCHAR(32) NOT NULL,
                    tokens BIGINT NOT NULL,
                    calls BIGINT NOT NULL,
                    cost DOUBLE PRECISION NOT NULL,
                    reason_ref VARCHAR(256),
                    created_at TIMESTAMP NOT NULL,
                    CONSTRAINT chk_sa_cost_usage_source CHECK (source IN ('MODEL', 'TOOL', 'SANDBOX', 'MANUAL_ADJUSTMENT')),
                    CONSTRAINT chk_sa_cost_usage_tokens CHECK (tokens >= 0),
                    CONSTRAINT chk_sa_cost_usage_calls CHECK (calls >= 0),
                    CONSTRAINT chk_sa_cost_usage_cost CHECK (cost >= 0)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_cost_usage_aggregate
                ON sa_cost_usage_record(tenant_id, agent_id, run_id, created_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_cost_usage_rollout
                ON sa_cost_usage_record(tenant_id, agent_id, rollout_id, created_at)
                """);
    }

    private static CostUsageRecord record(String usageId,
                                          String tenantId,
                                          String agentId,
                                          String runId,
                                          long tokens,
                                          long calls,
                                          double cost,
                                          Instant createdAt) {
        return record(usageId, tenantId, agentId, runId, null, tokens, calls, cost, createdAt);
    }

    private static CostUsageRecord record(String usageId,
                                          String tenantId,
                                          String agentId,
                                          String runId,
                                          String rolloutId,
                                          long tokens,
                                          long calls,
                                          double cost,
                                          Instant createdAt) {
        return new CostUsageRecord(
                usageId,
                tenantId,
                agentId,
                runId,
                rolloutId,
                "user-1",
                "tool-1",
                "model-1",
                CostUsageSource.MODEL,
                tokens,
                calls,
                cost,
                null,
                createdAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
