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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.LoadSkillResourceToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamAgentStepEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamApprovalRequiredEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamSkillEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamToolCallEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AgentLoopStreamEvents {

    private static final String MODEL_STEP_TITLE = "Model turn";
    private static final String TOOL_CALL_STARTED_SUMMARY = "Tool call started";
    private static final String TOOL_CALL_APPROVAL_SUMMARY = "Tool call requires approval";
    private static final String TOOL_CALL_SUCCEEDED_STATUS = "SUCCEEDED";
    private static final String TOOL_CALL_FAILED_STATUS = "FAILED";
    private static final String SKILL_STATUS_SELECTED = "SELECTED";
    private static final String SKILL_STATUS_LOADED = "LOADED";
    private static final String SKILL_STATUS_METADATA_ONLY = "METADATA_ONLY";
    private static final String SKILL_STATUS_SKIPPED = "SKIPPED";
    private static final String TOOL_ARGUMENT_KEYS_FIELD = "argumentKeys";
    private static final String TOOL_ARGUMENT_COUNT_FIELD = "argumentCount";
    private static final String WEB_SEARCH_TOOL_ID = "web_search";
    private static final String WEB_SEARCH_SOURCES_FIELD = "sources";
    private static final String ARTIFACT_ID_PREFIX = "artifact-";
    private static final String MARKDOWN_LANGUAGE = "markdown";
    private static final String MARKDOWN_ARTIFACT_TITLE = "Research report";
    private static final String ARTIFACT_CONTENT_FIELD = "content";
    private static final String ARTIFACT_CODE_FIELD = "code";
    private static final String ARTIFACT_LANGUAGE_FIELD = "language";
    private static final String ARTIFACT_TITLE_FIELD = "title";
    private static final String ARTIFACT_TYPE_FIELD = "artifactType";
    private static final String MARKDOWN_ARTIFACT_TYPE = "MARKDOWN";
    private static final ToolRiskLevel DEFAULT_TOOL_RISK_LEVEL = ToolRiskLevel.HIGH;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentStreamEmitter streamEmitter;

    AgentLoopStreamEvents(AgentStreamEmitter streamEmitter) {
        this.streamEmitter = Objects.requireNonNull(streamEmitter, "streamEmitter must not be null");
    }

    void emitStepStarted(
            StreamCallback callback,
            AgentLoopRequest request,
            String stepId,
            int stepNo,
            Instant startedAt) {
        emitEvent(callback, StreamEventType.STEP_STARTED, new StreamAgentStepEvent(
                request.runId(),
                stepId,
                stepNo,
                AgentStepType.MODEL_TURN,
                AgentStepStatus.RUNNING,
                MODEL_STEP_TITLE,
                null,
                startedAt,
                null,
                null,
                null,
                null,
                false));
    }

    void emitStepFinished(
            StreamCallback callback,
            AgentLoopRequest request,
            String stepId,
            int stepNo,
            Instant startedAt,
            AgentStepStatus status,
            Throwable error,
            String message) {
        Instant finishedAt = Instant.now();
        emitEvent(callback, StreamEventType.STEP_FINISHED, new StreamAgentStepEvent(
                request.runId(),
                stepId,
                stepNo,
                AgentStepType.MODEL_TURN,
                status,
                MODEL_STEP_TITLE,
                null,
                startedAt,
                finishedAt,
                Math.max(0L, Duration.between(startedAt, finishedAt).toMillis()),
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? message : Objects.requireNonNullElse(error.getMessage(), error.getClass().getName()),
                false));
    }

    void emitRecoverableError(StreamCallback callback, AgentLoopRequest request, String stepId, Throwable error) {
        emitEvent(callback, StreamEventType.RECOVERABLE_ERROR, new StreamAgentStepEvent(
                request.runId(),
                stepId,
                0,
                AgentStepType.MODEL_TURN,
                AgentStepStatus.FAILED,
                MODEL_STEP_TITLE,
                null,
                null,
                Instant.now(),
                null,
                error == null ? null : error.getClass().getSimpleName(),
                error == null ? null : Objects.requireNonNullElse(error.getMessage(), error.getClass().getName()),
                true));
    }

    void emitToolCallStarted(StreamCallback callback, AgentLoopRequest request, AgentToolCall toolCall) {
        emitEvent(callback, StreamEventType.TOOL_CALL_STARTED, new StreamToolCallEvent(
                request.runId(),
                toolCall.id(),
                toolCall.id(),
                toolCall.id(),
                toolCall.toolId(),
                DEFAULT_TOOL_RISK_LEVEL,
                TOOL_CALL_STARTED_SUMMARY,
                Instant.now(),
                null,
                null,
                null));
    }

    void emitToolCallWaitingUser(
            StreamCallback callback,
            AgentLoopRequest request,
            AgentToolCall toolCall,
            AgentObservation observation) {
        if (!isApprovalRequired(observation)) {
            return;
        }
        emitEvent(callback, StreamEventType.TOOL_CALL_WAITING_USER, new StreamApprovalRequiredEvent(
                request.runId(),
                toolCall.id(),
                observation.approvalId(),
                toolCall.id(),
                toolCall.toolId(),
                DEFAULT_TOOL_RISK_LEVEL,
                TOOL_CALL_APPROVAL_SUMMARY,
                argumentsPreview(toolCall),
                Instant.now()));
    }

    void emitToolCallFinished(
            StreamCallback callback,
            AgentLoopRequest request,
            AgentToolCall toolCall,
            AgentObservation observation) {
        Instant finishedAt = Instant.now();
        emitEvent(callback, StreamEventType.TOOL_CALL_FINISHED, new StreamToolCallEvent(
                request.runId(),
                toolCall.id(),
                toolCall.id(),
                toolCall.id(),
                toolCall.toolId(),
                DEFAULT_TOOL_RISK_LEVEL,
                observationText(observation),
                null,
                finishedAt,
                observation.success() ? null : observation.error(),
                observation.success() ? TOOL_CALL_SUCCEEDED_STATUS : TOOL_CALL_FAILED_STATUS));
    }

    void emitSkillRuntimeEvents(StreamCallback callback, AgentLoopRequest request) {
        if (request.skillRuntimeBlocks().isEmpty()) {
            return;
        }
        for (SkillRuntimeBlock skill : request.skillRuntimeBlocks()) {
            if (skill == null) {
                continue;
            }
            StreamEventType eventType = skill.injectMode() == SkillInjectMode.METADATA_AND_BODY && !skill.content().isBlank()
                    ? StreamEventType.SKILL_LOADED
                    : StreamEventType.SKILL_SELECTED;
            String status = eventType == StreamEventType.SKILL_LOADED
                    ? SKILL_STATUS_LOADED
                    : skill.injectMode() == SkillInjectMode.METADATA_ONLY
                    ? SKILL_STATUS_METADATA_ONLY
                    : SKILL_STATUS_SELECTED;
            emitEvent(callback, eventType, skillEvent(request, skill, null, status, null));
        }
    }

    void emitSkillResourceLoaded(
            StreamCallback callback,
            AgentLoopRequest request,
            AgentToolCall toolCall,
            AgentObservation observation) {
        if (!LoadSkillResourceToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            return;
        }
        String skillName = loadSkillName(toolCall);
        SkillRuntimeBlock skill = request.skillRuntimeBlocks().stream()
                .filter(candidate -> candidate.name().equals(skillName))
                .findFirst()
                .orElse(null);
        if (skill == null) {
            emitEvent(callback, StreamEventType.SKILL_SKIPPED, new StreamSkillEvent(
                    request.runId(),
                    skillName,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    loadSkillResourcePath(toolCall),
                    SKILL_STATUS_SKIPPED,
                    observation.error()));
            return;
        }
        emitEvent(callback, StreamEventType.SKILL_RESOURCE_LOADED, skillEvent(
                request,
                skill,
                loadSkillResourcePath(toolCall),
                observation.success() ? SKILL_STATUS_LOADED : SKILL_STATUS_SKIPPED,
                observation.success() ? null : observation.error()));
    }

    void emitToolThinking(StreamCallback callback, ModelTurn turn, List<AgentObservation> observations) {
        if (callback == null) {
            return;
        }
        if (!turn.thought().isBlank()) {
            callback.onThinking(turn.thought());
        }
        for (int i = 0; i < turn.toolCalls().size(); i++) {
            AgentToolCall toolCall = turn.toolCalls().get(i);
            AgentObservation observation = observations.get(i);
            callback.onThinking("[tool call] " + toolCall.toolId() + " -> "
                    + (observation.success() ? "ok" : "failed"));
        }
    }

    void emitSourcesFromObservations(
            StreamCallback callback,
            List<AgentToolCall> toolCalls,
            List<AgentObservation> observations) {
        if (callback == null || toolCalls == null || observations == null) {
            return;
        }
        for (int i = 0; i < Math.min(toolCalls.size(), observations.size()); i++) {
            AgentToolCall toolCall = toolCalls.get(i);
            AgentObservation observation = observations.get(i);
            if (toolCall == null || observation == null || !observation.success()
                    || !WEB_SEARCH_TOOL_ID.equals(toolCall.toolId())
                    || observation.content() == null || observation.content().isBlank()) {
                continue;
            }
            try {
                JsonNode sources = OBJECT_MAPPER.readTree(observation.content()).path(WEB_SEARCH_SOURCES_FIELD);
                if (sources.isArray() && !sources.isEmpty()) {
                    emitEvent(callback, StreamEventType.SOURCE_FOUND,
                            OBJECT_MAPPER.convertValue(sources, List.class));
                }
            } catch (Exception ignored) {
                // Tool observations are best-effort UI evidence; invalid JSON should not fail the run.
            }
        }
    }

    void emitContent(StreamCallback callback, String content) {
        if (callback != null && content != null && !content.isEmpty()) {
            callback.onContent(content);
        }
    }

    void emitFinalArtifact(StreamCallback callback, AgentLoopRequest request, String content) {
        if (callback == null || request == null || content == null || content.isBlank()
                || request.expectedOutputArtifactType() != OutputArtifactType.MARKDOWN) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", ARTIFACT_ID_PREFIX + Objects.requireNonNullElse(request.runId(), "live"));
        payload.put(ARTIFACT_TITLE_FIELD, MARKDOWN_ARTIFACT_TITLE);
        payload.put(ARTIFACT_LANGUAGE_FIELD, MARKDOWN_LANGUAGE);
        payload.put(ARTIFACT_TYPE_FIELD, MARKDOWN_ARTIFACT_TYPE);
        payload.put(ARTIFACT_CONTENT_FIELD, content);
        payload.put(ARTIFACT_CODE_FIELD, content);
        if (request.runId() != null && !request.runId().isBlank()) {
            payload.put("runId", request.runId());
        }
        emitEvent(callback, StreamEventType.ARTIFACT_CREATED, payload);
    }

    void emitComplete(StreamCallback callback) {
        if (callback != null) {
            callback.onComplete();
        }
    }

    private boolean isApprovalRequired(AgentObservation observation) {
        return observation != null
                && !observation.success()
                && ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED.equals(observation.error());
    }

    private StreamSkillEvent skillEvent(
            AgentLoopRequest request,
            SkillRuntimeBlock skill,
            String resourcePath,
            String status,
            String reason) {
        return new StreamSkillEvent(
                request.runId(),
                skill.name(),
                skill.revisionId(),
                skill.injectMode().name(),
                skill.category().name(),
                skill.description(),
                skill.allowedTools(),
                resourcePath,
                status,
                reason);
    }

    private Map<String, Object> argumentsPreview(AgentToolCall toolCall) {
        return Map.of(
                TOOL_ARGUMENT_KEYS_FIELD, toolCall.arguments().keySet(),
                TOOL_ARGUMENT_COUNT_FIELD, toolCall.arguments().size());
    }

    private void emitEvent(StreamCallback callback, StreamEventType eventType, Object payload) {
        streamEmitter.emitEvent(callback, eventType, payload);
    }

    private String observationText(AgentObservation observation) {
        return observation.success() ? observation.content() : observation.error();
    }

    private String loadSkillName(AgentToolCall toolCall) {
        Object value = toolCall.arguments().get("skillName");
        if (value == null) {
            value = toolCall.arguments().get("name");
        }
        return Objects.toString(value, "").trim();
    }

    private String loadSkillResourcePath(AgentToolCall toolCall) {
        Object value = toolCall.arguments().get("resourcePath");
        if (value == null) {
            return "SKILL.md";
        }
        return Objects.toString(value, "").trim();
    }
}
