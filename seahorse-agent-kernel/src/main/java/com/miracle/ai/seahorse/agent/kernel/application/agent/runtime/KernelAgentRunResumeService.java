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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentLoopCancelledException;
import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentLoopException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class KernelAgentRunResumeService implements AgentRunResumeInboundPort {

    private static final String RUN_NOT_FOUND = "Agent run does not exist";
    private static final String CHECKPOINT_NOT_FOUND = "Waiting approval checkpoint does not exist";
    private static final String APPROVAL_NOT_FOUND = "Approval decision does not exist";
    private static final String MODEL_PROTOCOL_ERROR = "Model did not finish resumed turn";
    private static final String RESULT_ID_PREFIX = "resume-step_";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentRunRepositoryPort runRepository;
    private final AgentCheckpointRepositoryPort checkpointRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final ToolGatewayPort toolGateway;
    private final StreamingChatModelPort modelPort;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public KernelAgentRunResumeService(AgentRunRepositoryPort runRepository,
                                       AgentCheckpointRepositoryPort checkpointRepository,
                                       ApprovalRequestQueryPort approvalQueryPort,
                                       ToolGatewayPort toolGateway,
                                       StreamingChatModelPort modelPort,
                                       CurrentUserPort currentUserPort,
                                       Clock clock) {
        this(runRepository, checkpointRepository, approvalQueryPort, toolGateway, modelPort, currentUserPort,
                clock, new ObjectMapper());
    }

    public KernelAgentRunResumeService(AgentRunRepositoryPort runRepository,
                                       AgentCheckpointRepositoryPort checkpointRepository,
                                       ApprovalRequestQueryPort approvalQueryPort,
                                       ToolGatewayPort toolGateway,
                                       StreamingChatModelPort modelPort,
                                       CurrentUserPort currentUserPort,
                                       Clock clock,
                                       ObjectMapper objectMapper) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.checkpointRepository = Objects.requireNonNull(
                checkpointRepository,
                "checkpointRepository must not be null");
        this.approvalQueryPort = Objects.requireNonNull(approvalQueryPort, "approvalQueryPort must not be null");
        this.toolGateway = Objects.requireNonNull(toolGateway, "toolGateway must not be null");
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public AgentRun resume(String runId) {
        currentUserPort.requireCurrentUser();
        AgentRun current = loadRun(runId);
        if (current.status() != AgentRunStatus.WAITING_APPROVAL) {
            return current;
        }
        AgentCheckpoint checkpoint = latestWaitingApprovalCheckpoint(current.runId());
        ApprovalRequest approval = approvalQueryPort
                .findLatestByRunIdAndStepId(current.runId(), checkpoint.stepId())
                .orElseThrow(() -> new IllegalStateException(APPROVAL_NOT_FOUND));
        if (approval.status() == ApprovalRequestStatus.REJECTED) {
            return transition(current, AgentRunStatus.REJECTED,
                    AgentRuntimeConstants.AGENT_RUN_APPROVAL_REJECTED_CODE,
                    approval.decisionComment());
        }
        if (approval.status() == ApprovalRequestStatus.EXPIRED) {
            return transition(current, AgentRunStatus.EXPIRED,
                    AgentRuntimeConstants.AGENT_RUN_APPROVAL_EXPIRED_CODE,
                    approval.decisionComment());
        }
        if (approval.status() != ApprovalRequestStatus.APPROVED && approval.status() != ApprovalRequestStatus.MODIFIED) {
            throw new IllegalStateException("Approval must be approved before resume");
        }

        AgentRun running = current.withStatus(AgentRunStatus.RUNNING, null, null, null);
        runRepository.updateRun(running);
        ToolInvocationRequest request = pendingToolInvocation(checkpoint, approval);
        ToolInvocationResult toolResult = toolGateway.invoke(request);
        appendToolStep(current.runId(), request, toolResult);
        if (!toolResult.success()) {
            return transition(running, AgentRunStatus.FAILED,
                    AgentRuntimeConstants.AGENT_RUN_RESUME_FAILED_CODE,
                    toolResult.error());
        }
        String finalAnswer = requestModelTurn(checkpoint, request, toolResult);
        appendModelStep(current.runId(), checkpoint, finalAnswer);
        return transition(running, AgentRunStatus.SUCCEEDED, null, null);
    }

    private AgentRun loadRun(String runId) {
        String safeRunId = requireText(runId, "runId must not be blank");
        return runRepository.findRunById(safeRunId)
                .orElseThrow(() -> new IllegalArgumentException(RUN_NOT_FOUND));
    }

    private AgentCheckpoint latestWaitingApprovalCheckpoint(String runId) {
        return checkpointRepository.findLatestByRunId(runId)
                .filter(checkpoint -> checkpoint.checkpointType() == AgentCheckpointType.WAITING_APPROVAL)
                .orElseThrow(() -> new IllegalStateException(CHECKPOINT_NOT_FOUND));
    }

    private ToolInvocationRequest pendingToolInvocation(AgentCheckpoint checkpoint, ApprovalRequest approval) {
        JsonNode root = readTree(checkpoint.pendingToolCallJson(), "pendingToolCallJson");
        return new ToolInvocationRequest(
                text(root, "runId"),
                text(root, "toolCallId"),
                text(root, "toolCallId"),
                text(root, "agentId"),
                text(root, "versionId"),
                text(root, "tenantId"),
                text(root, "userId"),
                text(root, "agentIdentityId"),
                text(root, "toolId"),
                approvalArguments(root, approval),
                stringMap(root.path("resourceRefs")),
                text(root, "idempotencyKey"),
                stringList(root.path("allowedToolIds")));
    }

    private Map<String, Object> approvalArguments(JsonNode root, ApprovalRequest approval) {
        Optional<Map<String, Object>> modifiedArguments = modifiedArguments(approval);
        return modifiedArguments.orElseGet(() -> objectMap(root.path("arguments")));
    }

    private Optional<Map<String, Object>> modifiedArguments(ApprovalRequest approval) {
        if (approval.status() != ApprovalRequestStatus.MODIFIED || isBlank(approval.argumentsPreviewJson())) {
            return Optional.empty();
        }
        JsonNode root = readTree(approval.argumentsPreviewJson(), "argumentsPreviewJson");
        JsonNode arguments = root.path("arguments");
        if (!arguments.isObject()) {
            return Optional.empty();
        }
        return Optional.of(objectMap(arguments));
    }

    private String requestModelTurn(AgentCheckpoint checkpoint,
                                    ToolInvocationRequest request,
                                    ToolInvocationResult toolResult) {
        List<ChatMessage> messages = messageHistory(checkpoint.messageHistoryJson());
        messages.add(ChatMessage.tool(request.toolCallId(), toolResult.content()));
        TurnBuffer callback = new TurnBuffer();
        StreamCancellationHandle handle = modelPort.streamChatWithTools(ChatRequest.builder()
                .messages(messages)
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .tools(List.of())
                .toolChoice("auto")
                .build(), callback, toolCalls -> callback.toolCalls.set(toolCalls == null ? List.of() : toolCalls));
        if (handle != null) {
            // The resumed turn is synchronous in this minimal runtime slice; cancellation is owned by future worker work.
        }
        callback.awaitCompletion();
        if (callback.error.get() != null) {
            throw new AgentLoopException("Resumed model turn failed", callback.error.get());
        }
        return callback.content.toString();
    }

    private void appendToolStep(String runId, ToolInvocationRequest request, ToolInvocationResult result) {
        Instant now = clock.instant();
        runRepository.appendStep(new AgentStep(
                nextStepId(),
                runId,
                nextStepNo(runId),
                AgentStepType.TOOL_CALL,
                result.success() ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                toJson(Map.of(
                        "toolCallId", request.toolCallId(),
                        "toolId", request.toolId(),
                        "arguments", request.arguments())),
                toJson(Map.of(
                        "success", result.success(),
                        "content", Objects.requireNonNullElse(result.content(), ""),
                        "error", Objects.requireNonNullElse(result.error(), ""))),
                result.success() ? null : AgentRuntimeConstants.AGENT_STEP_FAILURE_CODE,
                result.success() ? null : result.error(),
                now,
                now));
    }

    private void appendModelStep(String runId, AgentCheckpoint checkpoint, String finalAnswer) {
        Instant now = clock.instant();
        runRepository.appendStep(new AgentStep(
                nextStepId(),
                runId,
                nextStepNo(runId),
                AgentStepType.MODEL_TURN,
                AgentStepStatus.SUCCEEDED,
                checkpoint.messageHistoryJson(),
                toJson(Map.of("content", Objects.requireNonNullElse(finalAnswer, ""))),
                null,
                null,
                now,
                now));
    }

    private AgentRun transition(AgentRun run, AgentRunStatus status, String errorCode, String errorMessage) {
        AgentRun next = run.withStatus(
                status,
                errorCode,
                errorMessage,
                status.isFinished() ? clock.instant() : null);
        runRepository.updateRun(next);
        return next;
    }

    private List<ChatMessage> messageHistory(String messageHistoryJson) {
        if (isBlank(messageHistoryJson)) {
            return new ArrayList<>();
        }
        JsonNode root = readTree(messageHistoryJson, "messageHistoryJson");
        List<ChatMessage> messages = new ArrayList<>();
        if (!root.isArray()) {
            return messages;
        }
        for (JsonNode node : root) {
            ChatMessage message = new ChatMessage();
            message.setRole(role(node.path("role").asText(null)));
            message.setContent(node.path("content").asText(null));
            if (node.hasNonNull("thinkingContent")) {
                message.setThinkingContent(node.path("thinkingContent").asText());
            }
            if (node.hasNonNull("thinkingDuration")) {
                message.setThinkingDuration(node.path("thinkingDuration").asInt());
            }
            if (node.hasNonNull("toolCallId")) {
                message.setToolCallId(node.path("toolCallId").asText());
            }
            message.setToolCalls(toolCalls(node.path("toolCalls")));
            messages.add(message);
        }
        return messages;
    }

    private List<AgentToolCall> toolCalls(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray()) {
            return List.of();
        }
        List<AgentToolCall> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            String toolCallId = text(toolCallNode, "toolCallId");
            if (isBlank(toolCallId) && toolCallNode.hasNonNull("id")) {
                toolCallId = toolCallNode.path("id").asText();
            }
            toolCalls.add(AgentToolCall.of(
                    toolCallId,
                    text(toolCallNode, "toolId"),
                    objectMap(toolCallNode.path("arguments"))));
        }
        return toolCalls;
    }

    private ChatRole role(String role) {
        if (isBlank(role)) {
            return null;
        }
        return ChatRole.valueOf(role);
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private Map<String, String> stringMap(JsonNode node) {
        Map<String, Object> source = objectMap(node);
        if (source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(key, value == null ? null : value.toString()));
        return target;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private JsonNode readTree(String json, String label) {
        try {
            return objectMapper.readTree(requireText(json, label + " must not be blank"));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(label + " is not valid JSON", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private int nextStepNo(String runId) {
        return runRepository.listSteps(runId).size() + 1;
    }

    private String nextStepId() {
        return RESULT_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + escape(ex.getMessage()) + "\"}";
        }
    }

    private String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
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

    private static final class TurnBuffer implements StreamCallback {
        private final CountDownLatch done = new CountDownLatch(1);
        private final StringBuilder content = new StringBuilder();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final AtomicReference<List<AgentToolCall>> toolCalls = new AtomicReference<>(List.of());

        @Override
        public void onContent(String content) {
            if (content != null) {
                this.content.append(content);
            }
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error.set(error);
            done.countDown();
        }

        private void awaitCompletion() {
            try {
                if (!done.await(30, TimeUnit.SECONDS)) {
                    throw new AgentLoopException(MODEL_PROTOCOL_ERROR);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AgentLoopCancelledException("Agent run resume cancelled", ex);
            }
        }
    }
}
