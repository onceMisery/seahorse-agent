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

package com.miracle.ai.seahorse.agent.kernel.application.agent.factory;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivationType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentPublishValidationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionRollbackCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentVersionActivationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAgentFactoryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldListEnabledTemplatesAndCreateDraftFromTemplate() {
        MemoryTemplateRepository templateRepository = new MemoryTemplateRepository(List.of(
                template(AgentTemplateId.KNOWLEDGE_ASSISTANT, AgentTemplateStatus.ENABLED),
                template(AgentTemplateId.COMPLIANCE_REVIEWER, AgentTemplateStatus.DISABLED)));
        RecordingDefinitionPort definitionPort = new RecordingDefinitionPort();
        KernelAgentFactoryService service = service(templateRepository, definitionPort);

        List<AgentTemplate> templates = service.listTemplates(false);
        AgentDefinition definition = service.createFromTemplate(new AgentFactoryCreateCommand(
                AgentTemplateId.KNOWLEDGE_ASSISTANT,
                "tenant-1",
                "hr-assistant",
                "HR Assistant",
                "Answers HR policy questions",
                "owner-1",
                "people",
                List.of("search"),
                AgentRiskLevel.LOW,
                "Only answer approved HR policy questions."));

        assertEquals(1, templates.size());
        assertEquals(AgentStatus.DRAFT, definition.status());
        assertEquals("knowledge-assistant", definition.baseAgentId());
        assertEquals("tenant-1", definitionPort.lastCreateCommand.tenantId());
        assertEquals(AgentRiskLevel.LOW, definitionPort.lastCreateCommand.riskLevel());
    }

    @Test
    void shouldRejectTemplateToolAndRiskExpansion() {
        KernelAgentFactoryService service = service(
                new MemoryTemplateRepository(List.of(template(AgentTemplateId.KNOWLEDGE_ASSISTANT, AgentTemplateStatus.ENABLED))),
                new RecordingDefinitionPort());

        assertThrows(IllegalArgumentException.class, () -> service.createFromTemplate(new AgentFactoryCreateCommand(
                AgentTemplateId.KNOWLEDGE_ASSISTANT,
                "tenant-1",
                "bad-agent",
                "Bad Agent",
                null,
                "owner-1",
                "people",
                List.of("email-send"),
                AgentRiskLevel.LOW,
                null)));
        assertThrows(IllegalArgumentException.class, () -> service.createFromTemplate(new AgentFactoryCreateCommand(
                AgentTemplateId.KNOWLEDGE_ASSISTANT,
                "tenant-1",
                "risky-agent",
                "Risky Agent",
                null,
                "owner-1",
                "people",
                List.of("search"),
                AgentRiskLevel.HIGH,
                null)));
    }

    @Test
    void shouldHideAndRejectTemplatesUsingAdvancedProviderToolsInConsumerWebMode() {
        MemoryTemplateRepository templateRepository = new MemoryTemplateRepository(List.of(
                templateWithTools(AgentTemplateId.KNOWLEDGE_ASSISTANT, AgentTemplateStatus.ENABLED, List.of("search")),
                templateWithTools(AgentTemplateId.TOOL_OPERATOR, AgentTemplateStatus.ENABLED, List.of("mcp-weather"))));
        RecordingDefinitionPort definitionPort = new RecordingDefinitionPort();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        toolCatalogRepository.save(tool("search", ToolProvider.BUILTIN, ToolRiskLevel.LOW, true, false));
        toolCatalogRepository.save(tool("mcp-weather", ToolProvider.MCP, ToolRiskLevel.MEDIUM, true, false));
        KernelAgentFactoryService service = new KernelAgentFactoryService(
                templateRepository,
                definitionPort,
                new MemoryPublishCheckRepository(),
                toolCatalogRepository);

        List<AgentTemplate> templates = service.listTemplates(true);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createFromTemplate(new AgentFactoryCreateCommand(
                        AgentTemplateId.TOOL_OPERATOR,
                        "tenant-1",
                        "bad-agent",
                        "Bad Agent",
                        null,
                        "owner-1",
                        "ops",
                        List.of("mcp-weather"),
                        AgentRiskLevel.LOW,
                        null)));

        assertEquals(List.of(AgentTemplateId.KNOWLEDGE_ASSISTANT),
                templates.stream().map(AgentTemplate::templateId).toList());
        assertEquals("Tool provider is disabled in the current product mode", error.getMessage());
    }

    @Test
    void shouldReturnStructuredPublishValidationReportWithWarningsBeforePhase8() {
        MemoryPublishCheckRepository checkRepository = new MemoryPublishCheckRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        toolCatalogRepository.save(tool("search", ToolRiskLevel.LOW, true, false));
        toolCatalogRepository.save(tool("delete-customer", ToolRiskLevel.HIGH, true, false));
        KernelAgentFactoryService service = new KernelAgentFactoryService(
                new MemoryTemplateRepository(List.of(template(AgentTemplateId.TOOL_OPERATOR, AgentTemplateStatus.ENABLED))),
                new RecordingDefinitionPort(),
                checkRepository,
                toolCatalogRepository);

        AgentPublishCheckReport report = service.validatePublish(new AgentPublishValidationCommand(
                "agent-1",
                null,
                "Use tools only after checking policy.",
                List.of("search", "delete-customer"),
                "owner-1",
                "ops",
                "initial release"));

        assertEquals(AgentPublishCheckStatus.FAIL, report.status());
        assertEquals(AgentPublishCheckStatus.FAIL,
                report.item(AgentPublishCheckCode.HIGH_RISK_APPROVAL_PRESENT).orElseThrow().status());
        assertEquals(AgentPublishCheckStatus.WARN,
                report.item(AgentPublishCheckCode.EVAL_PRESENT).orElseThrow().status());
        assertEquals(AgentPublishCheckStatus.WARN,
                report.item(AgentPublishCheckCode.QUOTA_PRESENT).orElseThrow().status());
        assertEquals(report, checkRepository.saved);
    }

    @Test
    void shouldReturnLatestPublishCheckWithoutRevalidatingOrSaving() {
        MemoryPublishCheckRepository checkRepository = new MemoryPublishCheckRepository();
        AgentPublishCheckReport latest = report("check-existing", AgentPublishCheckStatus.WARN, NOW);
        checkRepository.saved = latest;
        KernelAgentFactoryService service = new KernelAgentFactoryService(
                new MemoryTemplateRepository(List.of(template(AgentTemplateId.KNOWLEDGE_ASSISTANT, AgentTemplateStatus.ENABLED))),
                new RecordingDefinitionPort(),
                checkRepository,
                new MemoryToolCatalogRepository());

        Optional<AgentPublishCheckReport> result = service.latestPublishCheck("agent-1");

        assertEquals(Optional.of(latest), result);
        assertEquals(0, checkRepository.saveCount);
    }

    @Test
    void shouldRollbackByActivatingTargetVersionWithoutMutatingVersionSnapshot() {
        MemoryDefinitionRepository definitionRepository = new MemoryDefinitionRepository();
        definitionRepository.saveDefinition(publishedDefinition("agent-1", "tenant-1", "agent-1-v2"));
        definitionRepository.saveVersion(version("agent-1-v1", "agent-1", 1, "old instructions"));
        definitionRepository.saveVersion(version("agent-1-v2", "agent-1", 2, "current instructions"));
        MemoryVersionActivationRepository activationRepository = new MemoryVersionActivationRepository();
        activationRepository.active = new AgentVersionActivation(
                "ava-current",
                "tenant-1",
                "agent-1",
                "agent-1-v2",
                AgentVersionActivationType.PUBLISH,
                null,
                AgentRollbackReasonCode.OPERATOR_REQUESTED,
                "publisher-1",
                NOW.minusSeconds(60));
        KernelAgentFactoryService service = service(
                definitionRepository,
                activationRepository,
                new MemoryCatalogQueryPort());

        AgentRollbackResult result = service.rollback(new AgentVersionRollbackCommand(
                "tenant-1",
                "agent-1",
                "agent-1-v1",
                "operator-1",
                AgentRollbackReasonCode.OPERATOR_REQUESTED,
                "incident rollback"));

        assertEquals(AgentRollbackStatus.ROLLED_BACK, result.status());
        assertEquals("agent-1-v2", result.previousVersionId());
        assertEquals("agent-1-v1", result.targetVersionId());
        assertEquals("old instructions",
                definitionRepository.findVersion("agent-1", "agent-1-v1").orElseThrow().instructions());
        assertEquals("agent-1-v1", activationRepository.active.versionId());
    }

    @Test
    void shouldRejectRollbackToVersionOwnedByAnotherAgent() {
        MemoryDefinitionRepository definitionRepository = new MemoryDefinitionRepository();
        definitionRepository.saveDefinition(publishedDefinition("agent-1", "tenant-1", "agent-1-v2"));
        definitionRepository.saveVersion(version("agent-2-v1", "agent-2", 1, "other agent"));
        KernelAgentFactoryService service = service(
                definitionRepository,
                new MemoryVersionActivationRepository(),
                new MemoryCatalogQueryPort());

        assertThrows(IllegalArgumentException.class, () -> service.rollback(new AgentVersionRollbackCommand(
                "tenant-1",
                "agent-1",
                "agent-2-v1",
                "operator-1",
                AgentRollbackReasonCode.OPERATOR_REQUESTED,
                "bad rollback")));
    }

    @Test
    void shouldRequireOperatorAndReasonForRollback() {
        assertThrows(IllegalArgumentException.class, () -> new AgentVersionRollbackCommand(
                "tenant-1",
                "agent-1",
                "agent-1-v1",
                " ",
                AgentRollbackReasonCode.OPERATOR_REQUESTED,
                "missing operator"));
        assertThrows(NullPointerException.class, () -> new AgentVersionRollbackCommand(
                "tenant-1",
                "agent-1",
                "agent-1-v1",
                "operator-1",
                null,
                "missing reason"));
    }

    @Test
    void shouldReturnAgentCatalogFromCatalogPort() {
        MemoryCatalogQueryPort catalogPort = new MemoryCatalogQueryPort();
        AgentCatalogPage page = new AgentCatalogPage(
                List.of(new AgentCatalogEntry(
                        "agent-1",
                        "tenant-1",
                        "Published Agent",
                        "A published agent",
                        "owner-1",
                        "ops",
                        AgentType.ASSISTANT,
                        AgentRiskLevel.LOW,
                        "agent-1-v1",
                        NOW)),
                1,
                20,
                1,
                1);
        catalogPort.page = page;
        KernelAgentFactoryService service = service(
                new MemoryDefinitionRepository(),
                new MemoryVersionActivationRepository(),
                catalogPort);

        AgentCatalogPage result = service.catalog(new AgentCatalogQuery("tenant-1", null, 1, 20));

        assertEquals(page, result);
        assertEquals("tenant-1", catalogPort.lastQuery.tenantId());
    }

    private static KernelAgentFactoryService service(AgentTemplateRepositoryPort templateRepository,
                                                    RecordingDefinitionPort definitionPort) {
        return new KernelAgentFactoryService(
                templateRepository,
                definitionPort,
                new MemoryPublishCheckRepository(),
                new MemoryToolCatalogRepository());
    }

    private static KernelAgentFactoryService service(MemoryDefinitionRepository definitionRepository,
                                                    MemoryVersionActivationRepository activationRepository,
                                                    MemoryCatalogQueryPort catalogQueryPort) {
        return new KernelAgentFactoryService(
                new MemoryTemplateRepository(List.of(template(AgentTemplateId.KNOWLEDGE_ASSISTANT, AgentTemplateStatus.ENABLED))),
                new RecordingDefinitionPort(),
                new MemoryPublishCheckRepository(),
                new MemoryToolCatalogRepository(),
                definitionRepository,
                activationRepository,
                catalogQueryPort);
    }

    private static AgentPublishCheckReport report(String checkId,
                                                  AgentPublishCheckStatus status,
                                                  Instant checkedAt) {
        return new AgentPublishCheckReport(
                checkId,
                "agent-1",
                "agent-1-v1",
                status,
                List.of(AgentPublishCheckItem.warn(
                        AgentPublishCheckCode.EVAL_PRESENT,
                        "Evaluation platform is not enabled yet.")),
                checkedAt);
    }

    private static AgentTemplate template(AgentTemplateId id, AgentTemplateStatus status) {
        return templateWithTools(
                id,
                status,
                id == AgentTemplateId.TOOL_OPERATOR ? List.of("search", "delete-customer") : List.of("search", "memory-read"));
    }

    private static AgentTemplate templateWithTools(AgentTemplateId id, AgentTemplateStatus status, List<String> toolIds) {
        return new AgentTemplate(
                id,
                status,
                id.value(),
                id.value() + " template",
                id == AgentTemplateId.TOOL_OPERATOR ? AgentType.DOMAIN : AgentType.ASSISTANT,
                id == AgentTemplateId.TOOL_OPERATOR ? AgentRiskLevel.HIGH : AgentRiskLevel.LOW,
                toolIds,
                "Base instructions for " + id.value(),
                "{\"base\":true}");
    }

    private static ToolCatalogEntry tool(String toolId,
                                         ToolRiskLevel riskLevel,
                                         boolean enabled,
                                         boolean requiresApproval) {
        return new ToolCatalogEntry(
                toolId,
                ToolProvider.BUILTIN,
                toolId,
                null,
                "{}",
                null,
                riskLevel,
                ToolActionType.EXECUTE,
                null,
                "platform",
                enabled,
                requiresApproval,
                NOW,
                NOW);
    }

    private static ToolCatalogEntry tool(String toolId,
                                         ToolProvider provider,
                                         ToolRiskLevel riskLevel,
                                         boolean enabled,
                                         boolean requiresApproval) {
        return new ToolCatalogEntry(
                toolId,
                provider,
                toolId,
                null,
                "{}",
                null,
                riskLevel,
                ToolActionType.EXECUTE,
                null,
                "platform",
                enabled,
                requiresApproval,
                NOW,
                NOW);
    }

    private static AgentDefinition publishedDefinition(String agentId, String tenantId, String latestVersionId) {
        return new AgentDefinition(
                agentId,
                tenantId,
                "Published Agent",
                "Published agent",
                "owner-1",
                "ops",
                AgentType.ASSISTANT,
                null,
                AgentStatus.PUBLISHED,
                AgentRiskLevel.LOW,
                latestVersionId,
                NOW.minusSeconds(120),
                NOW.minusSeconds(60));
    }

    private static AgentVersion version(String versionId, String agentId, long versionNo, String instructions) {
        return new AgentVersion(
                versionId,
                agentId,
                versionNo,
                instructions,
                "{}",
                "{}",
                "{}",
                "{}",
                "publisher-1",
                NOW.minusSeconds(60),
                "publish " + versionNo);
    }

    private static final class RecordingDefinitionPort implements AgentDefinitionInboundPort {

        private final Map<String, AgentDefinition> definitions = new LinkedHashMap<>();
        private AgentDefinitionCreateCommand lastCreateCommand;

        @Override
        public String createDraft(AgentDefinitionCreateCommand command) {
            lastCreateCommand = command;
            AgentDefinition definition = new AgentDefinition(
                    command.agentId(),
                    command.tenantId(),
                    command.name(),
                    command.description(),
                    command.ownerUserId(),
                    command.ownerTeam(),
                    command.agentType(),
                    command.baseAgentId(),
                    AgentStatus.DRAFT,
                    command.riskLevel(),
                    null,
                    NOW,
                    NOW);
            definitions.put(definition.agentId(), definition);
            return definition.agentId();
        }

        @Override
        public AgentDefinition updateDraft(String agentId, AgentDefinitionUpdateDraftCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentVersion publish(String agentId, AgentVersionPublishCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentDefinition disable(String agentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentDefinition enable(String agentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.ofNullable(definitions.get(agentId));
        }

        @Override
        public Optional<AgentVersion> findVersion(String agentId, String versionId) {
            return Optional.empty();
        }

        @Override
        public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
            return new AgentDefinitionPage(List.copyOf(definitions.values()), definitions.size(), size, current, 1);
        }
    }

    private static final class MemoryTemplateRepository implements AgentTemplateRepositoryPort {

        private final List<AgentTemplate> templates;

        private MemoryTemplateRepository(List<AgentTemplate> templates) {
            this.templates = templates;
        }

        @Override
        public List<AgentTemplate> list(boolean includeDisabled) {
            return templates.stream()
                    .filter(template -> includeDisabled || template.status() == AgentTemplateStatus.ENABLED)
                    .toList();
        }

        @Override
        public Optional<AgentTemplate> findById(AgentTemplateId templateId) {
            return templates.stream()
                    .filter(template -> template.templateId() == templateId)
                    .findFirst();
        }
    }

    private static final class MemoryPublishCheckRepository implements AgentPublishCheckRepositoryPort {

        private AgentPublishCheckReport saved;
        private int saveCount;

        @Override
        public AgentPublishCheckReport save(AgentPublishCheckReport report) {
            saveCount++;
            saved = report;
            return report;
        }

        @Override
        public Optional<AgentPublishCheckReport> latest(String agentId) {
            return Optional.ofNullable(saved)
                    .filter(report -> report.agentId().equals(agentId));
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
    }

    private static final class MemoryDefinitionRepository implements AgentDefinitionRepositoryPort {

        private final Map<String, AgentDefinition> definitions = new LinkedHashMap<>();
        private final Map<String, AgentVersion> versions = new LinkedHashMap<>();

        private void saveDefinition(AgentDefinition definition) {
            definitions.put(definition.agentId(), definition);
        }

        @Override
        public void create(AgentDefinition definition) {
            saveDefinition(definition);
        }

        @Override
        public void update(AgentDefinition definition) {
            saveDefinition(definition);
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.ofNullable(definitions.get(agentId));
        }

        @Override
        public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
            List<AgentDefinition> records = definitions.values().stream()
                    .filter(definition -> definition.tenantId().equals(tenantId))
                    .toList();
            return new AgentDefinitionPage(records, records.size(), size, current, 1);
        }

        @Override
        public long nextVersionNo(String agentId) {
            return versions.values().stream()
                    .filter(version -> version.agentId().equals(agentId))
                    .mapToLong(AgentVersion::versionNo)
                    .max()
                    .orElse(0L) + 1L;
        }

        @Override
        public void saveVersion(AgentVersion version) {
            versions.put(version.versionId(), version);
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return versions.values().stream()
                    .filter(version -> version.agentId().equals(agentId))
                    .max(java.util.Comparator.comparingLong(AgentVersion::versionNo));
        }

        @Override
        public Optional<AgentVersion> findVersion(String agentId, String versionId) {
            return Optional.ofNullable(versions.get(versionId))
                    .filter(version -> version.agentId().equals(agentId));
        }
    }

    private static final class MemoryVersionActivationRepository implements AgentVersionActivationRepositoryPort {

        private AgentVersionActivation active;

        @Override
        public Optional<AgentVersionActivation> findActive(String agentId) {
            return Optional.ofNullable(active)
                    .filter(activation -> activation.agentId().equals(agentId));
        }

        @Override
        public AgentVersionActivation activate(AgentVersionActivation activation) {
            active = activation;
            return activation;
        }
    }

    private static final class MemoryCatalogQueryPort implements AgentCatalogQueryPort {

        private AgentCatalogPage page = new AgentCatalogPage(List.of(), 0, 10, 1, 0);
        private AgentCatalogQuery lastQuery;

        @Override
        public AgentCatalogPage page(AgentCatalogQuery query) {
            lastQuery = query;
            return page;
        }
    }
}
