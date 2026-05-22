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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAgentToolBindingRepositoryAdapterTests {

    @Test
    void shouldReplaceListAndFindBindingsForAgentVersion() {
        DriverManagerDataSource dataSource = dataSource("agent-tool-binding");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createBindingSchema(jdbcTemplate);
        JdbcAgentToolBindingRepositoryAdapter adapter = new JdbcAgentToolBindingRepositoryAdapter(dataSource);
        Instant now = Instant.parse("2026-05-23T00:00:00Z");

        adapter.saveBindings("agent-1", "agent-1-v1", List.of(
                binding("binding-1", "agent-1", "agent-1-v1", "memory-read", 3, now),
                binding("binding-2", "agent-1", "agent-1-v1", "knowledge-search", 5, now)));
        adapter.saveBindings("agent-2", "agent-2-v1", List.of(
                binding("binding-other", "agent-2", "agent-2-v1", "memory-read", 1, now)));
        adapter.saveBindings("agent-1", "agent-1-v1", List.of(
                binding("binding-3", "agent-1", "agent-1-v1", "memory-write", 2, now.plusSeconds(60))));

        List<AgentToolBinding> bindings = adapter.listBindings("agent-1", "agent-1-v1");

        assertThat(bindings).extracting(AgentToolBinding::toolId).containsExactly("memory-write");
        assertThat(adapter.findBinding("agent-1", "agent-1-v1", "memory-write")).isPresent();
        assertThat(adapter.findBinding("agent-1", "agent-1-v1", "memory-read")).isEmpty();
        assertThat(adapter.isBound("agent-2", "agent-2-v1", "memory-read")).isTrue();
    }

    private static AgentToolBinding binding(String bindingId,
                                            String agentId,
                                            String versionId,
                                            String toolId,
                                            int maxCallsPerRun,
                                            Instant createdAt) {
        return new AgentToolBinding(
                bindingId,
                agentId,
                versionId,
                toolId,
                maxCallsPerRun,
                "{\"required\":[\"input\"]}",
                "admin-1",
                createdAt);
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createBindingSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_tool_binding (
                    id VARCHAR(64) PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    version_id VARCHAR(64) NOT NULL,
                    tool_id VARCHAR(128) NOT NULL,
                    max_calls_per_run INT NOT NULL,
                    argument_policy_json CLOB,
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    UNIQUE(agent_id, version_id, tool_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_agent_tool_binding_version
                ON sa_agent_tool_binding(agent_id, version_id)
                """);
    }
}
