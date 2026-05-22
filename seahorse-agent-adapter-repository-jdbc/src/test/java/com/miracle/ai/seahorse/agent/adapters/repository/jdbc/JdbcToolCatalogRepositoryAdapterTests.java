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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcToolCatalogRepositoryAdapterTests {

    @Test
    void shouldSaveUpdateFindAndPageCatalogEntries() {
        DriverManagerDataSource dataSource = dataSource("tool-catalog");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createToolCatalogSchema(jdbcTemplate);
        JdbcToolCatalogRepositoryAdapter adapter = new JdbcToolCatalogRepositoryAdapter(dataSource);
        Instant now = Instant.parse("2026-05-23T00:00:00Z");

        adapter.save(tool("memory-read", "Memory Read", ToolRiskLevel.MEDIUM, ToolActionType.READ,
                "MEMORY", true, false, now));
        adapter.save(tool("knowledge-search", "Knowledge Search", ToolRiskLevel.LOW, ToolActionType.READ,
                "KNOWLEDGE_BASE", true, false, now.plusSeconds(60)));
        adapter.save(tool("memory-read", "Memory Read Updated", ToolRiskLevel.HIGH, ToolActionType.WRITE,
                "MEMORY", true, true, now.plusSeconds(120)));
        adapter.setEnabled("memory-read", false);

        ToolCatalogEntry found = adapter.findById("memory-read").orElseThrow();
        ToolCatalogPage page = adapter.page(new ToolCatalogQuery("MEMORY", "read", 1, 10, null));

        assertThat(found.name()).isEqualTo("Memory Read Updated");
        assertThat(found.riskLevel()).isEqualTo(ToolRiskLevel.HIGH);
        assertThat(found.actionType()).isEqualTo(ToolActionType.WRITE);
        assertThat(found.enabled()).isFalse();
        assertThat(found.requiresApproval()).isTrue();
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(ToolCatalogEntry::toolId).containsExactly("memory-read");
    }

    private static ToolCatalogEntry tool(String toolId,
                                         String name,
                                         ToolRiskLevel riskLevel,
                                         ToolActionType actionType,
                                         String resourceType,
                                         boolean enabled,
                                         boolean requiresApproval,
                                         Instant updatedAt) {
        return new ToolCatalogEntry(
                toolId,
                ToolProvider.BUILTIN,
                name,
                name + " description",
                "{\"type\":\"object\"}",
                null,
                riskLevel,
                actionType,
                resourceType,
                "platform",
                enabled,
                requiresApproval,
                Instant.parse("2026-05-23T00:00:00Z"),
                updatedAt);
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createToolCatalogSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_tool_catalog (
                    tool_id VARCHAR(128) PRIMARY KEY,
                    provider VARCHAR(32) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    description VARCHAR(1000),
                    schema_json CLOB NOT NULL,
                    output_schema_json CLOB,
                    risk_level VARCHAR(32) NOT NULL,
                    action_type VARCHAR(32) NOT NULL,
                    resource_type VARCHAR(64),
                    owner_team VARCHAR(128),
                    enabled BOOLEAN NOT NULL,
                    requires_approval BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_tool_catalog_resource
                ON sa_tool_catalog(resource_type, enabled)
                """);
    }
}
