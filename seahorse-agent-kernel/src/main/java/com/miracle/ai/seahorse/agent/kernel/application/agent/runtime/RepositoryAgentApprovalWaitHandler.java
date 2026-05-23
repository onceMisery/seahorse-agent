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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class RepositoryAgentApprovalWaitHandler implements AgentApprovalWaitHandler {

    private static final String CHECKPOINT_ID_PREFIX = "checkpoint_";
    private static final String REDACTED_VALUE = "[REDACTED]";
    private static final Set<String> SENSITIVE_ARGUMENT_NAMES = Set.of(
            "password",
            "secret",
            "token",
            "apiKey",
            "authorization",
            "credential");

    private final AgentRunRepositoryPort runRepository;
    private final AgentCheckpointRepositoryPort checkpointRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public RepositoryAgentApprovalWaitHandler(AgentRunRepositoryPort runRepository,
                                              AgentCheckpointRepositoryPort checkpointRepository,
                                              Clock clock) {
        this(runRepository, checkpointRepository, clock, new ObjectMapper());
    }

    public RepositoryAgentApprovalWaitHandler(AgentRunRepositoryPort runRepository,
                                              AgentCheckpointRepositoryPort checkpointRepository,
                                              Clock clock,
                                              ObjectMapper objectMapper) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.checkpointRepository = Objects.requireNonNull(
                checkpointRepository,
                "checkpointRepository must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    public static RepositoryAgentApprovalWaitHandler fromRepositories(AgentRunRepositoryPort runRepository,
                                                                      AgentCheckpointRepositoryPort checkpointRepository,
                                                                      Clock clock) {
        return new RepositoryAgentApprovalWaitHandler(runRepository, checkpointRepository, clock);
    }

    @Override
    public AgentCheckpoint waitForApproval(AgentApprovalWaitCommand command) {
        AgentApprovalWaitCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        ToolInvocationRequest request = safeCommand.toolInvocationRequest();
        if (isBlank(request.runId())) {
            return null;
        }
        Instant now = clock.instant();
        runRepository.findRunById(request.runId())
                .filter(run -> run.status() == AgentRunStatus.RUNNING)
                .map(run -> run.withStatus(AgentRunStatus.WAITING_APPROVAL, null, null, null))
                .ifPresent(runRepository::updateRun);
        AgentCheckpoint checkpoint = new AgentCheckpoint(
                nextCheckpointId(),
                request.runId(),
                request.stepId(),
                nextSequenceNo(request.runId()),
                AgentCheckpointType.WAITING_APPROVAL,
                safeCommand.stateJson(),
                messageHistoryJson(safeCommand.messageHistory()),
                null,
                pendingToolCallJson(request),
                now);
        checkpointRepository.save(checkpoint);
        return checkpoint;
    }

    private long nextSequenceNo(String runId) {
        return checkpointRepository.findLatestByRunId(runId)
                .map(AgentCheckpoint::sequenceNo)
                .orElse(0L) + 1L;
    }

    private String nextCheckpointId() {
        return CHECKPOINT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String messageHistoryJson(List<ChatMessage> messageHistory) {
        return toJson(messageHistory.stream()
                .map(this::messageJson)
                .toList());
    }

    private Map<String, Object> messageJson(ChatMessage message) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        if (message == null) {
            return payload;
        }
        payload.put("role", message.getRole() == null ? null : message.getRole().name());
        payload.put("content", message.getContent());
        payload.put("thinkingContent", message.getThinkingContent());
        payload.put("thinkingDuration", message.getThinkingDuration());
        payload.put("toolCallId", message.getToolCallId());
        payload.put("toolCalls", toolCallsJson(message.getToolCalls()));
        return payload;
    }

    private List<Map<String, Object>> toolCallsJson(List<AgentToolCall> toolCalls) {
        if (toolCalls == null) {
            return List.of();
        }
        return toolCalls.stream()
                .<Map<String, Object>>map(toolCall -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("toolCallId", toolCall.id());
                    payload.put("toolId", toolCall.toolId());
                    payload.put("arguments", redactArguments(toolCall.arguments()));
                    return payload;
                })
                .toList();
    }

    private String pendingToolCallJson(ToolInvocationRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolId", request.toolId());
        payload.put("toolCallId", request.toolCallId());
        payload.put("arguments", redactArguments(request.arguments()));
        payload.put("resourceRefs", request.resourceRefs());
        payload.put("idempotencyKey", request.idempotencyKey());
        payload.put("agentId", request.agentId());
        payload.put("versionId", request.versionId());
        payload.put("runId", request.runId());
        payload.put("tenantId", request.tenantId());
        payload.put("userId", request.userId());
        payload.put("agentIdentityId", request.agentIdentityId());
        payload.put("allowedToolIds", request.allowedToolIds());
        return toJson(payload);
    }

    private Map<String, Object> redactArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> redacted = new LinkedHashMap<>();
        arguments.forEach((key, value) -> redacted.put(key, shouldRedact(key) ? REDACTED_VALUE : value));
        return redacted;
    }

    private boolean shouldRedact(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("_", "").replace("-", "").toLowerCase();
        return SENSITIVE_ARGUMENT_NAMES.stream()
                .map(name -> name.toLowerCase().replace("_", "").replace("-", ""))
                .anyMatch(normalized::contains);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + escape(ex.getMessage()) + "\"}";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
