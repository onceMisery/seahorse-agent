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

package com.miracle.ai.seahorse.agent.kernel.application.agent.registry;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAgentDefinitionServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateDraftAgentWithAdminBoundaryAndDefaultTenant() {
        MemoryAgentDefinitionRepository repository = new MemoryAgentDefinitionRepository();
        KernelAgentDefinitionService service = new KernelAgentDefinitionService(repository, adminUser(), FIXED_CLOCK);

        String agentId = service.createDraft(new AgentDefinitionCreateCommand(
                "procurement-assistant",
                null,
                "Procurement Assistant",
                "Handles internal procurement workflow guidance",
                "owner-1",
                "platform",
                AgentType.WORKFLOW,
                null,
                AgentRiskLevel.HIGH));

        AgentDefinition saved = repository.definitions.get(agentId);
        assertEquals(AgentDefinition.DEFAULT_TENANT_ID, saved.tenantId());
        assertEquals(AgentStatus.DRAFT, saved.status());
        assertEquals(AgentRiskLevel.HIGH, saved.riskLevel());
        assertEquals(FIXED_CLOCK.instant(), saved.createdAt());
    }

    @Test
    void shouldPublishImmutableVersionAndLockDraftUpdates() {
        MemoryAgentDefinitionRepository repository = new MemoryAgentDefinitionRepository();
        KernelAgentDefinitionService service = new KernelAgentDefinitionService(repository, adminUser(), FIXED_CLOCK);
        service.createDraft(new AgentDefinitionCreateCommand(
                "compliance-reviewer",
                "tenant-a",
                "Compliance Reviewer",
                null,
                "owner-1",
                null,
                AgentType.DOMAIN,
                null,
                AgentRiskLevel.CRITICAL));

        AgentVersion version = service.publish("compliance-reviewer", new AgentVersionPublishCommand(
                "Review policy evidence before responding.",
                null,
                null,
                null,
                null,
                "initial release"));

        AgentDefinition published = repository.definitions.get("compliance-reviewer");
        assertEquals("compliance-reviewer-v1", version.versionId());
        assertEquals(1L, version.versionNo());
        assertEquals(AgentVersion.EMPTY_JSON_OBJECT, version.toolSetJson());
        assertEquals("admin-1", version.publishedBy());
        assertEquals(AgentStatus.PUBLISHED, published.status());
        assertEquals(version.versionId(), published.latestVersionId());
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.updateDraft(
                "compliance-reviewer",
                new AgentDefinitionUpdateDraftCommand("Changed", null, null, null, null)));
        assertEquals("只有 DRAFT 状态的 Agent 可以修改", error.getMessage());
    }

    @Test
    void shouldRejectNonAdminDefinitionMutation() {
        KernelAgentDefinitionService service = new KernelAgentDefinitionService(
                new MemoryAgentDefinitionRepository(), user(), FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.createDraft(
                new AgentDefinitionCreateCommand(
                        "assistant",
                        null,
                        "Assistant",
                        null,
                        "owner-1",
                        null,
                        AgentType.ASSISTANT,
                        null,
                        AgentRiskLevel.LOW)));
        assertEquals("权限不足", error.getMessage());
    }

    @Test
    void shouldDisableAgentDefinition() {
        MemoryAgentDefinitionRepository repository = new MemoryAgentDefinitionRepository();
        KernelAgentDefinitionService service = new KernelAgentDefinitionService(repository, adminUser(), FIXED_CLOCK);
        service.createDraft(new AgentDefinitionCreateCommand(
                "ops-agent",
                "tenant-a",
                "Ops Agent",
                null,
                "owner-1",
                null,
                AgentType.ASSISTANT,
                null,
                AgentRiskLevel.MEDIUM));

        AgentDefinition disabled = service.disable("ops-agent");

        assertEquals(AgentStatus.DISABLED, disabled.status());
        assertEquals(FIXED_CLOCK.instant(), disabled.updatedAt());
        assertEquals(disabled, repository.definitions.get("ops-agent"));
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser("admin-1", "root", "admin", null));
    }

    private static CurrentUserPort user() {
        return () -> Optional.of(new CurrentUser("user-1", "alice", "user", null));
    }

    private static class MemoryAgentDefinitionRepository implements AgentDefinitionRepositoryPort {
        private final Map<String, AgentDefinition> definitions = new LinkedHashMap<>();
        private final Map<String, AgentVersion> latestVersions = new LinkedHashMap<>();

        @Override
        public void create(AgentDefinition definition) {
            definitions.put(definition.agentId(), definition);
        }

        @Override
        public void update(AgentDefinition definition) {
            definitions.put(definition.agentId(), definition);
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.ofNullable(definitions.get(agentId));
        }

        @Override
        public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
            return new AgentDefinitionPage(List.copyOf(definitions.values()), definitions.size(), size, current, 1);
        }

        @Override
        public long nextVersionNo(String agentId) {
            return latestVersions.containsKey(agentId) ? latestVersions.get(agentId).versionNo() + 1 : 1L;
        }

        @Override
        public void saveVersion(AgentVersion version) {
            latestVersions.put(version.agentId(), version);
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return Optional.ofNullable(latestVersions.get(agentId));
        }
    }
}
