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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopExitReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamApprovalRequiredEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AgentScopeReActExecutor implements ReActExecutorPort {

    private static final ObjectMapper TRACE_OBJECT_MAPPER = new ObjectMapper();
    private static final String WAITING_APPROVAL_MESSAGE = "Waiting for tool approval.";
    private static final String TOOL_CALL_APPROVAL_SUMMARY = "Tool call requires approval";
    private static final String AGENTSCOPE_STEP_ID = "agentscope-step";
    private static final String TRACE_TYPE_AGENT_STEP = "AGENT_STEP";
    private static final String TRACE_TYPE_AGENTSCOPE_AGENT = "AGENTSCOPE_AGENT";
    private static final String TRACE_TYPE_AGENTSCOPE_MODEL_CALL = "AGENTSCOPE_MODEL_CALL";
    private static final String TRACE_TYPE_AGENTSCOPE_TOOL_CALL = "AGENTSCOPE_TOOL_CALL";
    private static final String TRACE_TYPE_AGENTSCOPE_TOOL_RESULT = "AGENTSCOPE_TOOL_RESULT";
    private static final String TRACE_TYPE_AGENTSCOPE_APPROVAL = "AGENTSCOPE_APPROVAL";
    private static final String TRACE_CLASS_NAME =
            "com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeReActExecutor";

    private final AgentScopeAgentClient client;
    private final Executor asyncExecutor;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final AgentScopeObservationSupport observationSupport;
    private final KernelRagTraceRecorder traceRecorder;

    public AgentScopeReActExecutor(AgentScopeAgentClient client) {
        this(client, ForkJoinPool.commonPool());
    }

    public AgentScopeReActExecutor(AgentScopeAgentClient client, Executor asyncExecutor) {
        this(client, asyncExecutor, ApprovalRequestQueryPort.empty());
    }

    public AgentScopeReActExecutor(
            AgentScopeAgentClient client,
            Executor asyncExecutor,
            ApprovalRequestQueryPort approvalQueryPort) {
        this(client, asyncExecutor, approvalQueryPort, AgentScopeObservationSupport.noop());
    }

    public AgentScopeReActExecutor(
            AgentScopeAgentClient client,
            Executor asyncExecutor,
            ApprovalRequestQueryPort approvalQueryPort,
            AgentScopeObservationSupport observationSupport) {
        this(client, asyncExecutor, approvalQueryPort, observationSupport, KernelRagTraceRecorder.noop());
    }

    public AgentScopeReActExecutor(
            AgentScopeAgentClient client,
            Executor asyncExecutor,
            ApprovalRequestQueryPort approvalQueryPort,
            AgentScopeObservationSupport observationSupport,
            KernelRagTraceRecorder traceRecorder) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor must not be null");
        this.approvalQueryPort = Objects.requireNonNullElseGet(approvalQueryPort, ApprovalRequestQueryPort::empty);
        this.observationSupport = Objects.requireNonNullElseGet(observationSupport, AgentScopeObservationSupport::noop);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
    }

    @Override
    public AgentLoopResult execute(AgentLoopRequest request) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try (ObservationScope ignored = observationSupport.start("agentscope.execute", safeRequest.tenantId(),
                observationSupport.attributes(
                        "engine", engineId(),
                        "runId", Objects.requireNonNullElse(safeRequest.runId(), ""),
                        "agentName", Objects.requireNonNullElse(safeRequest.agentId(), "")))) {
            Msg response = client.call(safeRequest, toAgentScopeMessages(safeRequest));
            if (response != null && response.getGenerateReason() == GenerateReason.PERMISSION_ASKING) {
                return waitingApprovalResult();
            }
            String finalAnswer = response == null ? "" : response.getTextContent();
            return new AgentLoopResult(finalAnswer, List.of(AgentStep.finalAnswer(finalAnswer)), false);
        } catch (RuntimeException ex) {
            AgentScopeToolApprovalRequiredException approval = approvalRequired(ex);
            if (approval == null) {
                throw ex;
            }
            return waitingApprovalResult();
        }
    }

    @Override
    public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
        return streamExecute(request, callback, TraceRunScope.disabled());
    }

    @Override
    public StreamCancellationHandle streamExecute(
            AgentLoopRequest request,
            StreamCallback callback,
            TraceRunScope traceRunScope) {
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        String capturedTenant = TenantContext.capture();
        AtomicReference<TraceNodeScope> traceNodeScope = new AtomicReference<>(TraceNodeScope.disabled());
        AtomicReference<AgentScopeTraceState> runtimeTraceState = new AtomicReference<>();
        AtomicBoolean traceFinished = new AtomicBoolean(false);
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        CompletableFuture<Disposable> subscriptionFuture = CompletableFuture.supplyAsync(() -> {
            TenantContext.restore(capturedTenant);
            AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
            TraceNodeScope startedNode = traceRecorder.startNode(traceRunScope, agentScopeStepCommand(safeRequest));
            traceNodeScope.set(startedNode);
            AgentScopeTraceState traceState = new AgentScopeTraceState(traceRunScope, startedNode);
            runtimeTraceState.set(traceState);
            StreamCallback tracedCallback = finishTraceNodeOnTerminal(safeCallback, startedNode, traceFinished);
            ObservationScope scope = observationSupport.start("agentscope.execute", safeRequest.tenantId(),
                    observationSupport.attributes(
                            "engine", engineId(),
                            "runId", Objects.requireNonNullElse(safeRequest.runId(), ""),
                            "agentName", Objects.requireNonNullElse(safeRequest.agentId(), "")));
            try {
                if (cancellationRequested.get()) {
                    traceState.finishOpen(new CancellationException("stream cancelled"));
                    finishTraceNode(startedNode, traceFinished, new CancellationException("stream cancelled"));
                    return null;
                }
                StreamEmissionState emissionState = new StreamEmissionState();
                Disposable subscription = client.stream(safeRequest, toAgentScopeMessages(safeRequest))
                        .doFinally(ignored -> scope.close())
                        .subscribe(
                                event -> {
                                    traceState.record(event);
                                    emitEvent(event, safeRequest, tracedCallback, emissionState);
                                },
                                error -> {
                                    traceState.finishOpen(error);
                                    emitErrorOrApprovalRequired(error, safeRequest, tracedCallback);
                                },
                                () -> {
                                    traceState.finishOpen(null);
                                    tracedCallback.onComplete();
                                });
                if (cancellationRequested.get()) {
                    subscription.dispose();
                    traceState.finishOpen(new CancellationException("stream cancelled"));
                    finishTraceNode(startedNode, traceFinished, new CancellationException("stream cancelled"));
                }
                return subscription;
            } catch (Throwable ex) {
                scope.close();
                AgentScopeToolApprovalRequiredException approval = approvalRequired(ex);
                if (approval == null) {
                    traceState.finishOpen(ex);
                    emitRecoverableError(safeRequest, ex, tracedCallback);
                    tracedCallback.onError(ex);
                } else {
                    traceState.recordApprovalRequired(safeRequest, approval);
                    traceState.finishOpen(null);
                    emitApprovalRequired(safeRequest, approval, tracedCallback);
                }
                return null;
            } finally {
                TenantContext.clear();
            }
        }, asyncExecutor);
        return () -> {
            cancellationRequested.set(true);
            Disposable subscription = subscriptionFuture.getNow(null);
            if (subscription != null) {
                subscription.dispose();
            }
            subscriptionFuture.cancel(true);
            AgentScopeTraceState traceState = runtimeTraceState.get();
            if (traceState != null) {
                traceState.finishOpen(new CancellationException("stream cancelled"));
            }
            finishTraceNode(traceNodeScope.get(), traceFinished, new CancellationException("stream cancelled"));
        };
    }

    @Override
    public String engineId() {
        return "agentscope";
    }

    private TraceNodeStartCommand agentScopeStepCommand(AgentLoopRequest request) {
        return new TraceNodeStartCommand(
                AGENTSCOPE_STEP_ID,
                TRACE_TYPE_AGENT_STEP,
                TRACE_CLASS_NAME,
                "streamExecute",
                null,
                0,
                traceExtraData(request));
    }

    private String traceExtraData(AgentLoopRequest request) {
        if (request == null) {
            return "{\"engine\":\"agentscope\"}";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engine", "agentscope");
        payload.put("runId", request.runId());
        payload.put("agentId", request.agentId());
        return toJson(payload);
    }

    private StreamCallback finishTraceNodeOnTerminal(
            StreamCallback delegate,
            TraceNodeScope nodeScope,
            AtomicBoolean traceFinished) {
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
                try {
                    delegate.onComplete();
                } finally {
                    finishOnce(null);
                }
            }

            @Override
            public void onError(Throwable error) {
                try {
                    delegate.onError(error);
                } finally {
                    finishOnce(error);
                }
            }

            private void finishOnce(Throwable error) {
                finishTraceNode(nodeScope, traceFinished, error);
            }
        };
    }

    private void finishTraceNode(TraceNodeScope nodeScope, AtomicBoolean traceFinished, Throwable error) {
        if (!traceFinished.compareAndSet(false, true)) {
            return;
        }
        if (error == null) {
            traceRecorder.finishNode(nodeScope);
        } else {
            traceRecorder.finishNode(nodeScope, error);
        }
    }

    private void finishTraceNode(TraceNodeScope nodeScope, Throwable error, String extraData) {
        if (error == null) {
            traceRecorder.finishNode(nodeScope, null, extraData);
        } else {
            traceRecorder.finishNode(nodeScope, error, extraData);
        }
    }

    private AgentLoopResult waitingApprovalResult() {
        return new AgentLoopResult(
                WAITING_APPROVAL_MESSAGE,
                List.of(AgentStep.finalAnswer(WAITING_APPROVAL_MESSAGE)),
                false,
                AgentLoopExitReason.WAITING_APPROVAL);
    }

    private void emitEvent(
            AgentEvent event,
            AgentLoopRequest request,
            StreamCallback callback,
            StreamEmissionState emissionState) {
        if (event == null) {
            return;
        }
        if (event instanceof RequireUserConfirmEvent confirmation) {
            emitApprovalRequired(request, confirmation, callback);
            return;
        }
        if (event instanceof RequestStopEvent stop
                && stop.getGenerateReason() == GenerateReason.PERMISSION_ASKING) {
            return;
        }
        if (event instanceof ThinkingBlockDeltaEvent thinking) {
            emitThinking(thinking.getDelta(), callback);
            return;
        }
        if (event instanceof TextBlockDeltaEvent text) {
            if (emitContent(text.getDelta(), callback)) {
                emissionState.markTextDeltaEmitted();
            }
            return;
        }
        if (event instanceof ModelCallEndEvent modelCallEnd) {
            emitUsage(modelCallEnd.getUsage(), callback);
            return;
        }
        if (event instanceof AgentResultEvent result && result.getResult() != null) {
            emitUsage(result.getResult().getUsage(), callback);
            if (!emissionState.textDeltaEmitted()) {
                emitContent(result.getResult().getTextContent(), callback);
            }
            return;
        }
        emitProgress(event, callback);
    }

    private boolean emitContent(String content, StreamCallback callback) {
        if (content == null || content.isBlank()) {
            return false;
        }
        callback.onContent(content);
        return true;
    }

    private void emitThinking(String content, StreamCallback callback) {
        if (content == null || content.isBlank()) {
            return;
        }
        callback.onThinking(content);
    }

    private void emitUsage(ChatUsage usage, StreamCallback callback) {
        if (usage == null) {
            return;
        }
        callback.onUsage(new ChatTokenUsage(usage.getInputTokens(), usage.getOutputTokens()));
    }

    private void emitProgress(AgentEvent event, StreamCallback callback) {
        if (callback == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.getType() == null ? "unknown" : event.getType().getValue());
        if (event.getId() != null && !event.getId().isBlank()) {
            payload.put("id", event.getId());
        }
        if (event.getSource() != null && !event.getSource().isBlank()) {
            payload.put("source", event.getSource());
        }
        if (event.getCreatedAt() != null && !event.getCreatedAt().isBlank()) {
            payload.put("createdAt", event.getCreatedAt());
        }
        if (event.getMetadata() != null && !event.getMetadata().isEmpty()) {
            payload.put("metadata", event.getMetadata());
        }
        callback.onEvent(StreamEventType.STEP_PROGRESS.value(), Map.copyOf(payload));
    }

    private void emitErrorOrApprovalRequired(Throwable error, AgentLoopRequest request, StreamCallback callback) {
        AgentScopeToolApprovalRequiredException approval = approvalRequired(error);
        if (approval == null) {
            emitRecoverableError(request, error, callback);
            callback.onError(error);
            return;
        }
        emitApprovalRequired(request, approval, callback);
    }

    private void emitRecoverableError(AgentLoopRequest request, Throwable error, StreamCallback callback) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", request == null ? "" : Objects.requireNonNullElse(request.runId(), ""));
        payload.put("stepId", AGENTSCOPE_STEP_ID);
        payload.put("errorType", error == null ? "" : error.getClass().getSimpleName());
        payload.put("message", errorMessage(error));
        callback.onEvent(StreamEventType.RECOVERABLE_ERROR.value(), Map.copyOf(payload));
    }

    private String errorMessage(Throwable error) {
        if (error == null) {
            return "";
        }
        return Objects.requireNonNullElse(error.getMessage(), error.getClass().getName());
    }

    private void emitApprovalRequired(
            AgentLoopRequest request,
            AgentScopeToolApprovalRequiredException approval,
            StreamCallback callback) {
        callback.onEvent(StreamEventType.TOOL_CALL_WAITING_USER.value(), new StreamApprovalRequiredEvent(
                request.runId(),
                AGENTSCOPE_STEP_ID,
                approval.approvalId(),
                approval.toolCallId(),
                approval.toolId(),
                ToolRiskLevel.MEDIUM,
                TOOL_CALL_APPROVAL_SUMMARY,
                Map.of(),
                Instant.now()));
        callback.onContent(WAITING_APPROVAL_MESSAGE);
        callback.onComplete();
    }

    private void emitApprovalRequired(
            AgentLoopRequest request,
            RequireUserConfirmEvent confirmation,
            StreamCallback callback) {
        ToolUseBlock toolCall = confirmation.getToolCalls().isEmpty()
                ? null
                : confirmation.getToolCalls().get(0);
        Optional<ApprovalRequest> approval = latestApproval(request);
        String approvalId = approval.map(ApprovalRequest::approvalId)
                .orElseGet(() -> toolCall == null ? "" : Objects.requireNonNullElse(toolCall.getId(), ""));
        String toolInvocationId = toolCall == null ? "" : Objects.requireNonNullElse(toolCall.getId(), "");
        String toolId = toolCall == null ? "" : Objects.requireNonNullElse(toolCall.getName(), "");
        callback.onEvent(StreamEventType.TOOL_CALL_WAITING_USER.value(), new StreamApprovalRequiredEvent(
                request.runId(),
                AGENTSCOPE_STEP_ID,
                approvalId,
                toolInvocationId,
                toolId,
                approval.map(ApprovalRequest::riskLevel).orElse(ToolRiskLevel.MEDIUM),
                approval.map(ApprovalRequest::summary).orElse(TOOL_CALL_APPROVAL_SUMMARY),
                toolCall == null ? Map.of() : Map.copyOf(toolCall.getInput()),
                approval.map(ApprovalRequest::requestedAt).orElseGet(Instant::now)));
        callback.onContent(WAITING_APPROVAL_MESSAGE);
    }

    private Optional<ApprovalRequest> latestApproval(AgentLoopRequest request) {
        if (request == null || request.runId() == null || request.runId().isBlank()) {
            return Optional.empty();
        }
        return approvalQueryPort.findLatestByRunIdAndStepId(request.runId(), AGENTSCOPE_STEP_ID);
    }

    private AgentScopeToolApprovalRequiredException approvalRequired(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AgentScopeToolApprovalRequiredException approval) {
                return approval;
            }
            current = current.getCause();
        }
        return null;
    }

    private List<Msg> toAgentScopeMessages(AgentLoopRequest request) {
        List<Msg> messages = new ArrayList<>();
        for (ChatMessage historyMessage : request.history()) {
            if (historyMessage != null) {
                messages.add(toAgentScopeMessage(historyMessage.getRole(), historyMessage.getContent()));
            }
        }
        messages.add(Msg.builder()
                .role(MsgRole.USER)
                .textContent(request.question())
                .build());
        return messages;
    }

    private Msg toAgentScopeMessage(ChatRole role, String content) {
        return Msg.builder()
                .role(toMsgRole(role))
                .textContent(Objects.requireNonNullElse(content, ""))
                .build();
    }

    private MsgRole toMsgRole(ChatRole role) {
        if (role == null) {
            return MsgRole.USER;
        }
        return switch (role) {
            case SYSTEM -> MsgRole.SYSTEM;
            case ASSISTANT -> MsgRole.ASSISTANT;
            case TOOL -> MsgRole.TOOL;
            case USER -> MsgRole.USER;
        };
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return TRACE_OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String textOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String safeNodeSuffix(String value) {
        String safeValue = textOrDefault(value, "unknown").trim();
        return safeValue.length() <= 80 ? safeValue : safeValue.substring(0, 80);
    }

    private String eventKey(String primary, AgentEvent event) {
        if (hasText(primary)) {
            return primary;
        }
        if (event != null && hasText(event.getId())) {
            return event.getId();
        }
        return "";
    }

    private String agentscopeEventExtraData(String eventType, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (value != null && (!(value instanceof String text) || hasText(text))) {
                    payload.put(key, value);
                }
            });
        }
        return toJson(payload);
    }

    private static final class StreamEmissionState {
        private boolean textDeltaEmitted;

        private void markTextDeltaEmitted() {
            textDeltaEmitted = true;
        }

        private boolean textDeltaEmitted() {
            return textDeltaEmitted;
        }
    }

    private final class AgentScopeTraceState {
        private final TraceRunScope runScope;
        private final ActiveTraceNode root;
        private final Map<String, ActiveTraceNode> modelCalls = new HashMap<>();
        private final Map<String, ActiveTraceNode> toolCalls = new HashMap<>();
        private final Map<String, ActiveTraceNode> toolResults = new HashMap<>();
        private ActiveTraceNode agent;
        private boolean closed;

        private AgentScopeTraceState(TraceRunScope runScope, TraceNodeScope rootScope) {
            this.runScope = runScope;
            this.root = new ActiveTraceNode(rootScope, 0);
        }

        private synchronized void record(AgentEvent event) {
            if (closed || event == null || !root.scope().active()) {
                return;
            }
            if (event instanceof AgentStartEvent start) {
                startAgent(start);
                return;
            }
            if (event instanceof AgentEndEvent end) {
                finishAgent(end);
                return;
            }
            if (event instanceof ModelCallStartEvent start) {
                startModelCall(start);
                return;
            }
            if (event instanceof ModelCallEndEvent end) {
                finishModelCall(end);
                return;
            }
            if (event instanceof ToolCallStartEvent start) {
                startToolCall(start);
                return;
            }
            if (event instanceof ToolCallEndEvent end) {
                finishToolCall(end);
                return;
            }
            if (event instanceof ToolResultStartEvent start) {
                startToolResult(start);
                return;
            }
            if (event instanceof ToolResultEndEvent end) {
                finishToolResult(end);
                return;
            }
            if (event instanceof RequireUserConfirmEvent confirmation) {
                recordApprovalRequired(confirmation);
            }
        }

        private synchronized void recordApprovalRequired(
                AgentLoopRequest request,
                AgentScopeToolApprovalRequiredException approval) {
            if (closed || !root.scope().active()) {
                return;
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("runId", request == null ? null : request.runId());
            fields.put("approvalId", approval == null ? null : approval.approvalId());
            fields.put("toolCallId", approval == null ? null : approval.toolCallId());
            fields.put("toolId", approval == null ? null : approval.toolId());
            recordInstant("agentscope-approval-required", TRACE_TYPE_AGENTSCOPE_APPROVAL, "approvalRequired",
                    agentscopeEventExtraData("approval_required", fields));
        }

        private synchronized void finishOpen(Throwable error) {
            if (closed) {
                return;
            }
            closed = true;
            finishAll(toolResults, error);
            finishAll(toolCalls, error);
            finishAll(modelCalls, error);
            finishActive(agent, error, null);
            agent = null;
        }

        private void startAgent(AgentStartEvent event) {
            finishActive(agent, null, null);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("sessionId", event.getSessionId());
            fields.put("replyId", event.getReplyId());
            fields.put("name", event.getName());
            fields.put("role", event.getRole());
            agent = startChild(
                    "agentscope-agent:" + safeNodeSuffix(event.getName()),
                    TRACE_TYPE_AGENTSCOPE_AGENT,
                    "agent",
                    root,
                    agentscopeEventExtraData("agent_start", fields));
        }

        private void finishAgent(AgentEndEvent event) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("replyId", event.getReplyId());
            finishActive(agent, null, agentscopeEventExtraData("agent_end", fields));
            agent = null;
        }

        private void startModelCall(ModelCallStartEvent event) {
            String key = eventKey(event.getReplyId(), event);
            ActiveTraceNode parent = activeParent();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("replyId", event.getReplyId());
            modelCalls.put(key, startChild(
                    "agentscope-model-call",
                    TRACE_TYPE_AGENTSCOPE_MODEL_CALL,
                    "modelCall",
                    parent,
                    agentscopeEventExtraData("model_call_start", fields)));
        }

        private void finishModelCall(ModelCallEndEvent event) {
            String key = eventKey(event.getReplyId(), event);
            ActiveTraceNode node = removeActive(modelCalls, key);
            if (node == null) {
                node = startChild(
                        "agentscope-model-call",
                        TRACE_TYPE_AGENTSCOPE_MODEL_CALL,
                        "modelCall",
                        activeParent(),
                        null);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("replyId", event.getReplyId());
            fields.put("usage", usageData(event.getUsage()));
            finishActive(node, null, agentscopeEventExtraData("model_call_end", fields));
        }

        private void startToolCall(ToolCallStartEvent event) {
            String key = eventKey(event.getToolCallId(), event);
            Map<String, Object> fields = toolFields("tool_call_start", event.getReplyId(), event.getToolCallId(),
                    event.getToolCallName());
            toolCalls.put(key, startChild(
                    "agentscope-tool-call:" + safeNodeSuffix(event.getToolCallName()),
                    TRACE_TYPE_AGENTSCOPE_TOOL_CALL,
                    "toolCall",
                    activeParent(),
                    agentscopeEventExtraData("tool_call_start", fields)));
        }

        private void finishToolCall(ToolCallEndEvent event) {
            String key = eventKey(event.getToolCallId(), event);
            Map<String, Object> fields = toolFields("tool_call_end", event.getReplyId(), event.getToolCallId(),
                    event.getToolCallName());
            finishActive(removeActive(toolCalls, key), null, agentscopeEventExtraData("tool_call_end", fields));
        }

        private void startToolResult(ToolResultStartEvent event) {
            String key = eventKey(event.getToolCallId(), event);
            Map<String, Object> fields = toolFields("tool_result_start", event.getReplyId(), event.getToolCallId(),
                    event.getToolCallName());
            toolResults.put(key, startChild(
                    "agentscope-tool-result:" + safeNodeSuffix(event.getToolCallName()),
                    TRACE_TYPE_AGENTSCOPE_TOOL_RESULT,
                    "toolResult",
                    activeParent(),
                    agentscopeEventExtraData("tool_result_start", fields)));
        }

        private void finishToolResult(ToolResultEndEvent event) {
            String key = eventKey(event.getToolCallId(), event);
            Map<String, Object> fields = toolFields("tool_result_end", event.getReplyId(), event.getToolCallId(),
                    event.getToolCallName());
            fields.put("state", event.getState() == null ? null : event.getState().getValue());
            Throwable error = event.getState() == null || event.getState() == ToolResultState.SUCCESS
                    ? null
                    : new IllegalStateException("AgentScope tool result state: " + event.getState().getValue());
            finishActive(removeActive(toolResults, key), error, agentscopeEventExtraData("tool_result_end", fields));
        }

        private void recordApprovalRequired(RequireUserConfirmEvent event) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("replyId", event.getReplyId());
            fields.put("toolCallCount", event.getToolCalls() == null ? 0 : event.getToolCalls().size());
            recordInstant("agentscope-approval-required", TRACE_TYPE_AGENTSCOPE_APPROVAL, "approvalRequired",
                    agentscopeEventExtraData("require_user_confirm", fields));
        }

        private void recordInstant(String nodeName, String nodeType, String methodName, String extraData) {
            ActiveTraceNode node = startChild(nodeName, nodeType, methodName, activeParent(), extraData);
            finishActive(node, null, extraData);
        }

        private ActiveTraceNode startChild(
                String nodeName,
                String nodeType,
                String methodName,
                ActiveTraceNode parent,
                String extraData) {
            if (runScope == null || !runScope.active() || parent == null || !parent.scope().active()) {
                return ActiveTraceNode.disabled();
            }
            TraceNodeScope scope = traceRecorder.startNode(runScope, new TraceNodeStartCommand(
                    nodeName,
                    nodeType,
                    TRACE_CLASS_NAME,
                    methodName,
                    parent.scope().nodeId(),
                    parent.depth() + 1,
                    extraData));
            return new ActiveTraceNode(scope, parent.depth() + 1);
        }

        private ActiveTraceNode activeParent() {
            return agent == null || !agent.scope().active() ? root : agent;
        }

        private ActiveTraceNode removeActive(Map<String, ActiveTraceNode> nodes, String key) {
            if (hasText(key) && nodes.containsKey(key)) {
                return nodes.remove(key);
            }
            if (nodes.size() == 1) {
                String onlyKey = nodes.keySet().iterator().next();
                return nodes.remove(onlyKey);
            }
            return null;
        }

        private void finishAll(Map<String, ActiveTraceNode> nodes, Throwable error) {
            List<ActiveTraceNode> activeNodes = new ArrayList<>(nodes.values());
            nodes.clear();
            activeNodes.forEach(node -> finishActive(node, error, null));
        }

        private void finishActive(ActiveTraceNode node, Throwable error, String extraData) {
            if (node == null || !node.scope().active()) {
                return;
            }
            finishTraceNode(node.scope(), error, extraData);
        }

        private Map<String, Object> toolFields(
                String eventType,
                String replyId,
                String toolCallId,
                String toolCallName) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("replyId", replyId);
            fields.put("toolCallId", toolCallId);
            fields.put("toolCallName", toolCallName);
            fields.put("eventName", eventType);
            return fields;
        }

        private Map<String, Object> usageData(ChatUsage usage) {
            if (usage == null) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("inputTokens", usage.getInputTokens());
            data.put("outputTokens", usage.getOutputTokens());
            data.put("totalTokens", usage.getTotalTokens());
            return data;
        }
    }

    private record ActiveTraceNode(TraceNodeScope scope, int depth) {
        private static ActiveTraceNode disabled() {
            return new ActiveTraceNode(TraceNodeScope.disabled(), 0);
        }
    }
}
