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

import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRunContextSnapshotRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcRunContextSnapshotRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:run-context-snapshot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcRunContextSnapshotRepositoryAdapter(dataSource);
    }

    @Test
    void shouldSaveAndFindSnapshotByRunId() {
        RunContextSnapshotRecord record = new RunContextSnapshotRecord();
        record.setTenantId("default");
        record.setRunId("run-1");
        record.setConversationId(101L);
        record.setBranchLeafMessageId(202L);
        record.setRoleCardId(303L);
        record.setRunProfileId(404L);
        record.setExecutorEngine("agentscope");
        record.setExecutorConfigJson("{\"nacosNamespace\":\"public\"}");
        record.setTraceContextJson("{\"traceId\":\"trace-1\"}");
        record.setSnapshotJson("{\"toolIds\":[\"echo\"],\"executorEngine\":\"agentscope\"}");

        Long id = adapter.save(record);

        assertThat(id).isNotNull();
        assertThat(adapter.findByRunId("run-1")).get()
                .satisfies(saved -> {
                    assertThat(saved.getId()).isEqualTo(id);
                    assertThat(saved.getTenantId()).isEqualTo("default");
                    assertThat(saved.getRunId()).isEqualTo("run-1");
                    assertThat(saved.getConversationId()).isEqualTo(101L);
                    assertThat(saved.getBranchLeafMessageId()).isEqualTo(202L);
                    assertThat(saved.getRoleCardId()).isEqualTo(303L);
                    assertThat(saved.getRunProfileId()).isEqualTo(404L);
                    assertThat(saved.getExecutorEngine()).isEqualTo("agentscope");
                    assertThat(saved.getExecutorConfigJson()).contains("public");
                    assertThat(saved.getTraceContextJson()).contains("trace-1");
                    assertThat(saved.getSnapshotJson()).contains("toolIds");
                    assertThat(saved.getCreateTime()).isNotNull();
                    assertThat(saved.getDeleted()).isZero();
                });
    }

    @Test
    void shouldIgnoreDeletedSnapshotsAndKeepTenantBoundary() {
        RunContextSnapshotRecord record = new RunContextSnapshotRecord();
        record.setTenantId("default");
        record.setRunId("run-deleted");
        record.setExecutorEngine("kernel");
        record.setSnapshotJson("{}");
        Long id = adapter.save(record);

        jdbcTemplate.update("UPDATE t_run_context_snapshot SET deleted = 1 WHERE id = ?", id);

        assertThat(adapter.findByRunId("run-deleted")).isEmpty();
        assertThat(adapter.findByRunId("missing")).isEmpty();
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_run_context_snapshot");
        jdbcTemplate.execute("""
                CREATE TABLE t_run_context_snapshot (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    run_id VARCHAR(64) NOT NULL,
                    conversation_id BIGINT,
                    branch_leaf_message_id BIGINT,
                    role_card_id BIGINT,
                    run_profile_id BIGINT,
                    executor_engine VARCHAR(32) NOT NULL DEFAULT 'kernel',
                    executor_config_json TEXT,
                    trace_context_json TEXT,
                    snapshot_json TEXT NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }
}
