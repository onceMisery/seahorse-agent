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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAccessDecisionRepositoryAdapterTests {

    @Test
    void shouldRecordAccessDecision() {
        DriverManagerDataSource dataSource = dataSource("access-decision-record");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createAccessDecisionSchema(jdbcTemplate);
        JdbcAccessDecisionRepositoryAdapter adapter = new JdbcAccessDecisionRepositoryAdapter(dataSource);

        adapter.record(decision("decision-1", "memory-1", AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH, Instant.parse("2026-05-24T00:00:00Z")));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT decision_id, tenant_id, subject_type, subject_id, action, resource_type, resource_id,
                       effect, reason_code, created_at
                FROM sa_access_decision_log
                WHERE decision_id = ?
                """, "decision-1");

        assertThat(row.get("TENANT_ID")).isEqualTo("tenant-1");
        assertThat(row.get("SUBJECT_TYPE")).isEqualTo(AccessSubjectType.USER_DELEGATED_AGENT.name());
        assertThat(row.get("SUBJECT_ID")).isEqualTo("user-1");
        assertThat(row.get("ACTION")).isEqualTo(ResourceAction.READ.name());
        assertThat(row.get("RESOURCE_TYPE")).isEqualTo(ContextResourceType.MEMORY.value());
        assertThat(row.get("RESOURCE_ID")).isEqualTo("memory-1");
        assertThat(row.get("EFFECT")).isEqualTo(AccessDecisionEffect.ALLOW.name());
        assertThat(row.get("REASON_CODE")).isEqualTo(ResourceAccessReasonCodes.OWNER_MATCH);
        assertThat(row.get("CREATED_AT")).isNotNull();
    }

    @Test
    void shouldPageAccessDecisionsByFilters() {
        DriverManagerDataSource dataSource = dataSource("access-decision-query");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createAccessDecisionSchema(jdbcTemplate);
        JdbcAccessDecisionRepositoryAdapter adapter = new JdbcAccessDecisionRepositoryAdapter(dataSource);
        Instant now = Instant.parse("2026-05-24T00:00:00Z");

        adapter.record(decision("decision-1", "memory-1", AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH, now));
        adapter.record(decision("decision-2", "memory-2", AccessDecisionEffect.DENY,
                ResourceAccessReasonCodes.OWNER_REQUIRED,
                now.plusSeconds(1)));
        adapter.record(decision("decision-3", "doc-1", AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.PUBLIC_RESOURCE,
                now.plusSeconds(2)));

        AccessDecisionPage page = adapter.page(new AccessDecisionQuery(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                ContextResourceType.MEMORY.value(),
                null,
                AccessDecisionEffect.ALLOW,
                null,
                1L,
                10L));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.pages()).isEqualTo(1L);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).decisionId()).isEqualTo("decision-1");
        assertThat(page.records().get(0).effect()).isEqualTo(AccessDecisionEffect.ALLOW);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createAccessDecisionSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_access_decision_log (
                    decision_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    subject_type VARCHAR(32) NOT NULL,
                    subject_id VARCHAR(64) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    resource_type VARCHAR(64) NOT NULL,
                    resource_id VARCHAR(128) NOT NULL,
                    effect VARCHAR(32) NOT NULL,
                    reason_code VARCHAR(128),
                    created_at TIMESTAMP NOT NULL
                )
                """);
    }

    private static AccessDecision decision(String decisionId,
                                           String resourceId,
                                           AccessDecisionEffect effect,
                                           String reasonCode,
                                           Instant createdAt) {
        return new AccessDecision(
                decisionId,
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                resourceId.startsWith("doc-") ? ContextResourceType.DOCUMENT.value() : ContextResourceType.MEMORY.value(),
                resourceId,
                effect,
                reasonCode,
                createdAt);
    }
}
