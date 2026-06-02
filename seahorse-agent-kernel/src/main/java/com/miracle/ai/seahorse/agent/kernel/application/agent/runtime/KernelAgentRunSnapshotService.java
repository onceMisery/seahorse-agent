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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunMessageSnapshot;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshot;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotSource;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotStep;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentRunSnapshotService implements AgentRunSnapshotInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "权限不足";
    private static final long PENDING_APPROVAL_PAGE_SIZE = 50L;
    private static final long INITIAL_EVENT_SEQUENCE = 0L;
    private static final String ASSISTANT_ROLE = "ASSISTANT";
    private static final String FIELD_ASSISTANT_MESSAGE_ID = "assistantMessageId";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_ID = "id";
    private static final String FIELD_MESSAGE_ID = "messageId";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_THINKING = "thinking";
    private static final String FIELD_THINKING_CONTENT = "thinkingContent";

    private final AgentRunRepositoryPort runRepository;
    private final AgentCheckpointRepositoryPort checkpointRepository;
    private final ContextPackRepositoryPort contextPackRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final AgentArtifactRepositoryPort artifactRepository;
    private final CurrentUserPort currentUserPort;
    private final ObjectMapper objectMapper;

    public KernelAgentRunSnapshotService(AgentRunRepositoryPort runRepository,
                                         AgentCheckpointRepositoryPort checkpointRepository,
                                         ContextPackRepositoryPort contextPackRepository,
                                         ApprovalRequestQueryPort approvalQueryPort,
                                         CurrentUserPort currentUserPort) {
        this(runRepository, checkpointRepository, contextPackRepository, approvalQueryPort, null, currentUserPort,
                new ObjectMapper());
    }

    public KernelAgentRunSnapshotService(AgentRunRepositoryPort runRepository,
                                         AgentCheckpointRepositoryPort checkpointRepository,
                                         ContextPackRepositoryPort contextPackRepository,
                                         ApprovalRequestQueryPort approvalQueryPort,
                                         AgentArtifactRepositoryPort artifactRepository,
                                         CurrentUserPort currentUserPort) {
        this(runRepository, checkpointRepository, contextPackRepository, approvalQueryPort, artifactRepository,
                currentUserPort, new ObjectMapper());
    }

    public KernelAgentRunSnapshotService(AgentRunRepositoryPort runRepository,
                                         AgentCheckpointRepositoryPort checkpointRepository,
                                         ContextPackRepositoryPort contextPackRepository,
                                         ApprovalRequestQueryPort approvalQueryPort,
                                         AgentArtifactRepositoryPort artifactRepository,
                                         CurrentUserPort currentUserPort,
                                         ObjectMapper objectMapper) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.checkpointRepository = Objects.requireNonNullElseGet(
                checkpointRepository,
                AgentCheckpointRepositoryPort::empty);
        this.contextPackRepository = Objects.requireNonNullElseGet(
                contextPackRepository,
                ContextPackRepositoryPort::empty);
        this.approvalQueryPort = Objects.requireNonNullElseGet(approvalQueryPort, ApprovalRequestQueryPort::empty);
        this.artifactRepository = Objects.requireNonNullElseGet(artifactRepository, AgentArtifactRepositoryPort::empty);
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public Optional<AgentRunSnapshot> findSnapshot(String runId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeRunId = requireText(runId, "runId must not be blank");
        return runRepository.findRunById(safeRunId)
                .map(run -> snapshotFor(requireReadable(run, currentUser), currentUser));
    }

    private AgentRunSnapshot snapshotFor(AgentRun run, CurrentUser currentUser) {
        List<AgentStep> steps = runRepository.listSteps(run.runId()).stream()
                .sorted(Comparator.comparingInt(AgentStep::stepNo))
                .toList();
        Optional<AgentCheckpoint> latestCheckpoint = checkpointRepository.findLatestByRunId(run.runId());
        List<AgentRunSnapshotSource> sources = sources(latestContextPackId(run.runId(), latestCheckpoint), run,
                currentUser);
        List<ApprovalRequest> pendingApprovals = pendingApprovals(run.runId(), currentUser);
        List<AgentArtifact> artifacts = artifacts(run.runId(), currentUser);
        return new AgentRunSnapshot(
                run,
                steps.stream().map(this::toSnapshotStep).toList(),
                latestCheckpoint,
                messageSnapshot(latestCheckpoint),
                currentStepId(steps, latestCheckpoint),
                sources,
                artifacts,
                pendingApprovals,
                latestCheckpoint.map(AgentCheckpoint::sequenceNo).orElse(INITIAL_EVENT_SEQUENCE),
                canResume(run, latestCheckpoint, pendingApprovals),
                run.status() == AgentRunStatus.FAILED);
    }

    private AgentRunMessageSnapshot messageSnapshot(Optional<AgentCheckpoint> latestCheckpoint) {
        return latestCheckpoint
                .map(AgentCheckpoint::messageHistoryJson)
                .map(this::messageSnapshot)
                .orElseGet(AgentRunMessageSnapshot::empty);
    }

    private AgentRunMessageSnapshot messageSnapshot(String messageHistoryJson) {
        if (!hasText(messageHistoryJson)) {
            return AgentRunMessageSnapshot.empty();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(messageHistoryJson);
        } catch (JsonProcessingException ex) {
            return AgentRunMessageSnapshot.empty();
        }
        if (!root.isArray()) {
            return AgentRunMessageSnapshot.empty();
        }
        AgentRunMessageSnapshot latest = AgentRunMessageSnapshot.empty();
        for (JsonNode node : root) {
            if (isAssistantMessage(node)) {
                latest = new AgentRunMessageSnapshot(
                        firstText(
                                text(node, FIELD_ASSISTANT_MESSAGE_ID),
                                text(node, FIELD_MESSAGE_ID),
                                text(node, FIELD_ID)),
                        text(node, FIELD_CONTENT),
                        firstText(text(node, FIELD_THINKING_CONTENT), text(node, FIELD_THINKING)));
            }
        }
        return latest;
    }

    private boolean isAssistantMessage(JsonNode node) {
        String role = text(node, FIELD_ROLE);
        return role != null && ASSISTANT_ROLE.equalsIgnoreCase(role);
    }

    private Optional<String> latestContextPackId(String runId, Optional<AgentCheckpoint> latestCheckpoint) {
        Optional<String> latestId = latestCheckpoint
                .map(AgentCheckpoint::contextPackId)
                .filter(this::hasText);
        if (latestId.isPresent()) {
            return latestId;
        }
        return checkpointRepository.listByRunId(runId).stream()
                .sorted(Comparator.comparingLong(AgentCheckpoint::sequenceNo).reversed())
                .map(AgentCheckpoint::contextPackId)
                .filter(this::hasText)
                .findFirst();
    }

    private List<AgentRunSnapshotSource> sources(Optional<String> contextPackId,
                                                 AgentRun run,
                                                 CurrentUser currentUser) {
        if (contextPackId.isEmpty()) {
            return List.of();
        }
        return contextPackRepository.findById(contextPackId.orElseThrow())
                .filter(pack -> packBelongsToRun(pack, run))
                .filter(pack -> isAdmin(currentUser) || currentUserId(currentUser).equals(pack.userId()))
                .map(pack -> contextPackRepository.listItems(pack.contextPackId()).stream()
                        .map(this::toSnapshotSource)
                        .toList())
                .orElse(List.of());
    }

    private boolean packBelongsToRun(ContextPack pack, AgentRun run) {
        return run.runId().equals(pack.runId()) && run.userId().equals(pack.userId());
    }

    private List<ApprovalRequest> pendingApprovals(String runId, CurrentUser currentUser) {
        ApprovalRequestPage page = approvalQueryPort.page(new ApprovalRequestQuery(
                null,
                runId,
                ApprovalRequestStatus.PENDING,
                ApprovalRequestQuery.DEFAULT_CURRENT,
                PENDING_APPROVAL_PAGE_SIZE));
        return page.records().stream()
                .filter(approval -> isAdmin(currentUser) || currentUserId(currentUser).equals(approval.userId()))
                .toList();
    }

    private List<AgentArtifact> artifacts(String runId, CurrentUser currentUser) {
        return artifactRepository.listByRunId(runId).stream()
                .filter(artifact -> isAdmin(currentUser) || currentUserId(currentUser).equals(artifact.userId()))
                .toList();
    }

    private AgentRunSnapshotStep toSnapshotStep(AgentStep step) {
        return new AgentRunSnapshotStep(
                step.stepId(),
                step.stepNo(),
                step.stepType(),
                step.status(),
                firstText(step.errorMessage(), step.outputJson(), step.inputJson()),
                step.errorCode(),
                step.errorMessage(),
                step.startedAt(),
                step.finishedAt());
    }

    private AgentRunSnapshotSource toSnapshotSource(ContextItem item) {
        String title = null;
        String url = null;
        String snippet = null;
        if (hasText(item.citationJson())) {
            try {
                JsonNode citation = objectMapper.readTree(item.citationJson());
                title = text(citation, "title");
                url = text(citation, "url");
                snippet = firstText(text(citation, "snippet"), text(citation, "text"));
            } catch (JsonProcessingException ignored) {
            }
        }
        return new AgentRunSnapshotSource(
                item.itemId(),
                item.contextPackId(),
                item.sourceType(),
                item.sourceId(),
                firstText(item.summary(), item.content()),
                item.score(),
                item.confidence(),
                item.sensitivity(),
                item.citationJson(),
                title,
                url,
                snippet,
                confidenceLevelFromScore(item.score()),
                null,
                null,
                0);
    }

    private static String confidenceLevelFromScore(double score) {
        if (score >= 0.85) return "HIGH";
        if (score >= 0.7) return "MEDIUM";
        if (score > 0) return "LOW";
        return "UNKNOWN";
    }

    private String currentStepId(List<AgentStep> steps, Optional<AgentCheckpoint> latestCheckpoint) {
        return steps.stream()
                .filter(step -> step.status() == AgentStepStatus.RUNNING)
                .map(AgentStep::stepId)
                .findFirst()
                .or(() -> latestCheckpoint.map(AgentCheckpoint::stepId).filter(this::hasText))
                .or(() -> steps.stream()
                        .max(Comparator.comparingInt(AgentStep::stepNo))
                        .map(AgentStep::stepId))
                .orElse(null);
    }

    private boolean canResume(AgentRun run,
                              Optional<AgentCheckpoint> latestCheckpoint,
                              List<ApprovalRequest> pendingApprovals) {
        return run.status() == AgentRunStatus.WAITING_APPROVAL
                || (!run.status().isFinished() && (latestCheckpoint.isPresent() || !pendingApprovals.isEmpty()));
    }

    private AgentRun requireReadable(AgentRun run, CurrentUser currentUser) {
        if (isAdmin(currentUser) || run.userId().equals(currentUserId(currentUser))) {
            return run;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null || currentUser.userId() == null ? null : String.valueOf(currentUser.userId());
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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
}
