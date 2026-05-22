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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelToolCatalogManagementServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-23T00:00:00Z");

    @Test
    void shouldPageAndFindCatalogEntriesBehindAdminBoundary() {
        MemoryToolCatalogRepository repository = new MemoryToolCatalogRepository(tool(true));
        KernelToolCatalogManagementService service = new KernelToolCatalogManagementService(repository, adminUser());

        ToolCatalogPage page = service.page("MCP", "weather", 2L, 20L, true);
        Optional<ToolCatalogEntry> found = service.findById("weather_query");

        assertEquals("MCP", repository.lastQuery.resourceType());
        assertEquals("weather", repository.lastQuery.keyword());
        assertEquals(2L, repository.lastQuery.current());
        assertEquals(20L, repository.lastQuery.size());
        assertEquals(true, repository.lastQuery.enabled());
        assertEquals(1L, page.total());
        assertEquals("weather_query", found.orElseThrow().toolId());
    }

    @Test
    void shouldEnableAndDisableCatalogEntry() {
        MemoryToolCatalogRepository repository = new MemoryToolCatalogRepository(tool(true));
        KernelToolCatalogManagementService service = new KernelToolCatalogManagementService(repository, adminUser());

        ToolCatalogEntry disabled = service.disable("weather_query");
        ToolCatalogEntry enabled = service.enable("weather_query");

        assertEquals(false, disabled.enabled());
        assertEquals(true, enabled.enabled());
        assertEquals(true, repository.entries.get("weather_query").enabled());
    }

    @Test
    void shouldRejectNonAdminAccess() {
        KernelToolCatalogManagementService service = new KernelToolCatalogManagementService(
                new MemoryToolCatalogRepository(tool(true)), user());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.page(null, null, 1L, 10L, null));

        assertEquals("权限不足", error.getMessage());
    }

    private static ToolCatalogEntry tool(boolean enabled) {
        return new ToolCatalogEntry(
                "weather_query",
                ToolProvider.MCP,
                "Weather Query",
                "查询天气",
                "{\"type\":\"object\"}",
                null,
                ToolRiskLevel.MEDIUM,
                ToolActionType.EXECUTE,
                "MCP",
                "platform",
                enabled,
                false,
                NOW,
                NOW);
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser("admin-1", "root", "admin", null));
    }

    private static CurrentUserPort user() {
        return () -> Optional.of(new CurrentUser("user-1", "alice", "user", null));
    }

    private static final class MemoryToolCatalogRepository implements ToolCatalogRepositoryPort {
        private final Map<String, ToolCatalogEntry> entries = new LinkedHashMap<>();
        private ToolCatalogQuery lastQuery;

        private MemoryToolCatalogRepository(ToolCatalogEntry entry) {
            entries.put(entry.toolId(), entry);
        }

        @Override
        public void save(ToolCatalogEntry entry) {
            entries.put(entry.toolId(), entry);
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return Optional.ofNullable(entries.get(toolId));
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
            ToolCatalogEntry current = entries.get(toolId);
            if (current == null) {
                return;
            }
            entries.put(toolId, new ToolCatalogEntry(
                    current.toolId(),
                    current.provider(),
                    current.name(),
                    current.description(),
                    current.schemaJson(),
                    current.outputSchemaJson(),
                    current.riskLevel(),
                    current.actionType(),
                    current.resourceType(),
                    current.ownerTeam(),
                    enabled,
                    current.requiresApproval(),
                    current.createdAt(),
                    current.updatedAt()));
        }

        @Override
        public ToolCatalogPage page(ToolCatalogQuery query) {
            lastQuery = query;
            return new ToolCatalogPage(List.copyOf(entries.values()), entries.size(), query.size(), query.current(), 1L);
        }
    }
}
