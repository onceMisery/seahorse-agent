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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingItemCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingReplaceCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentToolBindingManagementServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReplaceVersionToolBindingsBehindAdminBoundary() {
        MemoryAgentToolBindingRepository repository = new MemoryAgentToolBindingRepository();
        KernelAgentToolBindingManagementService service = new KernelAgentToolBindingManagementService(
                repository, adminUser(), FIXED_CLOCK);

        List<AgentToolBinding> bindings = service.replaceBindings(
                "agent-1",
                "agent-1-v1",
                new AgentToolBindingReplaceCommand(List.of(
                        new AgentToolBindingItemCommand(
                                "weather_query", 3, "{\"required\":[\"query\"],\"allowed\":[\"query\"]}"),
                        new AgentToolBindingItemCommand("memory_read", 5, null))));

        assertEquals("agent-1", repository.lastAgentId);
        assertEquals("agent-1-v1", repository.lastVersionId);
        assertEquals(2, bindings.size());
        assertEquals("weather_query", bindings.get(0).toolId());
        assertEquals(3, bindings.get(0).maxCallsPerRun());
        assertEquals("admin-1", bindings.get(0).createdBy());
        assertEquals(FIXED_CLOCK.instant(), bindings.get(0).createdAt());
        assertTrue(bindings.get(0).bindingId().startsWith("atb-"));
        assertEquals(AgentToolBinding.EMPTY_JSON_OBJECT, bindings.get(1).argumentPolicyJson());
    }

    @Test
    void shouldRejectDuplicateToolBindingsInSameVersion() {
        KernelAgentToolBindingManagementService service = new KernelAgentToolBindingManagementService(
                new MemoryAgentToolBindingRepository(), adminUser(), FIXED_CLOCK);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replaceBindings(
                        "agent-1",
                        "agent-1-v1",
                        new AgentToolBindingReplaceCommand(List.of(
                                new AgentToolBindingItemCommand("weather_query", 3, null),
                                new AgentToolBindingItemCommand("weather_query", 5, null)))));

        assertEquals("toolId 不能重复", error.getMessage());
    }

    @Test
    void shouldRejectAdvancedProviderToolBindingsInConsumerWebMode() {
        MemoryToolCatalogRepository toolCatalog = new MemoryToolCatalogRepository();
        toolCatalog.save(tool("mcp_weather", ToolProvider.MCP));
        KernelAgentToolBindingManagementService service = new KernelAgentToolBindingManagementService(
                new MemoryAgentToolBindingRepository(),
                adminUser(),
                FIXED_CLOCK,
                toolCatalog);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.replaceBindings(
                        "agent-1",
                        "agent-1-v1",
                        new AgentToolBindingReplaceCommand(List.of(
                                new AgentToolBindingItemCommand("mcp_weather", 3, null)))));

        assertEquals("Tool provider is disabled in the current product mode", error.getMessage());
    }

    @Test
    void shouldRejectNonAdminAccess() {
        KernelAgentToolBindingManagementService service = new KernelAgentToolBindingManagementService(
                new MemoryAgentToolBindingRepository(), user(), FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.replaceBindings(
                        "agent-1",
                        "agent-1-v1",
                        new AgentToolBindingReplaceCommand(List.of(
                                new AgentToolBindingItemCommand("weather_query", 3, null)))));

        assertEquals("权限不足", error.getMessage());
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser(1L, "root", "admin", null));
    }

    private static CurrentUserPort user() {
        return () -> Optional.of(new CurrentUser(2L, "alice", "user", null));
    }

    private static ToolCatalogEntry tool(String toolId, ToolProvider provider) {
        return new ToolCatalogEntry(
                toolId,
                provider,
                toolId,
                null,
                "{}",
                null,
                ToolRiskLevel.MEDIUM,
                ToolActionType.EXECUTE,
                provider.name(),
                "platform",
                true,
                false,
                FIXED_CLOCK.instant(),
                FIXED_CLOCK.instant());
    }

    private static final class MemoryAgentToolBindingRepository implements AgentToolBindingRepositoryPort {
        private final List<AgentToolBinding> bindings = new ArrayList<>();
        private String lastAgentId;
        private String lastVersionId;

        @Override
        public void saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings) {
            lastAgentId = agentId;
            lastVersionId = versionId;
            this.bindings.clear();
            this.bindings.addAll(bindings);
        }

        @Override
        public List<AgentToolBinding> listBindings(String agentId, String versionId) {
            return List.copyOf(bindings);
        }
    }

    private static final class MemoryToolCatalogRepository implements ToolCatalogRepositoryPort {

        private final Map<String, ToolCatalogEntry> tools = new LinkedHashMap<>();

        @Override
        public void save(ToolCatalogEntry entry) {
            tools.put(entry.toolId(), entry);
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return Optional.ofNullable(tools.get(toolId));
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
        }

        @Override
        public ToolCatalogPage page(ToolCatalogQuery query) {
            return new ToolCatalogPage(List.copyOf(tools.values()), tools.size(), query.size(), query.current(), 1L);
        }
    }
}
