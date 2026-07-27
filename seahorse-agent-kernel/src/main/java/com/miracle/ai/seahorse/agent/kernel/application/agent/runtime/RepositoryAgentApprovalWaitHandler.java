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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialJsonFieldClassifier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
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

public class RepositoryAgentApprovalWaitHandler implements AgentApprovalWaitHandler {

    private static final String CHECKPOINT_ID_PREFIX = "checkpoint_";
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
                checkpointStateJson(safeCommand),
                messageHistoryJson(safeCommand.messageHistory()),
                safeCommand.resumeDescriptor().contextPackId(),
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
        return CHECKPOINT_ID_PREFIX + SnowflakeIds.nextIdString();
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
        payload.put("content", safeStructuredText(message.getContent()));
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
        payload.put("resourceRefs", redactMap(request.resourceRefs()));
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
        return redactMap(arguments);
    }

    private Map<String, Object> redactMap(Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> redacted = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String fieldName = key == null ? null : String.valueOf(key);
            redacted.put(fieldName, redactValue(fieldName, value));
        });
        return redacted;
    }

    private Object redactValue(String fieldName, Object value) {
        if (fieldName != null && CredentialJsonFieldClassifier.isSensitiveProviderOrAuditField(fieldName)) {
            return CredentialTextRedactor.REDACTED_VALUE;
        }
        if (value instanceof String text) {
            return safeStructuredText(text);
        }
        if (value instanceof Map<?, ?> map) {
            return redactMap(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> redactValue(null, item)).toList();
        }
        return value;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + escape(safeText(ex.getMessage())) + "\"}";
        }
    }

    private String checkpointStateJson(AgentApprovalWaitCommand command) {
        try {
            JsonNode state = objectMapper.readTree(command.stateJson());
            if (!(state instanceof ObjectNode objectState)) {
                throw new IllegalStateException("Approval checkpoint state must be a JSON object");
            }
            ObjectNode checkpointState = objectState.deepCopy();
            checkpointState.set("resumeDescriptor", objectMapper.valueToTree(command.resumeDescriptor()));
            return toJson(redactValue(null, objectMapper.convertValue(checkpointState, Object.class)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Approval checkpoint state is not valid JSON", ex);
        }
    }

    private String safeText(String value) {
        return CredentialTextRedactor.redact(value);
    }

    private String safeStructuredText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.stripLeading();
        if (stripped.startsWith("{") || stripped.startsWith("[")) {
            try {
                Object structured = objectMapper.readValue(value, Object.class);
                return objectMapper.writeValueAsString(redactValue(null, structured));
            } catch (JsonProcessingException ignored) {
                // Continue with bounded text redaction for malformed or embedded JSON.
            }
        }
        return CredentialTextRedactor.redactStructured(value);
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
