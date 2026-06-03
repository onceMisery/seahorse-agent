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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentSkillRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-06-03T00:00:00Z");

    @Test
    void shouldSaveFindAndPageSkillsWithoutDeletedRows() {
        DriverManagerDataSource dataSource = dataSource("agent-skill-crud");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSkillSchema(jdbcTemplate);
        JdbcAgentSkillRepositoryAdapter adapter = new JdbcAgentSkillRepositoryAdapter(dataSource);

        adapter.saveSkill(skill("research-helper", AgentSkillStatus.ACTIVE, true));
        adapter.saveSkill(skill("deleted-helper", AgentSkillStatus.DELETED, false));

        AgentSkill found = adapter.findSkill("tenant-a", "research-helper").orElseThrow();
        AgentSkillPage page = adapter.page("tenant-a", 1, 10, "research");

        assertThat(found.tags()).containsExactly("analysis", "public");
        assertThat(found.allowedTools()).containsExactly("web_search", "load_skill");
        assertThat(found.enabled()).isTrue();
        assertThat(adapter.findSkill("tenant-a", "deleted-helper")).isEmpty();
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(AgentSkill::name).containsExactly("research-helper");
    }

    @Test
    void shouldSaveListFindRevisionsAndComputeNextRevisionNo() {
        DriverManagerDataSource dataSource = dataSource("agent-skill-revision");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSkillSchema(jdbcTemplate);
        JdbcAgentSkillRepositoryAdapter adapter = new JdbcAgentSkillRepositoryAdapter(dataSource);

        assertThat(adapter.nextRevisionNo("tenant-a", "research-helper")).isEqualTo(1L);
        adapter.saveRevision(revision("rev-1", 1L));
        adapter.saveRevision(revision("rev-2", 2L));

        assertThat(adapter.nextRevisionNo("tenant-a", "research-helper")).isEqualTo(3L);
        assertThat(adapter.findRevision("tenant-a", "rev-1")).hasValueSatisfying(revision ->
                assertThat(revision.frontmatterJson()).isEqualTo("{\"name\":\"research-helper\"}"));
        assertThat(adapter.listRevisions("tenant-a", "research-helper"))
                .extracting(AgentSkillRevision::revisionId)
                .containsExactly("rev-2", "rev-1");
    }

    @Test
    void shouldReplaceBindingsBySoftDeletingPreviousRows() {
        DriverManagerDataSource dataSource = dataSource("agent-skill-binding");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSkillSchema(jdbcTemplate);
        JdbcAgentSkillRepositoryAdapter adapter = new JdbcAgentSkillRepositoryAdapter(dataSource);

        adapter.replaceBindings("tenant-a", "agent-1", List.of(
                binding("agent-1", "research-helper", "rev-1", SkillInjectMode.METADATA_AND_BODY)));
        adapter.replaceBindings("tenant-a", "agent-1", List.of(
                binding("agent-1", "writing-helper", "rev-2", SkillInjectMode.METADATA_ONLY)));

        List<AgentSkillBinding> bindings = adapter.listBindings("tenant-a", "agent-1");
        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sa_agent_skill_binding WHERE tenant_id = ? AND agent_id = ? AND deleted = 1",
                Integer.class, "tenant-a", "agent-1");

        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).skillName()).isEqualTo("writing-helper");
        assertThat(bindings.get(0).injectMode()).isEqualTo(SkillInjectMode.METADATA_ONLY);
        assertThat(deletedRows).isEqualTo(1);
    }

    private AgentSkill skill(String name, AgentSkillStatus status, boolean enabled) {
        return new AgentSkill(name, "tenant-a", AgentSkillCategory.PUBLIC, AgentSkillSource.BUILT_IN, status,
                enabled, "rev-1", "Research helper", List.of("analysis", "public"),
                List.of("web_search", "load_skill"), "system", "system", NOW, NOW);
    }

    private AgentSkillRevision revision(String revisionId, long revisionNo) {
        return new AgentSkillRevision(revisionId, "research-helper", "tenant-a", revisionNo,
                "sha256-" + revisionNo, "# Research helper", "{\"name\":\"research-helper\"}",
                SkillScanDecision.ALLOW, "{\"decision\":\"ALLOW\"}", "system", NOW.plusSeconds(revisionNo));
    }

    private AgentSkillBinding binding(String agentId, String skillName, String revisionId, SkillInjectMode injectMode) {
        return new AgentSkillBinding(agentId, "tenant-a", skillName, revisionId, injectMode, "admin-1", NOW);
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createSkillSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_skill (
                    skill_name VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    category VARCHAR(32) NOT NULL,
                    source VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    enabled SMALLINT NOT NULL,
                    latest_revision_id VARCHAR(64),
                    description VARCHAR(500) NOT NULL,
                    tags_json CLOB NOT NULL,
                    allowed_tools_json CLOB NOT NULL,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (tenant_id, skill_name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_skill_revision (
                    revision_id VARCHAR(64) PRIMARY KEY,
                    skill_name VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    revision_no BIGINT NOT NULL,
                    content_hash VARCHAR(128) NOT NULL,
                    content CLOB NOT NULL,
                    frontmatter_json CLOB NOT NULL,
                    scan_decision VARCHAR(32) NOT NULL,
                    scan_result_json CLOB NOT NULL,
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    UNIQUE(tenant_id, skill_name, revision_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_skill_binding (
                    pk_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    skill_name VARCHAR(128) NOT NULL,
                    revision_id VARCHAR(64) NOT NULL,
                    inject_mode VARCHAR(32) NOT NULL,
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }
}
