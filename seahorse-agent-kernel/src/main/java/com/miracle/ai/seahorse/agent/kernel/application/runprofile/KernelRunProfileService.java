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

package com.miracle.ai.seahorse.agent.kernel.application.runprofile;

import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileResolvedPreview;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileToolBindingCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
public class KernelRunProfileService implements RunProfileInboundPort {

    @NonNull
    private final RunProfileRepositoryPort repositoryPort;

    @Override
    public List<RunProfileRecord> list(String userId) {
        return repositoryPort.listByUser(requireText(userId, "userId must not be blank"));
    }

    @Override
    public Optional<RunProfileDetails> findById(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            return Optional.empty();
        }
        return repositoryPort.findById(safeUserId, id)
                .map(profile -> RunProfileDetails.builder()
                        .profile(profile)
                        .toolBindings(repositoryPort.listTools(id))
                        .build());
    }

    @Override
    public Optional<RunProfileResolvedPreview> resolvePreview(String userId, Long id) {
        return findById(userId, id).map(details -> {
            RunProfileRecord profile = details.getProfile();
            List<RunProfileToolBindingRecord> enabledTools = Objects.requireNonNullElse(
                            details.getToolBindings(),
                            List.<RunProfileToolBindingRecord>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(tool -> !Integer.valueOf(0).equals(tool.getEnabled()))
                    .toList();
            return RunProfileResolvedPreview.builder()
                    .runProfileId(profile.getId())
                    .roleCardId(profile.getRoleCardId())
                    .executorEngine(normalizeEngine(profile.getExecutorEngine()))
                    .executorConfigJson(profile.getExecutorConfigJson())
                    .modelConfigJson(profile.getModelConfigJson())
                    .memoryScopeJson(profile.getMemoryScopeJson())
                    .guardrailConfigJson(profile.getGuardrailConfigJson())
                    .explicitToolAllowlist(true)
                    .toolIds(toolIdsByProvider(enabledTools, "MCP", "A2A"))
                    .mcpToolIds(toolIdsForProvider(enabledTools, "MCP"))
                    .a2aAgentIds(toolIdsForProvider(enabledTools, "A2A"))
                    .build();
        });
    }

    @Override
    public RunProfileResolvedPreview applyToConversation(String userId, String conversationId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        String safeConversationId = requireText(conversationId, "conversationId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        RunProfileResolvedPreview preview = resolvePreview(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        repositoryPort.applyToConversation(safeUserId, safeConversationId, id);
        return preview;
    }

    @Override
    public Optional<RunProfileDetails> findAppliedToConversation(String userId, String conversationId) {
        String safeUserId = requireText(userId, "userId must not be blank");
        String safeConversationId = requireText(conversationId, "conversationId must not be blank");
        return repositoryPort.findAppliedProfileId(safeUserId, safeConversationId)
                .flatMap(id -> findById(safeUserId, id));
    }

    @Override
    public Long save(RunProfileCommand command) {
        RunProfileCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String userId = requireText(safeCommand.getUserId(), "userId must not be blank");
        String name = requireText(safeCommand.getName(), "name must not be blank");
        RunProfileRecord record = safeCommand.getId() == null
                ? new RunProfileRecord()
                : repositoryPort.findById(userId, safeCommand.getId())
                        .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        record.setId(safeCommand.getId());
        record.setUserId(userId);
        record.setName(name);
        record.setDescription(blankToNull(safeCommand.getDescription()));
        record.setRoleCardId(safeCommand.getRoleCardId());
        record.setExecutorEngine(normalizeEngine(safeCommand.getExecutorEngine()));
        record.setExecutorConfigJson(blankToNull(safeCommand.getExecutorConfigJson()));
        record.setModelConfigJson(blankToNull(safeCommand.getModelConfigJson()));
        record.setMemoryScopeJson(blankToNull(safeCommand.getMemoryScopeJson()));
        record.setGuardrailConfigJson(blankToNull(safeCommand.getGuardrailConfigJson()));
        if (record.getEnabled() == null) {
            record.setEnabled(0);
        }
        if (record.getDeleted() == null) {
            record.setDeleted(0);
        }
        Long id = repositoryPort.save(record);
        repositoryPort.replaceTools(id, toolRecords(id, safeCommand.getToolBindings()));
        return id;
    }

    @Override
    public void activate(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        repositoryPort.findById(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        repositoryPort.disableAll(safeUserId);
        repositoryPort.setEnabled(safeUserId, id, true);
    }

    @Override
    public void delete(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        if (id == null) {
            throw new IllegalArgumentException("runProfileId must not be null");
        }
        repositoryPort.delete(safeUserId, id);
    }

    private List<RunProfileToolBindingRecord> toolRecords(
            Long profileId,
            List<RunProfileToolBindingCommand> commands) {
        return Objects.requireNonNullElse(commands, List.<RunProfileToolBindingCommand>of())
                .stream()
                .filter(Objects::nonNull)
                .map(command -> {
                    RunProfileToolBindingRecord record = new RunProfileToolBindingRecord();
                    record.setProfileId(profileId);
                    record.setToolId(requireText(command.getToolId(), "toolId must not be blank"));
                    record.setProvider(requireText(command.getProvider(), "provider must not be blank"));
                    record.setEnabled(command.isEnabled() ? 1 : 0);
                    record.setDeleted(0);
                    return record;
                })
                .toList();
    }

    private String normalizeEngine(String executorEngine) {
        String normalized = blankToNull(executorEngine);
        return normalized == null ? "kernel" : normalized;
    }

    private List<String> toolIdsForProvider(List<RunProfileToolBindingRecord> tools, String provider) {
        return tools.stream()
                .filter(tool -> provider.equalsIgnoreCase(blankToNull(tool.getProvider())))
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> toolIdsByProvider(List<RunProfileToolBindingRecord> tools, String... excludedProviders) {
        return tools.stream()
                .filter(tool -> {
                    String provider = blankToNull(tool.getProvider());
                    for (String excludedProvider : excludedProviders) {
                        if (excludedProvider.equalsIgnoreCase(provider)) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(Objects::nonNull)
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
