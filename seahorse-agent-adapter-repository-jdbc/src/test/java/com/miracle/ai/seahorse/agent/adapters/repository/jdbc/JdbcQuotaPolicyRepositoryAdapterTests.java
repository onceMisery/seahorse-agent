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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcQuotaPolicyRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldUpsertFindLatestActiveAndDisablePolicy() {
        DriverManagerDataSource dataSource = dataSource("quota-policy");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createQuotaPolicySchema(jdbcTemplate);
        JdbcQuotaPolicyRepositoryAdapter adapter = new JdbcQuotaPolicyRepositoryAdapter(dataSource);

        adapter.upsert(policy("policy-old", QuotaScope.AGENT, "agent-1", 1_000L, NOW));
        adapter.upsert(policy("policy-new", QuotaScope.AGENT, "agent-1", 2_000L, NOW.plusSeconds(60)));

        QuotaPolicy latest = adapter.findActive("tenant-a", QuotaScope.AGENT, "agent-1").orElseThrow();
        assertThat(latest.policyId()).isEqualTo("policy-new");
        assertThat(latest.tokenLimit()).isEqualTo(2_000L);

        QuotaPolicy updated = policy("policy-new", QuotaScope.AGENT, "agent-1", 3_000L, NOW.plusSeconds(120));
        adapter.upsert(updated);
        assertThat(adapter.findActive("tenant-a", QuotaScope.AGENT, "agent-1").orElseThrow().tokenLimit())
                .isEqualTo(3_000L);

        adapter.disable("policy-new", NOW.plusSeconds(180));
        assertThat(adapter.findActive("tenant-a", QuotaScope.AGENT, "agent-1").orElseThrow().policyId())
                .isEqualTo("policy-old");
        adapter.disable("policy-old", NOW.plusSeconds(181));
        assertThat(adapter.findActive("tenant-a", QuotaScope.AGENT, "agent-1")).isEmpty();
    }

    @Test
    void shouldRejectInvalidEnumAndNegativeLimitsAtDatabaseBoundary() {
        DriverManagerDataSource dataSource = dataSource("quota-policy-invalid");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createQuotaPolicySchema(jdbcTemplate);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sa_quota_policy
                        (policy_id, tenant_id, scope, subject_id, status, token_limit, call_limit,
                         cost_limit, warn_ratio, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "invalid-scope",
                "tenant-a",
                "UNKNOWN",
                "agent-1",
                QuotaPolicyStatus.ACTIVE.name(),
                100L,
                null,
                null,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO sa_quota_policy
                        (policy_id, tenant_id, scope, subject_id, status, token_limit, call_limit,
                         cost_limit, warn_ratio, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "negative-limit",
                "tenant-a",
                QuotaScope.AGENT.name(),
                "agent-1",
                QuotaPolicyStatus.ACTIVE.name(),
                -1L,
                null,
                null,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW))
                .isInstanceOf(RuntimeException.class);
    }

    static void createQuotaPolicySchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_quota_policy (
                    policy_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    scope VARCHAR(32) NOT NULL,
                    subject_id VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    token_limit BIGINT,
                    call_limit BIGINT,
                    cost_limit DOUBLE PRECISION,
                    warn_ratio DOUBLE PRECISION NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    CONSTRAINT chk_sa_quota_policy_scope CHECK (scope IN ('TENANT', 'AGENT', 'USER', 'TOOL', 'MODEL', 'RUN')),
                    CONSTRAINT chk_sa_quota_policy_status CHECK (status IN ('ACTIVE', 'DISABLED')),
                    CONSTRAINT chk_sa_quota_policy_limit_required CHECK (token_limit IS NOT NULL OR call_limit IS NOT NULL OR cost_limit IS NOT NULL),
                    CONSTRAINT chk_sa_quota_policy_token_limit CHECK (token_limit IS NULL OR token_limit >= 0),
                    CONSTRAINT chk_sa_quota_policy_call_limit CHECK (call_limit IS NULL OR call_limit >= 0),
                    CONSTRAINT chk_sa_quota_policy_cost_limit CHECK (cost_limit IS NULL OR cost_limit >= 0),
                    CONSTRAINT chk_sa_quota_policy_warn_ratio CHECK (warn_ratio > 0 AND warn_ratio <= 1)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_quota_policy_active
                ON sa_quota_policy(tenant_id, scope, subject_id, status, updated_at DESC, policy_id DESC)
                """);
    }

    private static QuotaPolicy policy(String policyId,
                                      QuotaScope scope,
                                      String subjectId,
                                      long tokenLimit,
                                      Instant updatedAt) {
        return new QuotaPolicy(
                policyId,
                "tenant-a",
                scope,
                subjectId,
                QuotaPolicyStatus.ACTIVE,
                tokenLimit,
                100L,
                12.5d,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                updatedAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
