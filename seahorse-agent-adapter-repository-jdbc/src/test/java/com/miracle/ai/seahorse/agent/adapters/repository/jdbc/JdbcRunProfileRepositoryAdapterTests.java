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

import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRunProfileRepositoryAdapterTests {

    private JdbcRunProfileRepositoryAdapter adapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:run-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcRunProfileRepositoryAdapter(dataSource);
    }

    @Test
    void shouldSaveFindAndReplaceToolBindings() {
        RunProfileRecord profile = new RunProfileRecord();
        profile.setUserId("100");
        profile.setName("Research AgentScope");
        profile.setDescription("Long research");
        profile.setRoleCardId(9L);
        profile.setExecutorEngine("agentscope");
        profile.setExecutorConfigJson("{\"studioTraceEnabled\":true}");
        profile.setModelConfigJson("{\"model\":\"gpt-4.1-mini\"}");
        profile.setMemoryScopeJson("{\"longTerm\":true}");
        profile.setGuardrailConfigJson("{\"highRiskToolApproval\":true}");
        profile.setEnabled(0);
        profile.setAssetSource("SYSTEM");
        profile.setPresetKey("plan.deep-research");
        profile.setPresetVersion(2);
        profile.setReadonly(1);

        Long id = adapter.save(profile);
        adapter.replaceTools(id, List.of(RunProfileToolBindingRecord.builder()
                .toolId("filesystem.read_file")
                .provider("MCP")
                .enabled(1)
                .build()));

        assertThat(adapter.listByUser("100")).extracting(RunProfileRecord::getId).containsExactly(id);
        assertThat(adapter.findById("100", id)).hasValueSatisfying(found -> {
            assertThat(found.getName()).isEqualTo("Research AgentScope");
            assertThat(found.getExecutorEngine()).isEqualTo("agentscope");
            assertThat(found.getExecutorConfigJson()).contains("studioTraceEnabled");
            assertThat(found.getAssetSource()).isEqualTo("SYSTEM");
            assertThat(found.getPresetKey()).isEqualTo("plan.deep-research");
            assertThat(found.getPresetVersion()).isEqualTo(2);
            assertThat(found.getReadonly()).isEqualTo(1);
        });
        assertThat(adapter.listTools(id)).extracting(RunProfileToolBindingRecord::getToolId)
                .containsExactly("filesystem.read_file");

        adapter.replaceTools(id, List.of(RunProfileToolBindingRecord.builder()
                .toolId("clock")
                .provider("BUILT_IN")
                .enabled(1)
                .build()));
        assertThat(adapter.listTools(id)).extracting(RunProfileToolBindingRecord::getToolId).containsExactly("clock");
    }

    @Test
    void shouldActivateOnlyOneProfileAndSoftDelete() {
        Long first = adapter.save(profile("100", "Kernel", "kernel"));
        Long second = adapter.save(profile("100", "AgentScope", "agentscope"));

        adapter.disableAll("100");
        adapter.setEnabled("100", second, true);
        adapter.delete("100", first);

        assertThat(adapter.findById("100", first)).isEmpty();
        assertThat(adapter.findById("100", second)).hasValueSatisfying(found ->
                assertThat(found.getEnabled()).isEqualTo(1));
    }

    @Test
    void shouldExposeSystemRunProfilesToEveryUser() {
        RunProfileRecord preset = profile("system", "Deep Research", "kernel");
        preset.setAssetSource("SYSTEM");
        preset.setPresetKey("plan.deep-research");
        preset.setReadonly(1);
        Long presetId = adapter.save(preset);

        assertThat(adapter.listByUser("100")).extracting(RunProfileRecord::getId).contains(presetId);
        assertThat(adapter.findById("200", presetId)).hasValueSatisfying(found -> {
            assertThat(found.getAssetSource()).isEqualTo("SYSTEM");
            assertThat(found.getReadonly()).isEqualTo(1);
        });
    }

    @Test
    void shouldApplyRunProfileToConversation() {
        Long profileId = adapter.save(profile("100", "AgentScope", "agentscope"));
        jdbcTemplate.update("""
                INSERT INTO t_conversation
                (id, conversation_id, user_id, title, create_time, update_time, last_time, deleted, tenant_id)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, ?)
                """, 200L, 200L, 100L, "Conversation", "default");

        adapter.applyToConversation("100", "200", profileId);

        assertThat(adapter.findAppliedProfileId("100", "200")).contains(profileId);
    }

    private RunProfileRecord profile(String userId, String name, String engine) {
        RunProfileRecord record = new RunProfileRecord();
        record.setUserId(userId);
        record.setName(name);
        record.setExecutorEngine(engine);
        record.setEnabled(0);
        return record;
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_run_profile_tool");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_run_profile");
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation (
                    id BIGINT PRIMARY KEY,
                    conversation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(256),
                    run_profile_id BIGINT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    last_time TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_run_profile (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id VARCHAR(64) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    description TEXT,
                    role_card_id BIGINT,
                    executor_engine VARCHAR(32) NOT NULL DEFAULT 'kernel',
                    executor_config_json TEXT,
                    model_config_json TEXT,
                    memory_scope_json TEXT,
                    guardrail_config_json TEXT,
                    approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
                    approval_operator VARCHAR(64),
                    approval_comment TEXT,
                    approval_time TIMESTAMP,
                    asset_source VARCHAR(32) NOT NULL DEFAULT 'USER',
                    preset_key VARCHAR(128),
                    preset_version INTEGER NOT NULL DEFAULT 1,
                    readonly SMALLINT NOT NULL DEFAULT 0,
                    enabled SMALLINT NOT NULL DEFAULT 0,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_run_profile_tool (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    profile_id BIGINT NOT NULL,
                    tool_id VARCHAR(128) NOT NULL,
                    provider VARCHAR(32) NOT NULL,
                    enabled SMALLINT NOT NULL DEFAULT 1,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_run_profile_tool
                ON sa_run_profile_tool (tenant_id, profile_id, tool_id)
                """);
    }
}
