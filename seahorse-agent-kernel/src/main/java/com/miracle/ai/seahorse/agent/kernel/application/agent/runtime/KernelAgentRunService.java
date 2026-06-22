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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.billing.QuotaEnforcementService;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentRunService implements AgentRunInboundPort {

    private static final String RUN_ID_PREFIX = "run_";
    private static final String VERSION_REQUIRED_MESSAGE = "Agent run requires a versionId";
    private static final String VERSION_NOT_FOUND_MESSAGE = "Agent version does not exist";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentDefinitionRepositoryPort definitionRepository;
    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final QuotaEnforcementService quotaEnforcementService;
    private final RunContextSnapshotRepositoryPort runContextSnapshotRepository;
    private final Optional<RunProfileInboundPort> runProfilePort;
    private final ObjectMapper objectMapper;

    public KernelAgentRunService(AgentDefinitionRepositoryPort definitionRepository,
                                 AgentRunRepositoryPort runRepository,
                                 CurrentUserPort currentUserPort,
                                 Clock clock) {
        this(definitionRepository, runRepository, currentUserPort, clock, null);
    }

    public KernelAgentRunService(AgentDefinitionRepositoryPort definitionRepository,
                                 AgentRunRepositoryPort runRepository,
                                 CurrentUserPort currentUserPort,
                                 Clock clock,
                                 QuotaEnforcementService quotaEnforcementService) {
        this(definitionRepository,
                runRepository,
                currentUserPort,
                clock,
                quotaEnforcementService,
                RunContextSnapshotRepositoryPort.noop(),
                null);
    }

    public KernelAgentRunService(AgentDefinitionRepositoryPort definitionRepository,
                                 AgentRunRepositoryPort runRepository,
                                 CurrentUserPort currentUserPort,
                                 Clock clock,
                                 QuotaEnforcementService quotaEnforcementService,
                                 RunContextSnapshotRepositoryPort runContextSnapshotRepository) {
        this(definitionRepository,
                runRepository,
                currentUserPort,
                clock,
                quotaEnforcementService,
                runContextSnapshotRepository,
                null);
    }

    public KernelAgentRunService(AgentDefinitionRepositoryPort definitionRepository,
                                 AgentRunRepositoryPort runRepository,
                                 CurrentUserPort currentUserPort,
                                 Clock clock,
                                 QuotaEnforcementService quotaEnforcementService,
                                 RunContextSnapshotRepositoryPort runContextSnapshotRepository,
                                 RunProfileInboundPort runProfilePort) {
        this.definitionRepository = Objects.requireNonNull(definitionRepository, "definitionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.quotaEnforcementService = quotaEnforcementService;
        this.runContextSnapshotRepository = Objects.requireNonNullElseGet(
                runContextSnapshotRepository,
                RunContextSnapshotRepositoryPort::noop);
        this.runProfilePort = Optional.ofNullable(runProfilePort);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AgentRun startRun(AgentRunStartCommand command) {
        AgentRunStartCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        AgentDefinition definition = loadDefinition(safeCommand.agentId());
        if (definition != null && definition.disabled()) {
            throw new IllegalStateException("DISABLED Agent cannot start a new run");
        }

        // Quota enforcement: check token and concurrency limits before starting a run
        if (quotaEnforcementService != null && safeCommand.tenantId() != null) {
            try {
                quotaEnforcementService.checkTokenQuota(safeCommand.tenantId());
            } catch (com.miracle.ai.seahorse.agent.kernel.domain.billing.QuotaExceededException ex) {
                throw ex;
            } catch (Exception ignored) {
                // Fail-open: quota system unavailable
            }
            try {
                quotaEnforcementService.checkConcurrencyQuota(safeCommand.tenantId());
            } catch (com.miracle.ai.seahorse.agent.kernel.domain.billing.QuotaExceededException ex) {
                throw ex;
            } catch (Exception ignored) {
                // Fail-open: quota system unavailable
            }
        }

        EffectiveRunProfileContext runProfileContext = effectiveRunProfileContext(safeCommand, currentUser);
        String versionId = resolveVersionId(safeCommand, definition);
        String metadataJson = runtimeMetadataJson(safeCommand, runProfileContext);
        AgentRun run = new AgentRun(
                nextRunId(),
                definition == null ? safeCommand.agentId() : definition.agentId(),
                versionId,
                safeCommand.rolloutId(),
                safeCommand.tenantId(),
                currentUser.operator(),
                safeCommand.conversationId(),
                safeCommand.triggerType(),
                safeCommand.inputSummary(),
                AgentRunStatus.RUNNING,
                safeCommand.traceId(),
                0L,
                0L,
                AgentRun.ZERO_COST,
                null,
                null,
                clock.instant(),
                null,
                metadataJson);
        runRepository.createRun(run);
        saveRunContextSnapshot(run, safeCommand, runProfileContext, metadataJson);
        return run;
    }

    private EffectiveRunProfileContext effectiveRunProfileContext(AgentRunStartCommand command, CurrentUser currentUser) {
        Long runProfileId = command.runProfileId();
        String executorEngine = trimToNull(command.executorEngine());
        Map<String, Object> executorConfig = command.executorConfig();
        if (runProfileId == null) {
            Optional<RunProfileRecord> appliedProfile = runProfilePort
                    .flatMap(port -> findAppliedRunProfile(port, currentUser, command.conversationId()))
                    .map(RunProfileDetails::getProfile)
                    .filter(profile -> profile.getId() != null);
            if (appliedProfile.isPresent()) {
                RunProfileRecord profile = appliedProfile.orElseThrow();
                runProfileId = profile.getId();
                if (executorEngine == null) {
                    executorEngine = trimToNull(profile.getExecutorEngine());
                }
                if (executorConfig == null || executorConfig.isEmpty()) {
                    executorConfig = readJsonObjectOrEmpty(profile.getExecutorConfigJson());
                }
            }
        }
        return new EffectiveRunProfileContext(
                runProfileId,
                executorEngine,
                executorConfig == null ? Map.of() : Map.copyOf(executorConfig));
    }

    private Optional<RunProfileDetails> findAppliedRunProfile(
            RunProfileInboundPort port,
            CurrentUser currentUser,
            String conversationId) {
        String normalizedConversationId = trimToNull(conversationId);
        if (normalizedConversationId == null) {
            return Optional.empty();
        }
        return port.findAppliedToConversation(currentUser.operator(), normalizedConversationId);
    }

    private String runtimeMetadataJson(AgentRunStartCommand command, EffectiveRunProfileContext runProfileContext) {
        Map<String, Object> metadata = readMetadata(command.metadataJson());
        if (runProfileContext.runProfileId() != null) {
            metadata.put("runProfileId", runProfileContext.runProfileId());
        }
        if (runProfileContext.executorEngine() != null) {
            metadata.put("executorEngine", runProfileContext.executorEngine());
        }
        if (!runProfileContext.executorConfig().isEmpty()) {
            metadata.put("executorConfig", runProfileContext.executorConfig());
        }
        if (metadata.isEmpty()) {
            return trimToNull(command.metadataJson());
        }
        return writeJson(metadata);
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        String normalized = trimToNull(metadataJson);
        if (normalized == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(normalized, MAP_TYPE);
        } catch (Exception ignored) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("legacyMetadataJson", normalized);
            return metadata;
        }
    }

    private Map<String, Object> readJsonObjectOrEmpty(String json) {
        String normalized = trimToNull(json);
        if (normalized == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(normalized, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void saveRunContextSnapshot(
            AgentRun run,
            AgentRunStartCommand command,
            EffectiveRunProfileContext runProfileContext,
            String metadataJson) {
        RunContextSnapshotRecord snapshot = new RunContextSnapshotRecord();
        snapshot.setTenantId(run.tenantId());
        snapshot.setRunId(run.runId());
        snapshot.setConversationId(parseLong(run.conversationId()));
        snapshot.setRunProfileId(runProfileContext.runProfileId());
        snapshot.setExecutorEngine(defaultText(runProfileContext.executorEngine(), "kernel"));
        snapshot.setExecutorConfigJson(writeJsonOrNull(runProfileContext.executorConfig()));
        snapshot.setTraceContextJson(traceContextJson(run));
        snapshot.setSnapshotJson(snapshotJson(run, command, runProfileContext, metadataJson));
        runContextSnapshotRepository.save(snapshot);
    }

    private String traceContextJson(AgentRun run) {
        Map<String, Object> traceContext = new LinkedHashMap<>();
        putIfPresent(traceContext, "traceId", run.traceId());
        putIfPresent(traceContext, "agentId", run.agentId());
        putIfPresent(traceContext, "versionId", run.versionId());
        putIfPresent(traceContext, "rolloutId", run.rolloutId());
        return writeJson(traceContext);
    }

    private String snapshotJson(
            AgentRun run,
            AgentRunStartCommand command,
            EffectiveRunProfileContext runProfileContext,
            String metadataJson) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", run.runId());
        putIfPresent(snapshot, "agentId", run.agentId());
        putIfPresent(snapshot, "versionId", run.versionId());
        putIfPresent(snapshot, "rolloutId", run.rolloutId());
        snapshot.put("tenantId", run.tenantId());
        snapshot.put("userId", run.userId());
        putIfPresent(snapshot, "conversationId", run.conversationId());
        snapshot.put("triggerType", run.triggerType().name());
        putIfPresent(snapshot, "inputSummary", run.inputSummary());
        putIfPresent(snapshot, "traceId", run.traceId());
        if (runProfileContext.runProfileId() != null) {
            snapshot.put("runProfileId", runProfileContext.runProfileId());
        }
        snapshot.put("executorEngine", defaultText(runProfileContext.executorEngine(), "kernel"));
        if (!runProfileContext.executorConfig().isEmpty()) {
            snapshot.put("executorConfig", runProfileContext.executorConfig());
        }
        Map<String, Object> metadata = readMetadata(metadataJson);
        if (!metadata.isEmpty()) {
            snapshot.put("metadata", metadata);
        }
        snapshot.put("startedAt", run.startedAt().toString());
        return writeJson(snapshot);
    }

    private String writeJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        return writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent run context", ex);
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        String text = trimToNull(value);
        if (text != null) {
            target.put(key, text);
        }
    }

    private Long parseLong(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveVersionId(AgentRunStartCommand command, AgentDefinition definition) {
        if (definition == null) {
            return command.versionId();
        }
        String requestedVersionId = trimToNull(command.versionId());
        if (requestedVersionId != null) {
            return definitionRepository.findVersion(definition.agentId(), requestedVersionId)
                    .map(AgentVersion::versionId)
                    .orElseThrow(() -> new IllegalArgumentException(VERSION_NOT_FOUND_MESSAGE));
        }
        String versionId = defaultText(null, definition.latestVersionId());
        if (versionId == null) {
            throw new IllegalStateException(VERSION_REQUIRED_MESSAGE);
        }
        return versionId;
    }

    @Override
    public Optional<AgentRun> findRunById(String runId) {
        currentUserPort.requireCurrentUser();
        return runRepository.findRunById(requireText(runId, "runId must not be blank"));
    }

    @Override
    public AgentRunPage page(AgentRunQuery query) {
        currentUserPort.requireCurrentUser();
        AgentRunQuery safeQuery = query == null
                ? new AgentRunQuery(null, null, null, null, null, 1L, 15L)
                : query;
        return runRepository.page(new AgentRunQuery(
                safeQuery.agentId(),
                safeQuery.runId(),
                safeQuery.rolloutId(),
                normalizeStatus(safeQuery.status()),
                safeQuery.from(),
                safeQuery.to(),
                safeQuery.current(),
                safeQuery.size()));
    }

    @Override
    public List<AgentStep> listSteps(String runId) {
        currentUserPort.requireCurrentUser();
        return runRepository.listSteps(requireText(runId, "runId must not be blank"));
    }

    @Override
    public AgentRun cancel(String runId) {
        currentUserPort.requireCurrentUser();
        AgentRun current = loadRun(runId);
        AgentRun cancelled = current.cancel(clock.instant());
        runRepository.updateRun(cancelled);
        return cancelled;
    }

    @Override
    public AgentRun retry(String runId) {
        currentUserPort.requireCurrentUser();
        AgentRun current = loadRun(runId);
        AgentRun retrying = current.retry();
        runRepository.updateRun(retrying);
        return retrying;
    }

    @Override
    public AgentRun succeed(String runId) {
        AgentRun current = loadRun(runId);
        if (!current.status().isWorkerRunnable()) {
            return current;
        }
        AgentRun succeeded = current.withStatus(AgentRunStatus.SUCCEEDED, null, null, clock.instant());
        runRepository.updateRun(succeeded);
        return succeeded;
    }

    @Override
    public AgentRun fail(String runId, String errorCode, String errorMessage) {
        AgentRun current = loadRun(runId);
        if (!current.status().isWorkerRunnable()) {
            return current;
        }
        AgentRun failed = current.withStatus(
                AgentRunStatus.FAILED,
                defaultText(errorCode, AgentRuntimeConstants.DEFAULT_AGENT_RUN_FAILURE_CODE),
                errorMessage,
                clock.instant());
        runRepository.updateRun(failed);
        return failed;
    }

    private AgentDefinition loadDefinition(String agentId) {
        String safeAgentId = requireText(agentId, "agentId must not be blank");
        Optional<AgentDefinition> definition = definitionRepository.findById(safeAgentId);
        if (definition.isPresent()) {
            return definition.get();
        }
        if (AgentRuntimeConstants.LEGACY_REACT_AGENT_ID.equals(safeAgentId)) {
            return null;
        }
        throw new IllegalArgumentException("Agent does not exist");
    }

    private AgentRun loadRun(String runId) {
        return runRepository.findRunById(requireText(runId, "runId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Agent run does not exist"));
    }

    private String nextRunId() {
        return RUN_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return defaultValue;
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String trimmed = trimToNull(status);
        if (trimmed == null) {
            return null;
        }
        return switch (trimmed.toUpperCase()) {
            case "ACTIVE" -> AgentRunStatus.RUNNING.name();
            case "COMPLETED" -> AgentRunStatus.SUCCEEDED.name();
            case "WAITING" -> AgentRunStatus.WAITING_APPROVAL.name();
            case "ERROR" -> AgentRunStatus.FAILED.name();
            case "PAUSED", "SUSPENDED" -> AgentRunStatus.RETRYING.name();
            default -> trimmed.toUpperCase();
        };
    }

    private record EffectiveRunProfileContext(
            Long runProfileId,
            String executorEngine,
            Map<String, Object> executorConfig) {
    }
}
