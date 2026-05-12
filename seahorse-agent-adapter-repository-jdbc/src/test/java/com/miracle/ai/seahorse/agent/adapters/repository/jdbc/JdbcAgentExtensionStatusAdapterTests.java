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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentExtensionStatusAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcAgentExtensionStatusAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:agent-extension-status;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcAgentExtensionStatusAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldSaveAndListExtensionStatus() {
        adapter.saveStatus(status(true, "operator-a"));

        List<AgentExtensionStatus> statuses = adapter.listStatuses();

        assertThat(statuses).hasSize(1);
        assertThat(statuses.get(0).name()).isEqualTo("retrieval-observation");
        assertThat(statuses.get(0).capabilities()).containsExactly("observation");
        assertThat(statuses.get(0).details()).containsEntry("reason", "manual");
    }

    @Test
    void shouldUpdateExistingStatus() {
        adapter.saveStatus(status(true, "operator-a"));
        adapter.saveStatus(status(false, "operator-b"));

        AgentExtensionStatus status = adapter.listStatuses().get(0);

        assertThat(status.enabled()).isFalse();
        assertThat(status.updatedBy()).isEqualTo("operator-b");
    }

    private AgentExtensionStatus status(boolean enabled, String updatedBy) {
        return new AgentExtensionStatus(
                "retrieval-observation",
                "SearchChannelFeature",
                "WRAPPER",
                "1.0.0",
                enabled,
                true,
                Set.of("observation"),
                "updated",
                "",
                Map.of("reason", "manual"),
                updatedBy,
                Instant.now());
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_agent_extension_status");
        jdbcTemplate.execute("""
                CREATE TABLE t_agent_extension_status (
                    extension_name VARCHAR(128) NOT NULL,
                    port_type VARCHAR(256) NOT NULL,
                    feature_type VARCHAR(64) NOT NULL,
                    version VARCHAR(64),
                    enabled BOOLEAN NOT NULL,
                    healthy BOOLEAN NOT NULL,
                    capabilities_json TEXT,
                    message TEXT,
                    last_error TEXT,
                    details_json TEXT,
                    updated_by VARCHAR(128),
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    PRIMARY KEY (extension_name, port_type)
                )
                """);
    }
}
