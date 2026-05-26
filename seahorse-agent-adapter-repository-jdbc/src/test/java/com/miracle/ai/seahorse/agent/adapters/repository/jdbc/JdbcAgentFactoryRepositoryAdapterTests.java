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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivationType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentFactoryRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldReadEnabledAgentTemplatesAndFindById() {
        DriverManagerDataSource dataSource = dataSource("agent-template");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createTemplateSchema(jdbcTemplate);
        insertTemplate(jdbcTemplate, AgentTemplateId.KNOWLEDGE_ASSISTANT, AgentTemplateStatus.ENABLED);
        insertTemplate(jdbcTemplate, AgentTemplateId.COMPLIANCE_REVIEWER, AgentTemplateStatus.DISABLED);
        JdbcAgentTemplateRepositoryAdapter adapter = new JdbcAgentTemplateRepositoryAdapter(dataSource);

        List<AgentTemplate> enabled = adapter.list(false);
        List<AgentTemplate> all = adapter.list(true);
        AgentTemplate found = adapter.findById(AgentTemplateId.KNOWLEDGE_ASSISTANT).orElseThrow();

        assertThat(enabled).extracting(AgentTemplate::templateId)
                .containsExactly(AgentTemplateId.KNOWLEDGE_ASSISTANT);
        assertThat(all).hasSize(2);
        assertThat(found.allowedToolIds()).containsExactly("search", "memory-read");
        assertThat(found.riskCap()).isEqualTo(AgentRiskLevel.LOW);
        assertThat(found.status()).isEqualTo(AgentTemplateStatus.ENABLED);
    }

    @Test
    void shouldSaveAndReturnLatestPublishCheckReport() {
        DriverManagerDataSource dataSource = dataSource("agent-publish-check");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createPublishCheckSchema(jdbcTemplate);
        JdbcAgentPublishCheckRepositoryAdapter adapter = new JdbcAgentPublishCheckRepositoryAdapter(dataSource);
        AgentPublishCheckReport first = report("check-1", AgentPublishCheckStatus.WARN, NOW);
        AgentPublishCheckReport second = report("check-2", AgentPublishCheckStatus.FAIL, NOW.plusSeconds(60));

        adapter.save(first);
        adapter.save(second);

        AgentPublishCheckReport latest = adapter.latest("agent-1").orElseThrow();
        assertThat(latest.checkId()).isEqualTo("check-2");
        assertThat(latest.status()).isEqualTo(AgentPublishCheckStatus.FAIL);
        assertThat(latest.item(AgentPublishCheckCode.EVAL_PRESENT).orElseThrow().status())
                .isEqualTo(AgentPublishCheckStatus.WARN);
        assertThat(adapter.latest("missing")).isEmpty();
    }

    @Test
    void shouldSaveVersionActivationAndResolveLatestActiveVersion() {
        DriverManagerDataSource dataSource = dataSource("agent-version-activation");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createActivationSchema(jdbcTemplate);
        JdbcAgentVersionActivationRepositoryAdapter adapter =
                new JdbcAgentVersionActivationRepositoryAdapter(dataSource);
        AgentVersionActivation first = activation(
                "activation-1",
                "agent-1",
                "agent-1-v1",
                null,
                NOW);
        AgentVersionActivation second = activation(
                "activation-2",
                "agent-1",
                "agent-1-v2",
                "agent-1-v1",
                NOW.plusSeconds(60));

        assertThat(adapter.findActive("agent-1")).isEmpty();

        adapter.activate(first);
        adapter.activate(second);

        AgentVersionActivation active = adapter.findActive("agent-1").orElseThrow();
        assertThat(active.activationId()).isEqualTo("activation-2");
        assertThat(active.versionId()).isEqualTo("agent-1-v2");
        assertThat(active.previousVersionId()).isEqualTo("agent-1-v1");
        assertThat(active.activationType()).isEqualTo(AgentVersionActivationType.ROLLBACK);
        assertThat(adapter.findActive("missing")).isEmpty();
    }

    @Test
    void shouldReturnOnlyPublishedAgentsInCatalog() {
        DriverManagerDataSource dataSource = dataSource("agent-catalog");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createDefinitionAndVersionSchema(jdbcTemplate);
        insertDefinition(jdbcTemplate, "agent-published", "tenant-1", AgentStatus.PUBLISHED, "agent-published-v1", NOW);
        insertVersion(jdbcTemplate, "agent-published-v1", "agent-published", 1, NOW.plusSeconds(30));
        insertDefinition(jdbcTemplate, "agent-draft", "tenant-1", AgentStatus.DRAFT, null, NOW);
        insertDefinition(jdbcTemplate, "agent-disabled", "tenant-1", AgentStatus.DISABLED, "agent-disabled-v1", NOW);
        insertVersion(jdbcTemplate, "agent-disabled-v1", "agent-disabled", 1, NOW.plusSeconds(30));
        insertDefinition(jdbcTemplate, "agent-other-tenant", "tenant-2", AgentStatus.PUBLISHED, "agent-other-v1", NOW);
        insertVersion(jdbcTemplate, "agent-other-v1", "agent-other-tenant", 1, NOW.plusSeconds(30));
        JdbcAgentCatalogQueryAdapter adapter = new JdbcAgentCatalogQueryAdapter(dataSource);

        AgentCatalogPage page = adapter.page(new AgentCatalogQuery("tenant-1", "published", 1, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(AgentCatalogEntry::agentId).containsExactly("agent-published");
        assertThat(page.records().get(0).latestVersionId()).isEqualTo("agent-published-v1");
        assertThat(page.records().get(0).publishedAt()).isEqualTo(NOW.plusSeconds(30));
    }

    private static AgentPublishCheckReport report(String checkId,
                                                  AgentPublishCheckStatus status,
                                                  Instant checkedAt) {
        return new AgentPublishCheckReport(
                checkId,
                "agent-1",
                null,
                status,
                List.of(
                        AgentPublishCheckItem.warn(
                                AgentPublishCheckCode.EVAL_PRESENT,
                                "Evaluation platform is not enabled yet."),
                        AgentPublishCheckItem.fail(
                                AgentPublishCheckCode.HIGH_RISK_APPROVAL_PRESENT,
                                "High risk tools require approval policy.")),
                checkedAt);
    }

    private static AgentVersionActivation activation(String activationId,
                                                     String agentId,
                                                     String versionId,
                                                     String previousVersionId,
                                                     Instant createdAt) {
        return new AgentVersionActivation(
                activationId,
                "tenant-1",
                agentId,
                versionId,
                AgentVersionActivationType.ROLLBACK,
                previousVersionId,
                AgentRollbackReasonCode.OPERATOR_REQUESTED,
                "operator-1",
                createdAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    private static void createTemplateSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_template (
                    template_id VARCHAR(64) PRIMARY KEY,
                    status VARCHAR(32) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    description VARCHAR(1000),
                    agent_type VARCHAR(32) NOT NULL,
                    risk_cap VARCHAR(32) NOT NULL,
                    allowed_tool_ids_json CLOB NOT NULL,
                    base_instructions CLOB NOT NULL,
                    guardrail_config_json CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
    }

    private static void createPublishCheckSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_publish_check (
                    check_id VARCHAR(64) PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    result_json CLOB NOT NULL,
                    checked_by VARCHAR(64) NOT NULL,
                    checked_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_publish_check_agent
                ON sa_agent_publish_check(agent_id, checked_at)
                """);
    }

    private static void createActivationSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_version_activation (
                    activation_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64) NOT NULL,
                    activation_type VARCHAR(32) NOT NULL,
                    previous_version_id VARCHAR(64),
                    reason_code VARCHAR(64) NOT NULL,
                    operator_id VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_version_activation_active
                ON sa_agent_version_activation(agent_id, created_at)
                """);
    }

    private static void createDefinitionAndVersionSchema(JdbcTemplate jdbcTemplate) {
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
                CREATE TABLE sa_agent_version (
                    version_id VARCHAR(64) PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    version_no BIGINT NOT NULL,
                    instructions CLOB NOT NULL,
                    tool_set_json CLOB NOT NULL,
                    model_config_json CLOB NOT NULL,
                    memory_config_json CLOB NOT NULL,
                    guardrail_config_json CLOB NOT NULL,
                    published_by VARCHAR(64) NOT NULL,
                    published_at TIMESTAMP NOT NULL,
                    change_summary VARCHAR(500) NOT NULL
                )
                """);
    }

    private static void insertTemplate(JdbcTemplate jdbcTemplate,
                                       AgentTemplateId templateId,
                                       AgentTemplateStatus status) {
        jdbcTemplate.update("""
                        INSERT INTO sa_agent_template
                        (template_id, status, name, description, agent_type, risk_cap, allowed_tool_ids_json,
                         base_instructions, guardrail_config_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                templateId.value(),
                status.name(),
                templateId.value(),
                templateId.value() + " template",
                AgentType.ASSISTANT.name(),
                AgentRiskLevel.LOW.name(),
                "[\"search\",\"memory-read\"]",
                "Answer from approved enterprise knowledge.",
                "{\"grounded\":true}",
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));
    }

    private static void insertDefinition(JdbcTemplate jdbcTemplate,
                                         String agentId,
                                         String tenantId,
                                         AgentStatus status,
                                         String latestVersionId,
                                         Instant updatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO sa_agent_definition
                        (agent_id, tenant_id, name, description, owner_user_id, owner_team, agent_type, base_agent_id,
                         status, risk_level, latest_version_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                agentId,
                tenantId,
                agentId + " name",
                agentId + " description",
                "owner-1",
                "ops",
                AgentType.ASSISTANT.name(),
                null,
                status.name(),
                AgentRiskLevel.LOW.name(),
                latestVersionId,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(updatedAt));
    }

    private static void insertVersion(JdbcTemplate jdbcTemplate,
                                      String versionId,
                                      String agentId,
                                      long versionNo,
                                      Instant publishedAt) {
        jdbcTemplate.update("""
                        INSERT INTO sa_agent_version
                        (version_id, agent_id, version_no, instructions, tool_set_json, model_config_json,
                         memory_config_json, guardrail_config_json, published_by, published_at, change_summary)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                versionId,
                agentId,
                versionNo,
                "Instructions",
                "{}",
                "{}",
                "{}",
                "{}",
                "publisher-1",
                java.sql.Timestamp.from(publishedAt),
                "initial");
    }
}
