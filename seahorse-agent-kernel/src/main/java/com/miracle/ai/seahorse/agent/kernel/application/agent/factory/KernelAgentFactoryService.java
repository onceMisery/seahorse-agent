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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivationType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentPublishValidationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionRollbackCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentVersionActivationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolProviderExposurePolicyPort;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentFactoryService implements AgentFactoryInboundPort {

    private static final String CHECK_ID_PREFIX = "apc_";
    private static final String ROLLBACK_ID_PREFIX = "rb_";
    private static final String ACTIVATION_ID_PREFIX = "ava_";

    private final AgentTemplateRepositoryPort templateRepository;
    private final AgentDefinitionInboundPort definitionPort;
    private final AgentPublishCheckRepositoryPort checkRepository;
    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final AgentDefinitionRepositoryPort definitionRepository;
    private final AgentVersionActivationRepositoryPort activationRepository;
    private final AgentCatalogQueryPort catalogQueryPort;
    private final ToolProviderExposurePolicyPort providerExposurePolicy;
    private final Clock clock;

    public KernelAgentFactoryService(AgentTemplateRepositoryPort templateRepository,
                                     AgentDefinitionInboundPort definitionPort,
                                     AgentPublishCheckRepositoryPort checkRepository,
                                     ToolCatalogRepositoryPort toolCatalogRepository) {
        this(templateRepository, definitionPort, checkRepository, toolCatalogRepository, Clock.systemUTC());
    }

    public KernelAgentFactoryService(AgentTemplateRepositoryPort templateRepository,
                                     AgentDefinitionInboundPort definitionPort,
                                     AgentPublishCheckRepositoryPort checkRepository,
                                     ToolCatalogRepositoryPort toolCatalogRepository,
                                     Clock clock) {
        this(
                templateRepository,
                definitionPort,
                checkRepository,
                toolCatalogRepository,
                null,
                null,
                null,
                ToolProviderExposurePolicyPort.demoDefaults(),
                clock);
    }

    public KernelAgentFactoryService(AgentTemplateRepositoryPort templateRepository,
                                     AgentDefinitionInboundPort definitionPort,
                                     AgentPublishCheckRepositoryPort checkRepository,
                                     ToolCatalogRepositoryPort toolCatalogRepository,
                                     AgentDefinitionRepositoryPort definitionRepository,
                                     AgentVersionActivationRepositoryPort activationRepository,
                                     AgentCatalogQueryPort catalogQueryPort) {
        this(
                templateRepository,
                definitionPort,
                checkRepository,
                toolCatalogRepository,
                definitionRepository,
                activationRepository,
                catalogQueryPort,
                ToolProviderExposurePolicyPort.demoDefaults(),
                Clock.systemUTC());
    }

    public KernelAgentFactoryService(AgentTemplateRepositoryPort templateRepository,
                                     AgentDefinitionInboundPort definitionPort,
                                     AgentPublishCheckRepositoryPort checkRepository,
                                     ToolCatalogRepositoryPort toolCatalogRepository,
                                     AgentDefinitionRepositoryPort definitionRepository,
                                     AgentVersionActivationRepositoryPort activationRepository,
                                     AgentCatalogQueryPort catalogQueryPort,
                                     Clock clock) {
        this(
                templateRepository,
                definitionPort,
                checkRepository,
                toolCatalogRepository,
                definitionRepository,
                activationRepository,
                catalogQueryPort,
                ToolProviderExposurePolicyPort.demoDefaults(),
                clock);
    }

    public KernelAgentFactoryService(AgentTemplateRepositoryPort templateRepository,
                                     AgentDefinitionInboundPort definitionPort,
                                     AgentPublishCheckRepositoryPort checkRepository,
                                     ToolCatalogRepositoryPort toolCatalogRepository,
                                     AgentDefinitionRepositoryPort definitionRepository,
                                     AgentVersionActivationRepositoryPort activationRepository,
                                     AgentCatalogQueryPort catalogQueryPort,
                                     ToolProviderExposurePolicyPort providerExposurePolicy,
                                     Clock clock) {
        this.templateRepository = Objects.requireNonNull(templateRepository,
                "templateRepository must not be null");
        this.definitionPort = Objects.requireNonNull(definitionPort, "definitionPort must not be null");
        this.checkRepository = Objects.requireNonNull(checkRepository, "checkRepository must not be null");
        this.toolCatalogRepository = Objects.requireNonNull(toolCatalogRepository,
                "toolCatalogRepository must not be null");
        this.definitionRepository = definitionRepository;
        this.activationRepository = activationRepository;
        this.catalogQueryPort = catalogQueryPort;
        this.providerExposurePolicy = Objects.requireNonNullElseGet(
                providerExposurePolicy,
                ToolProviderExposurePolicyPort::demoDefaults);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public List<AgentTemplate> listTemplates(boolean includeDisabled) {
        return templateRepository.list(includeDisabled).stream()
                .filter(this::templateAllowed)
                .toList();
    }

    @Override
    public AgentDefinition createFromTemplate(AgentFactoryCreateCommand command) {
        AgentFactoryCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentTemplate template = templateRepository.findById(safeCommand.templateId())
                .orElseThrow(() -> new IllegalArgumentException("Agent template not found"));
        requireTemplateAllowed(template);
        template.validateRequestedTools(safeCommand.requestedToolIds());
        template.validateRequestedRisk(safeCommand.riskLevel());
        requireToolsAllowed(safeCommand.requestedToolIds());
        String agentId = definitionPort.createDraft(new AgentDefinitionCreateCommand(
                safeCommand.agentId(),
                safeCommand.tenantId(),
                safeCommand.name(),
                safeCommand.description(),
                safeCommand.ownerUserId(),
                safeCommand.ownerTeam(),
                template.agentType(),
                template.templateId().value(),
                safeCommand.riskLevel()));
        return definitionPort.findById(agentId)
                .orElseThrow(() -> new IllegalStateException("Created agent definition not found"));
    }

    @Override
    public AgentPublishCheckReport validatePublish(AgentPublishValidationCommand command) {
        AgentPublishValidationCommand safeCommand = Objects.requireNonNull(command,
                "command must not be null");
        List<AgentPublishCheckItem> items = new ArrayList<>();
        items.add(requiredText(
                AgentPublishCheckCode.INSTRUCTIONS_PRESENT,
                safeCommand.instructions(),
                "Instructions are present.",
                "Instructions are required before publishing."));
        items.add(toolsEnabled(safeCommand.toolIds()));
        items.add(highRiskApprovalPresent(safeCommand.toolIds()));
        items.add(AgentPublishCheckItem.pass(
                AgentPublishCheckCode.RESOURCE_ACL_PRESENT,
                "Resource ACL check is not required for this kernel validation slice."));
        items.add(AgentPublishCheckItem.warn(
                AgentPublishCheckCode.EVAL_PRESENT,
                "Evaluation platform is not enabled for this agent yet."));
        items.add(AgentPublishCheckItem.warn(
                AgentPublishCheckCode.QUOTA_PRESENT,
                "Quota policy is not enabled for this agent yet."));
        items.add(ownerPresent(safeCommand));
        items.add(requiredText(
                AgentPublishCheckCode.CHANGE_SUMMARY_PRESENT,
                safeCommand.changeSummary(),
                "Change summary is present.",
                "Change summary is required before publishing."));
        AgentPublishCheckReport report = new AgentPublishCheckReport(
                checkId(),
                safeCommand.agentId(),
                safeCommand.versionId(),
                null,
                items,
                clock.instant());
        return checkRepository.save(report);
    }

    @Override
    public Optional<AgentPublishCheckReport> latestPublishCheck(String agentId) {
        return checkRepository.latest(requireText(agentId, "agentId must not be blank"));
    }

    @Override
    public AgentRollbackResult rollback(AgentVersionRollbackCommand command) {
        ensureRollbackConfigured();
        AgentVersionRollbackCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentDefinition definition = definitionRepository.findById(safeCommand.agentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent definition not found"));
        if (!definition.tenantId().equals(safeCommand.tenantId())) {
            throw new IllegalArgumentException("Rollback target tenant does not match agent");
        }
        AgentVersion targetVersion = definitionRepository.findVersion(definition.agentId(), safeCommand.versionId())
                .orElseThrow(() -> new IllegalArgumentException("Rollback target version not found"));
        String previousVersionId = activationRepository.findActive(definition.agentId())
                .map(AgentVersionActivation::versionId)
                .orElse(definition.latestVersionId());
        if (targetVersion.versionId().equals(previousVersionId)) {
            return new AgentRollbackResult(
                    rollbackId(),
                    definition.agentId(),
                    previousVersionId,
                    targetVersion.versionId(),
                    AgentRollbackStatus.NOOP_ALREADY_ACTIVE,
                    safeCommand.reasonCode(),
                    clock.instant());
        }
        AgentVersionActivation activation = new AgentVersionActivation(
                activationId(),
                definition.tenantId(),
                definition.agentId(),
                targetVersion.versionId(),
                AgentVersionActivationType.ROLLBACK,
                previousVersionId,
                safeCommand.reasonCode(),
                safeCommand.operator(),
                clock.instant());
        AgentVersionActivation saved = activationRepository.activate(activation);
        definitionRepository.update(definition.publish(targetVersion.versionId(), saved.createdAt()));
        return new AgentRollbackResult(
                rollbackId(),
                definition.agentId(),
                previousVersionId,
                targetVersion.versionId(),
                AgentRollbackStatus.ROLLED_BACK,
                safeCommand.reasonCode(),
                saved.createdAt());
    }

    @Override
    public AgentCatalogPage catalog(AgentCatalogQuery query) {
        if (catalogQueryPort == null) {
            throw new IllegalStateException("Agent catalog query port is not configured");
        }
        return catalogQueryPort.page(Objects.requireNonNull(query, "query must not be null"));
    }

    private AgentPublishCheckItem toolsEnabled(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return AgentPublishCheckItem.fail(
                    AgentPublishCheckCode.TOOLS_ENABLED,
                    "At least one enabled tool is required.");
        }
        boolean allEnabled = toolIds.stream()
                .map(toolCatalogRepository::findById)
                .allMatch(optional -> optional
                        .filter(providerExposurePolicy::isToolAllowed)
                        .map(ToolCatalogEntry::enabled)
                        .orElse(false));
        if (allEnabled) {
            return AgentPublishCheckItem.pass(AgentPublishCheckCode.TOOLS_ENABLED, "All requested tools are enabled.");
        }
        return AgentPublishCheckItem.fail(AgentPublishCheckCode.TOOLS_ENABLED, "One or more requested tools are missing or disabled.");
    }

    private AgentPublishCheckItem highRiskApprovalPresent(List<String> toolIds) {
        boolean missingApproval = toolIds.stream()
                .map(toolCatalogRepository::findById)
                .flatMap(Optional::stream)
                .filter(providerExposurePolicy::isToolAllowed)
                .filter(this::highRisk)
                .anyMatch(tool -> !tool.requiresApproval());
        if (missingApproval) {
            return AgentPublishCheckItem.fail(
                    AgentPublishCheckCode.HIGH_RISK_APPROVAL_PRESENT,
                    "High risk tools require approval policy.");
        }
        return AgentPublishCheckItem.pass(
                AgentPublishCheckCode.HIGH_RISK_APPROVAL_PRESENT,
                "High risk approval policy is present or not required.");
    }

    private AgentPublishCheckItem ownerPresent(AgentPublishValidationCommand command) {
        if (hasText(command.ownerUserId()) && hasText(command.ownerTeam())) {
            return AgentPublishCheckItem.pass(AgentPublishCheckCode.OWNER_PRESENT, "Owner and owner team are present.");
        }
        return AgentPublishCheckItem.fail(AgentPublishCheckCode.OWNER_PRESENT, "Owner and owner team are required.");
    }

    private AgentPublishCheckItem requiredText(AgentPublishCheckCode code,
                                               String value,
                                               String passMessage,
                                               String failMessage) {
        if (hasText(value)) {
            return AgentPublishCheckItem.pass(code, passMessage);
        }
        return AgentPublishCheckItem.fail(code, failMessage);
    }

    private boolean highRisk(ToolCatalogEntry tool) {
        return tool.riskLevel() == ToolRiskLevel.HIGH || tool.riskLevel() == ToolRiskLevel.CRITICAL;
    }

    private boolean templateAllowed(AgentTemplate template) {
        return template.allowedToolIds().stream()
                .map(toolCatalogRepository::findById)
                .flatMap(Optional::stream)
                .allMatch(providerExposurePolicy::isToolAllowed);
    }

    private void requireTemplateAllowed(AgentTemplate template) {
        template.allowedToolIds().stream()
                .map(toolCatalogRepository::findById)
                .flatMap(Optional::stream)
                .forEach(providerExposurePolicy::requireToolAllowed);
    }

    private void requireToolsAllowed(List<String> toolIds) {
        if (toolIds == null) {
            return;
        }
        toolIds.stream()
                .map(toolCatalogRepository::findById)
                .flatMap(Optional::stream)
                .forEach(providerExposurePolicy::requireToolAllowed);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void ensureRollbackConfigured() {
        if (definitionRepository == null || activationRepository == null) {
            throw new IllegalStateException("Agent rollback repositories are not configured");
        }
    }

    private String checkId() {
        return CHECK_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private String rollbackId() {
        return ROLLBACK_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private String activationId() {
        return ACTIVATION_ID_PREFIX + SnowflakeIds.nextIdString();
    }
}
