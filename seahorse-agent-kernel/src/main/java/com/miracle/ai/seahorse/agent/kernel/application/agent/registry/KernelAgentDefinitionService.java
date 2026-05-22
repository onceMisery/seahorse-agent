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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentDefinitionUpdateDraftCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionPublishCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentDefinitionService implements AgentDefinitionInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String VERSION_ID_SEPARATOR = "-v";

    private final AgentDefinitionRepositoryPort repository;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;

    public KernelAgentDefinitionService(AgentDefinitionRepositoryPort repository,
                                        CurrentUserPort currentUserPort,
                                        Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String createDraft(AgentDefinitionCreateCommand command) {
        currentUserPort.requireRole(ADMIN_ROLE);
        AgentDefinitionCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        AgentDefinition definition = new AgentDefinition(
                safeCommand.agentId(),
                safeCommand.tenantId(),
                safeCommand.name(),
                safeCommand.description(),
                safeCommand.ownerUserId(),
                safeCommand.ownerTeam(),
                safeCommand.agentType(),
                safeCommand.baseAgentId(),
                AgentStatus.DRAFT,
                safeCommand.riskLevel(),
                null,
                now,
                now);
        repository.create(definition);
        return definition.agentId();
    }

    @Override
    public AgentDefinition updateDraft(String agentId, AgentDefinitionUpdateDraftCommand command) {
        currentUserPort.requireRole(ADMIN_ROLE);
        AgentDefinitionUpdateDraftCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentDefinition current = load(agentId);
        AgentDefinition updated = current.updateDraft(
                safeCommand.name(),
                safeCommand.description(),
                safeCommand.ownerTeam(),
                safeCommand.agentType(),
                safeCommand.riskLevel(),
                clock.instant());
        repository.update(updated);
        return updated;
    }

    @Override
    public AgentVersion publish(String agentId, AgentVersionPublishCommand command) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        currentUserPort.requireRole(ADMIN_ROLE);
        AgentVersionPublishCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        AgentDefinition definition = load(agentId);
        long versionNo = repository.nextVersionNo(definition.agentId());
        String versionId = definition.agentId() + VERSION_ID_SEPARATOR + versionNo;
        AgentVersion version = new AgentVersion(
                versionId,
                definition.agentId(),
                versionNo,
                safeCommand.instructions(),
                safeCommand.toolSetJson(),
                safeCommand.modelConfigJson(),
                safeCommand.memoryConfigJson(),
                safeCommand.guardrailConfigJson(),
                currentUser.userId(),
                clock.instant(),
                safeCommand.changeSummary());
        repository.saveVersion(version);
        repository.update(definition.publish(version.versionId(), version.publishedAt()));
        return version;
    }

    @Override
    public AgentDefinition disable(String agentId) {
        currentUserPort.requireRole(ADMIN_ROLE);
        AgentDefinition disabled = load(agentId).disable(clock.instant());
        repository.update(disabled);
        return disabled;
    }

    @Override
    public Optional<AgentDefinition> findById(String agentId) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return repository.findById(requireText(agentId, "agentId 不能为空"));
    }

    @Override
    public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return repository.page(tenantId, current, size, keyword);
    }

    private AgentDefinition load(String agentId) {
        return repository.findById(requireText(agentId, "agentId 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
