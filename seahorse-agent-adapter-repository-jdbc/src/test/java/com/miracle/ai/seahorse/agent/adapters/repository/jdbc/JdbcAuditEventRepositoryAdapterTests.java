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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAuditEventRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveFindAndPageAuditEventsByStableFilters() {
        DriverManagerDataSource dataSource = dataSource("audit-event");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createAuditSchema(jdbcTemplate);
        JdbcAuditEventRepositoryAdapter adapter = new JdbcAuditEventRepositoryAdapter(dataSource);
        AuditEvent first = event(
                "audit-1",
                "tenant-a",
                AuditEventType.TOOL_INVOKED,
                "run-1",
                "agent-1",
                "tool",
                "weather",
                NOW);
        AuditEvent second = event(
                "audit-2",
                "tenant-a",
                AuditEventType.APPROVAL_DECIDED,
                "run-2",
                "agent-1",
                "approval",
                "approval-1",
                NOW.plusSeconds(60));
        AuditEvent third = event(
                "audit-3",
                "tenant-b",
                AuditEventType.TOOL_INVOKED,
                "run-3",
                "agent-2",
                "tool",
                "weather",
                NOW.plusSeconds(120));

        adapter.save(first);
        adapter.save(second);
        adapter.save(third);

        assertThat(adapter.findById("audit-1")).contains(first);
        assertThat(adapter.findById("missing")).isEmpty();

        AuditEventPage filtered = adapter.page(new AuditEventQuery(
                "tenant-a",
                "run-1",
                "agent-1",
                "tool",
                "weather",
                AuditEventType.TOOL_INVOKED,
                NOW.minusSeconds(1),
                NOW.plusSeconds(1),
                1,
                10));
        assertThat(filtered.records()).containsExactly(first);
        assertThat(filtered.total()).isEqualTo(1L);

        AuditEventPage tenantPage = adapter.page(new AuditEventQuery(
                "tenant-a",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                10));
        assertThat(tenantPage.records()).extracting(AuditEvent::auditId)
                .containsExactly("audit-2", "audit-1");
        assertThat(tenantPage.total()).isEqualTo(2L);
    }

    static void createAuditSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_audit_event (
                    audit_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    actor_type VARCHAR(32) NOT NULL,
                    actor_id VARCHAR(64) NOT NULL,
                    run_id VARCHAR(64),
                    agent_id VARCHAR(64),
                    resource_type VARCHAR(64),
                    resource_id VARCHAR(128),
                    redacted_payload CLOB NOT NULL,
                    occurred_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_audit_event_tenant_time
                ON sa_audit_event(tenant_id, occurred_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_audit_event_run
                ON sa_audit_event(run_id, occurred_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_audit_event_resource
                ON sa_audit_event(tenant_id, resource_type, resource_id, occurred_at)
                """);
    }

    private static AuditEvent event(String auditId,
                                    String tenantId,
                                    AuditEventType eventType,
                                    String runId,
                                    String agentId,
                                    String resourceType,
                                    String resourceId,
                                    Instant occurredAt) {
        return new AuditEvent(
                auditId,
                tenantId,
                eventType,
                AuditActorType.SYSTEM,
                "system",
                runId,
                agentId,
                resourceType,
                resourceId,
                "{\"safe\":\"ok\"}",
                occurredAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
