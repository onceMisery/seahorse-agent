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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentRunService implements AgentRunInboundPort {

    private static final String RUN_ID_PREFIX = "run_";
    private static final String VERSION_REQUIRED_MESSAGE = "Agent run requires a versionId";
    private static final String VERSION_NOT_FOUND_MESSAGE = "Agent version does not exist";

    private final AgentDefinitionRepositoryPort definitionRepository;
    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final QuotaEnforcementService quotaEnforcementService;

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
        this.definitionRepository = Objects.requireNonNull(definitionRepository, "definitionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.quotaEnforcementService = quotaEnforcementService;
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

        String versionId = resolveVersionId(safeCommand, definition);
        AgentRun run = new AgentRun(
                nextRunId(),
                definition == null ? safeCommand.agentId() : definition.agentId(),
                versionId,
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
                null);
        runRepository.createRun(run);
        return run;
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
}
