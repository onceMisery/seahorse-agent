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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingItemCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentToolBindingReplaceCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolProviderExposurePolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class KernelAgentToolBindingManagementService implements AgentToolBindingManagementInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String BINDING_ID_PREFIX = "atb-";

    private final AgentToolBindingRepositoryPort bindingRepository;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final ToolCatalogRepositoryPort toolCatalogRepository;
    private final ToolProviderExposurePolicyPort providerExposurePolicy;

    public KernelAgentToolBindingManagementService(AgentToolBindingRepositoryPort bindingRepository,
                                                   CurrentUserPort currentUserPort,
                                                   Clock clock) {
        this(bindingRepository, currentUserPort, clock, ToolCatalogRepositoryPort.empty(),
                ToolProviderExposurePolicyPort.consumerWebDefaults());
    }

    public KernelAgentToolBindingManagementService(AgentToolBindingRepositoryPort bindingRepository,
                                                   CurrentUserPort currentUserPort,
                                                   Clock clock,
                                                   ToolCatalogRepositoryPort toolCatalogRepository) {
        this(bindingRepository, currentUserPort, clock, toolCatalogRepository,
                ToolProviderExposurePolicyPort.consumerWebDefaults());
    }

    public KernelAgentToolBindingManagementService(AgentToolBindingRepositoryPort bindingRepository,
                                                   CurrentUserPort currentUserPort,
                                                   Clock clock,
                                                   ToolCatalogRepositoryPort toolCatalogRepository,
                                                   ToolProviderExposurePolicyPort providerExposurePolicy) {
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.toolCatalogRepository = Objects.requireNonNullElseGet(toolCatalogRepository, ToolCatalogRepositoryPort::empty);
        this.providerExposurePolicy = Objects.requireNonNullElseGet(
                providerExposurePolicy,
                ToolProviderExposurePolicyPort::consumerWebDefaults);
    }

    @Override
    public List<AgentToolBinding> replaceBindings(String agentId,
                                                  String versionId,
                                                  AgentToolBindingReplaceCommand command) {
        currentUserPort.requireRole(ADMIN_ROLE);
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeAgentId = requireText(agentId, "agentId must not be blank");
        String safeVersionId = requireText(versionId, "versionId must not be blank");
        AgentToolBindingReplaceCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        List<AgentToolBinding> bindings = buildBindings(safeAgentId, safeVersionId, safeCommand, currentUser, now);

        bindingRepository.saveBindings(safeAgentId, safeVersionId, bindings);
        return List.copyOf(bindings);
    }

    private List<AgentToolBinding> buildBindings(String agentId,
                                                 String versionId,
                                                 AgentToolBindingReplaceCommand command,
                                                 CurrentUser currentUser,
                                                 Instant now) {
        Set<String> toolIds = new HashSet<>();
        List<AgentToolBinding> bindings = new ArrayList<>();
        for (AgentToolBindingItemCommand item : command.tools()) {
            AgentToolBindingItemCommand safeItem = Objects.requireNonNull(item, "tool binding item must not be null");
            String toolId = requireText(safeItem.toolId(), "toolId must not be blank");
            if (!toolIds.add(toolId)) {
                throw new IllegalArgumentException("toolId 不能重复");
            }
            toolCatalogRepository.findById(toolId)
                    .ifPresent(providerExposurePolicy::requireToolAllowed);
            bindings.add(new AgentToolBinding(
                    nextBindingId(),
                    agentId,
                    versionId,
                    toolId,
                    safeItem.maxCallsPerRun(),
                    safeItem.argumentPolicyJson(),
                    currentUser.userId(),
                    now));
        }
        return bindings;
    }

    private String nextBindingId() {
        return BINDING_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
