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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.hasText;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.parseLong;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.putTextIfPresent;
import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.stringValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.runcontext.RunContextSnapshotRedactor;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ResolvedRoleCard;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent Run 生命周期协作者（从 {@link KernelChatInboundService} 提取）。
 * 按 §7 收敛原则外提：只负责 AgentRun 启动/结束、运行上下文快照、运行元数据与模型用量记账。
 */
final class KernelChatAgentRunSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatAgentRunSupport.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final RunContextSnapshotRedactor RUN_CONTEXT_SNAPSHOT_REDACTOR = new RunContextSnapshotRedactor();

    private final Optional<AgentRunInboundPort> agentRunPort;
    private final Optional<RunContextSnapshotRepositoryPort> runContextSnapshotRepository;
    private final List<AgentRunMetadataContributor> agentRunMetadataContributors;
    private final Optional<CostUsageRepositoryPort> costUsageRepository;
    private final Optional<RoleCardInboundPort> roleCardPort;
    private final KernelChatModelConfigSupport modelConfigSupport;
    private final KernelChatToolSupport toolSupport;

    KernelChatAgentRunSupport(Optional<AgentRunInboundPort> agentRunPort,
                              Optional<RunContextSnapshotRepositoryPort> runContextSnapshotRepository,
                              List<AgentRunMetadataContributor> agentRunMetadataContributors,
                              Optional<CostUsageRepositoryPort> costUsageRepository,
                              Optional<RoleCardInboundPort> roleCardPort,
                              KernelChatModelConfigSupport modelConfigSupport,
                              KernelChatToolSupport toolSupport) {
        this.agentRunPort = agentRunPort == null ? Optional.empty() : agentRunPort;
        this.runContextSnapshotRepository = runContextSnapshotRepository == null
                ? Optional.empty()
                : runContextSnapshotRepository;
        this.agentRunMetadataContributors = agentRunMetadataContributors == null
                ? List.of()
                : List.copyOf(agentRunMetadataContributors);
        this.costUsageRepository = costUsageRepository == null ? Optional.empty() : costUsageRepository;
        this.roleCardPort = roleCardPort == null ? Optional.empty() : roleCardPort;
        this.modelConfigSupport = Objects.requireNonNull(modelConfigSupport, "modelConfigSupport must not be null");
        this.toolSupport = Objects.requireNonNull(toolSupport, "toolSupport must not be null");
    }

    ResolvedRoleCard resolveRoleCard(String userId, Long roleCardId) {
        return roleCardPort.flatMap(port -> port.resolve(userId, roleCardId)).orElse(null);
    }

    Long effectiveRoleCardId(StreamChatCommand command) {
        if (command.roleCardId() != null) {
            return command.roleCardId();
        }
        return modelConfigSupport.runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getRoleCardId)
                .orElse(null);
    }

    AgentRun startAgentRun(StreamChatCommand command, TraceRunScope traceRunScope, String metadataJson) {
        if (agentRunPort.isEmpty()) {
            return null;
        }
        return agentRunPort.get().startRun(new AgentRunStartCommand(
                toolSupport.selectedAgentId(command),
                command.versionId(),
                null,
                command.tenantId(),
                command.conversationId(),
                AgentRunTriggerType.CHAT,
                inputSummary(command.question()),
                traceRunScope == null ? null : traceRunScope.traceId(),
                metadataJson,
                modelConfigSupport.effectiveRunProfileId(command),
                modelConfigSupport.effectiveExecutorEngine(command),
                modelConfigSupport.effectiveExecutorConfig(command),
                command.currentUser()));
    }

    void saveRunContextSnapshot(StreamChatCommand command, TraceRunScope traceRunScope) {
        if (runContextSnapshotRepository.isEmpty()) {
            return;
        }
        try {
            RunContextSnapshotRecord record = new RunContextSnapshotRecord();
            record.setTenantId(hasText(command.tenantId()) ? command.tenantId() : AgentDefinition.DEFAULT_TENANT_ID);
            record.setRunId(command.taskId());
            record.setConversationId(parseLong(command.conversationId()));
            record.setBranchLeafMessageId(command.branchLeafMessageId());
            record.setRoleCardId(effectiveRoleCardId(command));
            record.setRunProfileId(modelConfigSupport.effectiveRunProfileId(command));
            record.setExecutorEngine(modelConfigSupport.effectiveExecutorEngine(command));
            record.setExecutorConfigJson(modelConfigSupport.effectiveExecutorConfigJson(command));
            record.setTraceContextJson(traceContextJson(traceRunScope, null, null));
            record.setSnapshotJson(runContextSnapshotJson(command, record.getExecutorEngine()));
            runContextSnapshotRepository.get().save(RUN_CONTEXT_SNAPSHOT_REDACTOR.redact(record));
        } catch (Exception ex) {
            LOG.warn("Failed to save chat run context snapshot: runId={}, conversationId={}",
                    command.taskId(), command.conversationId(), ex);
        }
    }

    private String runContextSnapshotJson(
            StreamChatCommand command,
            AgentRun run,
            String executorEngine,
            String metadataJson)
            throws JsonProcessingException {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("conversationId", command.conversationId());
        snapshot.put("branchLeafMessageId", command.branchLeafMessageId());
        snapshot.put("assistantParentMessageId", command.assistantParentMessageId());
        snapshot.put("roleCardId", effectiveRoleCardId(command));
        snapshot.put("runProfileId", modelConfigSupport.effectiveRunProfileId(command));
        snapshot.put("executorEngine", executorEngine);
        snapshot.put("agentId", run.agentId());
        snapshot.put("versionId", run.versionId());
        snapshot.put("rolloutId", run.rolloutId());
        snapshot.put("toolIds", toolSupport.allowedToolIds(command));
        snapshot.put("mcpToolIds", toolSupport.allowedToolIdsByProvider(command, "MCP"));
        snapshot.put("a2aAgentIds", toolSupport.allowedToolIdsByProvider(command, "A2A"));
        snapshot.put("explicitToolAllowlist", toolSupport.explicitToolAllowlist(command));
        snapshot.put("knowledgeBaseIds", command.knowledgeBaseIds());
        snapshot.put("modelConfig",
                modelConfigSupport.modelConfigSnapshot(modelConfigSupport.effectiveModelExecutionConfig(command, run.agentId(), run.versionId())));
        appendAgentScopeSnapshot(snapshot, metadataJson);
        modelConfigSupport.appendRunProfileSnapshot(snapshot, command);
        ResolvedRoleCard roleCard = resolveRoleCard(command.userId(), effectiveRoleCardId(command));
        if (roleCard != null) {
            snapshot.put("roleCard", roleCardSnapshot(roleCard));
        }
        return OBJECT_MAPPER.writeValueAsString(snapshot);
    }

    private String runContextSnapshotJson(StreamChatCommand command, String executorEngine)
            throws JsonProcessingException {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("conversationId", command.conversationId());
        snapshot.put("taskId", command.taskId());
        snapshot.put("chatMode", command.chatMode().name());
        snapshot.put("branchLeafMessageId", command.branchLeafMessageId());
        snapshot.put("assistantParentMessageId", command.assistantParentMessageId());
        snapshot.put("roleCardId", effectiveRoleCardId(command));
        snapshot.put("runProfileId", modelConfigSupport.effectiveRunProfileId(command));
        snapshot.put("executorEngine", executorEngine);
        snapshot.put("toolIds", toolSupport.allowedToolIds(command));
        snapshot.put("mcpToolIds", toolSupport.allowedToolIdsByProvider(command, "MCP"));
        snapshot.put("a2aAgentIds", toolSupport.allowedToolIdsByProvider(command, "A2A"));
        snapshot.put("explicitToolAllowlist", toolSupport.explicitToolAllowlist(command));
        snapshot.put("knowledgeBaseIds", command.knowledgeBaseIds());
        snapshot.put("attachmentIds", command.attachmentIds());
        snapshot.put("selectedSkillNames", command.selectedSkillNames());
        modelConfigSupport.appendRunProfileSnapshot(snapshot, command);
        ResolvedRoleCard roleCard = resolveRoleCard(command.userId(), effectiveRoleCardId(command));
        if (roleCard != null) {
            snapshot.put("roleCard", roleCardSnapshot(roleCard));
        }
        return OBJECT_MAPPER.writeValueAsString(snapshot);
    }

    private Map<String, Object> roleCardSnapshot(ResolvedRoleCard roleCard) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", roleCard.roleCardId());
        snapshot.put("name", roleCard.name());
        snapshot.put("definition", roleCard.definition());
        snapshot.put("higherPerm", roleCard.higherPerm());
        return snapshot;
    }

    private String traceContextJson(TraceRunScope traceRunScope, AgentRun run, String metadataJson)
            throws JsonProcessingException {
        String traceId = traceRunScope != null && hasText(traceRunScope.traceId())
                ? traceRunScope.traceId()
                : run == null ? null : run.traceId();
        Map<String, Object> traceContext = new LinkedHashMap<>();
        putTextIfPresent(traceContext, "traceId", traceId);
        if (traceRunScope != null) {
            putTextIfPresent(traceContext, "otelTraceId", traceRunScope.telemetryTraceId());
            putTextIfPresent(traceContext, "otelTraceUrl", traceRunScope.telemetryTraceUrl());
        }
        appendAgentScopeTraceContext(traceContext, agentScopeMetadata(metadataJson));
        if (traceContext.isEmpty()) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsString(traceContext);
    }

    private void appendAgentScopeSnapshot(Map<String, Object> snapshot, String metadataJson) {
        Map<String, Object> agentScope = agentScopeMetadata(metadataJson);
        if (!agentScope.isEmpty()) {
            snapshot.put("agentScope", agentScope);
        }
    }

    private void appendAgentScopeTraceContext(
            Map<String, Object> traceContext,
            Map<String, Object> agentScope) {
        if (agentScope.isEmpty()) {
            return;
        }
        putTextIfPresent(traceContext, "studioUrl", stringValue(agentScope.get("studioUrl")));
        putTextIfPresent(traceContext, "studioProject", stringValue(agentScope.get("studioProject")));
        putTextIfPresent(traceContext, "studioRunId", stringValue(agentScope.get("studioRunId")));
        putTextIfPresent(traceContext, "studioTraceUrl", stringValue(agentScope.get("studioTraceUrl")));
    }

    private Map<String, Object> agentScopeMetadata(String metadataJson) {
        if (!hasText(metadataJson)) {
            return Map.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(metadataJson);
            JsonNode agentScope = root.get("agentScope");
            if (agentScope == null || !agentScope.isObject()) {
                return Map.of();
            }
            return OBJECT_MAPPER.convertValue(
                    agentScope,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            LOG.warn("AgentScope metadata is not valid JSON, ignoring trace lookup metadata", ex);
            return Map.of();
        }
    }

    String agentRunMetadataJson(StreamChatCommand command) {
        try {
            String agentId = toolSupport.selectedAgentId(command);
            String versionId = command.versionId();
            Optional<AgentVersion> version = modelConfigSupport.selectedVersion(agentId, versionId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("engine", modelConfigSupport.effectiveExecutorEngine(command));
            metadata.put("agentVersion", version
                    .map(this::agentVersionSnapshot)
                    .orElseGet(() -> {
                        Map<String, Object> fallback = new LinkedHashMap<>();
                        fallback.put("agentId", agentId);
                        fallback.put("versionId", versionId);
                        return fallback;
                    }));
            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("allowedToolIds", toolSupport.agentRunSnapshotToolIds(command, version));
            metadata.put("runtime", runtime);
            Long roleCardId = effectiveRoleCardId(command);
            if (roleCardId != null) {
                metadata.put("roleCardId", roleCardId);
            }
            appendContributorMetadata(metadata, command);
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            LOG.warn("Failed to serialize agent run metadata snapshot: taskId={}, agentId={}",
                    command.taskId(), toolSupport.selectedAgentId(command), ex);
            return null;
        }
    }

    private void appendContributorMetadata(Map<String, Object> metadata, StreamChatCommand command) {
        for (AgentRunMetadataContributor contributor : agentRunMetadataContributors) {
            try {
                Map<String, Object> contributed = contributor.metadata(command);
                if (contributed == null || contributed.isEmpty()) {
                    continue;
                }
                contributed.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        metadata.put(key, value);
                    }
                });
            } catch (Exception ex) {
                LOG.warn("Failed to append agent run metadata contribution: taskId={}, agentId={}",
                        command.taskId(), toolSupport.selectedAgentId(command), ex);
            }
        }
    }

    private Map<String, Object> agentVersionSnapshot(AgentVersion version) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("agentId", version.agentId());
        snapshot.put("versionId", version.versionId());
        snapshot.put("instructions", version.instructions());
        snapshot.put("modelConfigJson", version.modelConfigJson());
        snapshot.put("toolSetJson", version.toolSetJson());
        snapshot.put("skillSetJson", version.skillSetJson());
        return snapshot;
    }

    private String inputSummary(String question) {
        String value = CredentialTextRedactor.redactStructured(Objects.requireNonNullElse(question, "")).trim();
        if (value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    void finishRun(String runId, Throwable error) {
        if (runId == null || agentRunPort.isEmpty()) {
            return;
        }
        if (error == null) {
            agentRunPort.get().succeed(runId);
            return;
        }
        agentRunPort.get().fail(runId, AgentRuntimeConstants.DEFAULT_AGENT_RUN_FAILURE_CODE,
                Objects.requireNonNullElse(error.getMessage(), error.getClass().getName()));
    }

    StreamCallback recordAgentUsageOnUsage(StreamCallback delegate,
                                           AgentRun run,
                                           AgentLoopRequest request) {
        if (costUsageRepository.isEmpty() || run == null) {
            return delegate;
        }
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
                delegate.onContent(content);
            }

            @Override
            public void onThinking(String content) {
                delegate.onThinking(content);
            }

            @Override
            public void onUsage(ChatTokenUsage usage) {
                delegate.onUsage(usage);
                appendAgentModelUsage(run, request, usage);
            }

            @Override
            public void onRunStarted(String runId) {
                delegate.onRunStarted(runId);
            }

            @Override
            public void onEvent(String eventName, Object payload) {
                delegate.onEvent(eventName, payload);
            }

            @Override
            public void onComplete() {
                delegate.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                delegate.onError(error);
            }
        };
    }

    private void appendAgentModelUsage(AgentRun run, AgentLoopRequest request, ChatTokenUsage usage) {
        if (usage == null || usage.totalTokens() <= 0) {
            return;
        }
        try {
            costUsageRepository.get().append(new CostUsageRecord(
                    UUID.randomUUID().toString(),
                    hasText(run.tenantId()) ? run.tenantId() : AgentDefinition.DEFAULT_TENANT_ID,
                    run.agentId(),
                    run.runId(),
                    run.rolloutId(),
                    run.userId(),
                    null,
                    request == null ? null : request.modelId(),
                    CostUsageSource.MODEL,
                    usage.totalTokens(),
                    1L,
                    0.0D,
                    "agentscope.model",
                    Instant.now()));
        } catch (Exception ex) {
            LOG.warn("Failed to record agent model token usage: runId={}, agentId={}",
                    run.runId(), run.agentId(), ex);
        }
    }
}
