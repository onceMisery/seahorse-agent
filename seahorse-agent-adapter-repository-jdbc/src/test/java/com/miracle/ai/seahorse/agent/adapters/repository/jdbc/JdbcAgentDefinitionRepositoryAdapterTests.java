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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentDefinitionRepositoryAdapterTests {

    @Test
    void shouldCreateUpdateFindAndPageDefinitions() {
        DriverManagerDataSource dataSource = dataSource("agent-definition-crud");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createDefinitionSchema(jdbcTemplate);
        JdbcAgentDefinitionRepositoryAdapter adapter = new JdbcAgentDefinitionRepositoryAdapter(dataSource);
        Instant now = Instant.parse("2026-05-23T00:00:00Z");
        AgentDefinition draft = new AgentDefinition("agent-1", "tenant-a", "Agent One", "desc", "owner-1",
                "platform", AgentType.WORKFLOW, null, AgentStatus.DRAFT, AgentRiskLevel.HIGH, null, now, now);

        adapter.create(draft);
        adapter.update(draft.publish("agent-1-v1", now.plusSeconds(60)));
        AgentDefinition found = adapter.findById("agent-1").orElseThrow();
        AgentDefinitionPage page = adapter.page("tenant-a", 1, 10, "Agent");

        assertThat(found.status()).isEqualTo(AgentStatus.PUBLISHED);
        assertThat(found.latestVersionId()).isEqualTo("agent-1-v1");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(AgentDefinition::agentId).containsExactly("agent-1");
    }

    @Test
    void shouldSaveVersionsAndReturnLatestVersionNo() {
        DriverManagerDataSource dataSource = dataSource("agent-version");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createVersionSchema(jdbcTemplate);
        JdbcAgentDefinitionRepositoryAdapter adapter = new JdbcAgentDefinitionRepositoryAdapter(dataSource);
        Instant now = Instant.parse("2026-05-23T00:00:00Z");

        assertThat(adapter.nextVersionNo("agent-1")).isEqualTo(1L);
        adapter.saveVersion(version("agent-1-v1", "agent-1", 1L, now));
        adapter.saveVersion(version("agent-1-v2", "agent-1", 2L, now.plusSeconds(60)));

        assertThat(adapter.nextVersionNo("agent-1")).isEqualTo(3L);
        AgentVersion latest = adapter.latestVersion("agent-1").orElseThrow();
        assertThat(latest.versionId()).isEqualTo("agent-1-v2");
        assertThat(latest.versionNo()).isEqualTo(2L);
        assertThat(latest.skillSetJson()).isEqualTo("{\"skills\":[]}");
        AgentVersion first = adapter.findVersion("agent-1", "agent-1-v1").orElseThrow();
        assertThat(first.versionNo()).isEqualTo(1L);
        assertThat(first.skillSetJson()).isEqualTo("{\"skills\":[]}");
        assertThat(adapter.findVersion("agent-1", "missing")).isEmpty();
    }

    private AgentVersion version(String versionId, String agentId, long versionNo, Instant publishedAt) {
        return new AgentVersion(versionId, agentId, versionNo, "instructions", "{}", "{}", "{}",
                "{}", "{\"skills\":[]}", "admin-1", publishedAt, "release");
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createDefinitionSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_definition (
                    agent_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    name VARCHAR(80) NOT NULL,
                    description VARCHAR(500),
                    owner_user_id VARCHAR(64) NOT NULL,
                    owner_team VARCHAR(128),
                    agent_type VARCHAR(32) NOT NULL,
                    base_agent_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    risk_level VARCHAR(32) NOT NULL,
                    latest_version_id VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_definition_tenant_status
                ON sa_agent_definition(tenant_id, status)
                """);
    }

    static void createVersionSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_version (
                    version_id VARCHAR(64) PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    version_no BIGINT NOT NULL,
                    instructions CLOB NOT NULL,
                    tool_set_json CLOB NOT NULL,
                    model_config_json CLOB NOT NULL,
                    memory_config_json CLOB NOT NULL,
                    guardrail_config_json CLOB NOT NULL,
                    skill_set_json CLOB NOT NULL,
                    published_by VARCHAR(64) NOT NULL,
                    published_at TIMESTAMP NOT NULL,
                    change_summary VARCHAR(500) NOT NULL,
                    UNIQUE(agent_id, version_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_version_agent
                ON sa_agent_version(agent_id, version_no)
                """);
    }
}
