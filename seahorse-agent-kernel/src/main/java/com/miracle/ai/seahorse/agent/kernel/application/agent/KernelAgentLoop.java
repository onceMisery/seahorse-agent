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
import com.miracle.ai.seahorse.agent.kernel.application.agent.output.OutputGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitCommand;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.LoadSkillResourceToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopExitReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputGovernanceResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillToolPolicyMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamAgentStepEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamApprovalRequiredEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamSkillEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamToolCallEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kernel layer LLM-driven ReAct loop.
 */
public class KernelAgentLoop {

    private static final String TRUNCATED_MESSAGE =
            "Task step limit reached. Narrow the question or check tool configuration.";
    private static final int MAX_TOOL_OBSERVATION_CHARS = 8 * 1024;
    private static final String TOOL_OBSERVATION_TRUNCATED_SUFFIX = "...[truncated]";
    private static final String WAITING_APPROVAL_MESSAGE = "Waiting for tool approval.";
    private static final String RAW_ARGUMENTS_KEY = "_raw";
    private static final String IDEMPOTENCY_KEY_SEPARATOR = ":";
    private static final String TRACE_TYPE_AGENT_STEP = "AGENT_STEP";
    private static final String TRACE_TYPE_AGENT_TOOL = "AGENT_TOOL";
    private static final String MODEL_STEP_ID_PREFIX = "model-turn-";
    private static final String MODEL_STEP_TITLE = "Model turn";
    private static final String TOOL_CALL_STARTED_SUMMARY = "Tool call started";
    private static final String TOOL_CALL_FINISHED_SUMMARY = "Tool call finished";
    private static final String TOOL_CALL_FAILED_SUMMARY = "Tool call failed";
    private static final String TOOL_CALL_APPROVAL_SUMMARY = "Tool call requires approval";
    private static final String TOOL_CALL_SUCCEEDED_STATUS = "SUCCEEDED";
    private static final String TOOL_CALL_FAILED_STATUS = "FAILED";
    private static final String SKILL_STATUS_SELECTED = "SELECTED";
    private static final String SKILL_STATUS_LOADED = "LOADED";
    private static final String SKILL_STATUS_METADATA_ONLY = "METADATA_ONLY";
    private static final String SKILL_STATUS_SKIPPED = "SKIPPED";
    private static final String TOOL_ARGUMENT_KEYS_FIELD = "argumentKeys";
    private static final String TOOL_ARGUMENT_COUNT_FIELD = "argumentCount";
    private static final Pattern TEXT_TOOL_CALL_BLOCK_PATTERN =
            Pattern.compile("(?is)<tool_call\\b[^>]*>(.*?)</tool_call>");
    private static final Pattern TEXT_TOOL_CALL_FUNCTION_PATTERN =
            Pattern.compile("(?is)<function\\s*=\\s*([A-Za-z0-9_-]+)\\s*>(.*?)</function>");
    private static final Pattern TEXT_TOOL_CALL_PARAMETER_PATTERN =
            Pattern.compile("(?is)<parameter\\s*=\\s*([A-Za-z0-9_.-]+)\\s*>(.*?)</parameter>");
    private static final AtomicLong TEXT_TOOL_CALL_SEQUENCE = new AtomicLong();
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
    private static final String LEGACY_LOAD_SKILL_TOOL_ID = "load_skill";
    private static final ToolRiskLevel DEFAULT_TOOL_RISK_LEVEL = ToolRiskLevel.HIGH;
    private static final String TRACE_CLASS_NAME =
            "com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamingChatModelPort modelPort;
    private final ToolRegistryPort toolRegistry;
    private final ToolGatewayPort toolGateway;
    private final KernelAgentLoopOptions options;
    private final KernelRagTraceRecorder traceRecorder;
    private final ContextWeaverPort contextWeaver;
    private final AgentRunStepRecorder runStepRecorder;
    private final AgentApprovalWaitHandler approvalWaitHandler;
    // Phase D Slice 1a：可选输出治理。null 表示运行时未启用输出治理，保持引入前行为。
    private final OutputGovernanceService outputGovernance;

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options) {
        this(modelPort, toolRegistry, options, KernelRagTraceRecorder.noop());
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           ToolGatewayPort toolGateway,
                           KernelAgentLoopOptions options) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        ToolRegistryPort effectiveToolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolRegistry = effectiveToolRegistry;
        this.toolGateway = Objects.requireNonNullElseGet(toolGateway,
                () -> new LocalToolGatewayPort(effectiveToolRegistry));
        this.options = Objects.requireNonNullElseGet(options, KernelAgentLoopOptions::defaults);
        this.traceRecorder = KernelRagTraceRecorder.noop();
        this.contextWeaver = new DefaultContextWeaver();
        this.runStepRecorder = AgentRunStepRecorder.noop();
        this.approvalWaitHandler = AgentApprovalWaitHandler.noop();
        this.outputGovernance = null;
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           ToolGatewayPort toolGateway,
                           KernelAgentLoopOptions options,
                           AgentApprovalWaitHandler approvalWaitHandler) {
        this(modelPort,
                toolRegistry,
                toolGateway,
                options,
                KernelRagTraceRecorder.noop(),
                new DefaultContextWeaver(),
                AgentRunStepRecorder.noop(),
                approvalWaitHandler);
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder) {
        this(modelPort, toolRegistry, options, traceRecorder, new DefaultContextWeaver());
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options,
                           AgentRunStepRecorder runStepRecorder) {
        this(modelPort, toolRegistry, options, KernelRagTraceRecorder.noop(),
                new DefaultContextWeaver(), runStepRecorder);
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options,
                           ContextWeaverPort contextWeaver) {
        this(modelPort, toolRegistry, options, KernelRagTraceRecorder.noop(), contextWeaver);
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder,
                           ContextWeaverPort contextWeaver) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        this.toolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolGateway = new LocalToolGatewayPort(this.toolRegistry);
        this.options = Objects.requireNonNullElseGet(options, KernelAgentLoopOptions::defaults);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.contextWeaver = Objects.requireNonNullElseGet(contextWeaver, DefaultContextWeaver::new);
        this.runStepRecorder = AgentRunStepRecorder.noop();
        this.approvalWaitHandler = AgentApprovalWaitHandler.noop();
        this.outputGovernance = null;
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder,
                           ContextWeaverPort contextWeaver,
                           AgentRunStepRecorder runStepRecorder) {
        this(modelPort, toolRegistry, null, options, traceRecorder, contextWeaver, runStepRecorder,
                AgentApprovalWaitHandler.noop());
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           ToolGatewayPort toolGateway,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder,
                           ContextWeaverPort contextWeaver,
                           AgentRunStepRecorder runStepRecorder) {
        this(modelPort, toolRegistry, toolGateway, options, traceRecorder, contextWeaver, runStepRecorder,
                AgentApprovalWaitHandler.noop());
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           ToolGatewayPort toolGateway,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder,
                           ContextWeaverPort contextWeaver,
                           AgentRunStepRecorder runStepRecorder,
                           AgentApprovalWaitHandler approvalWaitHandler) {
        this(modelPort, toolRegistry, toolGateway, options, traceRecorder, contextWeaver,
                runStepRecorder, approvalWaitHandler, null);
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           ToolGatewayPort toolGateway,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder,
                           ContextWeaverPort contextWeaver,
                           AgentRunStepRecorder runStepRecorder,
                           AgentApprovalWaitHandler approvalWaitHandler,
                           OutputGovernanceService outputGovernance) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        ToolRegistryPort effectiveToolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolRegistry = effectiveToolRegistry;
        this.toolGateway = Objects.requireNonNullElseGet(toolGateway,
                () -> new LocalToolGatewayPort(effectiveToolRegistry));
        this.options = Objects.requireNonNullElseGet(options, KernelAgentLoopOptions::defaults);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.contextWeaver = Objects.requireNonNullElseGet(contextWeaver, DefaultContextWeaver::new);
        this.runStepRecorder = Objects.requireNonNullElseGet(runStepRecorder, AgentRunStepRecorder::noop);
        this.approvalWaitHandler = Objects.requireNonNullElseGet(approvalWaitHandler, AgentApprovalWaitHandler::noop);
        this.outputGovernance = outputGovernance;
    }

    public AgentLoopResult execute(AgentLoopRequest request) {
        return run(request, null, AgentRunControl.direct(), TraceRunScope.disabled());
    }

    public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
        return streamExecute(request, callback, TraceRunScope.disabled());
    }

    public StreamCancellationHandle streamExecute(AgentLoopRequest request,
                                                  StreamCallback callback,
                                                  TraceRunScope traceRunScope) {
        AgentRunControl control = new AgentRunControl();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "seahorse-agent-loop");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> future = executor.submit(() -> {
            try {
                run(request, callback, control, traceRunScope);
            } catch (Exception ex) {
                if (callback != null) {
                    callback.onError(ex);
                } else {
                    throw ex instanceof RuntimeException runtimeException
                            ? runtimeException
                            : new AgentLoopException("Agent loop failed", ex);
                }
            } finally {
                executor.shutdownNow();
            }
        });
        control.bindWorkerFuture(future);
        return control::cancel;
    }

    private AgentLoopResult run(AgentLoopRequest request,
                                StreamCallback callback,
                                AgentRunControl control,
                                TraceRunScope traceRunScope) {
        Objects.requireNonNull(request, "AgentLoopRequest must not be null");
        AgentRunControl runControl = Objects.requireNonNullElseGet(control, AgentRunControl::direct);
        List<ChatMessage> messages = new ArrayList<>(request.history());
        installRuntimeContext(messages, request.contextPack(), request.memoryContext(), request.skillRuntimeContext());
        emitSkillRuntimeEvents(callback, request);
        messages.add(ChatMessage.user(request.question()));

        List<AgentStep> steps = new ArrayList<>();
        Set<String> exhaustedToolIds = new HashSet<>();
        boolean hasToolObservations = false;
        int maxSteps = Math.min(request.maxSteps(), options.maxSteps());
        for (int step = 0; step < maxSteps; step++) {
            runControl.checkCancelled();
            int stepNo = step + 1;
            String stepId = modelStepId(stepNo);
            Instant stepStartedAt = Instant.now();
            emitStepStarted(callback, request, stepId, stepNo, stepStartedAt);
            TraceNodeScope stepScope = traceRecorder.startNode(traceRunScope, agentStepCommand(stepNo));
            boolean modelTurnRecorded = false;
            try {
                ModelTurn turn = requestModelTurn(request, messages, runControl, exhaustedToolIds);
                recordModelTurn(request, messages, turn, exhaustedToolIds, null);
                modelTurnRecorded = true;
                if (turn.toolCalls().isEmpty()) {
                    String finalContent = normalizeFinalMarkdown(applyOutputGovernance(request, turn.content()));
                    emitContent(callback, finalContent);
                    emitFinalArtifact(callback, request, finalContent);
                    steps.add(AgentStep.finalAnswer(finalContent));
                    emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                            null, finalContent);
                    emitComplete(callback);
                    traceRecorder.finishNode(stepScope);
                    return new AgentLoopResult(finalContent, steps, false);
                }

                List<String> effectiveAllowedToolIds = effectiveAllowedToolIds(request);
                List<AgentObservation> observations = executeTools(
                        turn.toolCalls(),
                        effectiveAllowedToolIds,
                        exposedToolIds(request, exhaustedToolIds),
                        request,
                        runControl,
                        traceRunScope,
                        stepScope,
                        callback);
                markExhaustedTools(turn.toolCalls(), observations, exhaustedToolIds);
                hasToolObservations = hasToolObservations || !observations.isEmpty();
                if (requiresApproval(observations)) {
                    emitToolThinking(callback, turn, observations);
                    AgentStep pendingStep = AgentStep.thought(turn.thought(), turn.toolCalls(), observations);
                    steps.add(pendingStep);
                    AgentToolCall pendingToolCall = pendingToolCall(turn.toolCalls(), observations);
                    approvalWaitHandler.waitForApproval(new AgentApprovalWaitCommand(
                            toolInvocationRequest(pendingToolCall, allowedToolIdSet(effectiveAllowedToolIds), request),
                            waitingApprovalStateJson(turn, observations),
                            waitingApprovalMessages(messages, turn)));
                    emitContent(callback, WAITING_APPROVAL_MESSAGE);
                    emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                            null, WAITING_APPROVAL_MESSAGE);
                    emitComplete(callback);
                    traceRecorder.finishNode(stepScope);
                    return new AgentLoopResult(
                            WAITING_APPROVAL_MESSAGE,
                            steps,
                            false,
                            AgentLoopExitReason.WAITING_APPROVAL);
                }
                runControl.checkCancelled();
                emitToolThinking(callback, turn, observations);
                emitSourcesFromObservations(callback, turn.toolCalls(), observations);
                steps.add(AgentStep.thought(turn.thought(), turn.toolCalls(), observations));
                messages.add(ChatMessage.assistantToolCalls(turn.content(), turn.toolCalls()));
                for (AgentObservation observation : observations) {
                    messages.add(ChatMessage.tool(observation.toolCallId(), observationText(observation)));
                }
                emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                        null, null);
                traceRecorder.finishNode(stepScope);
            } catch (RuntimeException ex) {
                if (!modelTurnRecorded) {
                    recordModelTurn(request, messages, null, exhaustedToolIds, ex);
                }
                emitRecoverableError(callback, request, stepId, ex);
                emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.FAILED,
                        ex, null);
                traceRecorder.finishNode(stepScope, ex);
                throw ex;
            }
        }
        if (hasToolObservations) {
            AgentLoopResult finalResult = requestFinalAnswerAfterToolSteps(
                    request, messages, steps, runControl, callback, traceRunScope, maxSteps + 1);
            if (finalResult != null) {
                return finalResult;
            }
        }
        emitContent(callback, TRUNCATED_MESSAGE);
        emitComplete(callback);
        return new AgentLoopResult(TRUNCATED_MESSAGE, steps, true);
    }

    private AgentLoopResult requestFinalAnswerAfterToolSteps(AgentLoopRequest request,
                                                             List<ChatMessage> messages,
                                                             List<AgentStep> steps,
                                                             AgentRunControl runControl,
                                                             StreamCallback callback,
                                                             TraceRunScope traceRunScope,
                                                             int stepNo) {
        runControl.checkCancelled();
        String stepId = modelStepId(stepNo);
        Instant stepStartedAt = Instant.now();
        emitStepStarted(callback, request, stepId, stepNo, stepStartedAt);
        TraceNodeScope stepScope = traceRecorder.startNode(traceRunScope, agentStepCommand(stepNo));
        try {
            ModelTurn turn = requestFinalModelTurn(request, messages, runControl);
            recordModelTurn(request, messages, turn, Set.of(), null);
            if (!turn.toolCalls().isEmpty()) {
                emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.FAILED,
                        null, "Final answer turn attempted to call tools.");
                traceRecorder.finishNode(stepScope);
                return null;
            }
            String finalContent = normalizeFinalMarkdown(applyOutputGovernance(request, turn.content()));
            emitContent(callback, finalContent);
            emitFinalArtifact(callback, request, finalContent);
            steps.add(AgentStep.finalAnswer(finalContent));
            emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                    null, finalContent);
            emitComplete(callback);
            traceRecorder.finishNode(stepScope);
            return new AgentLoopResult(finalContent, steps, false);
        } catch (RuntimeException ex) {
            recordModelTurn(request, messages, null, Set.of(), ex);
            emitRecoverableError(callback, request, stepId, ex);
            emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.FAILED,
                    ex, null);
            traceRecorder.finishNode(stepScope, ex);
            throw ex;
        }
    }

    private boolean requiresApproval(List<AgentObservation> observations) {
        return observations.stream().anyMatch(this::isApprovalRequired);
    }

    private boolean isApprovalRequired(AgentObservation observation) {
        return observation != null
                && !observation.success()
                && ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED.equals(observation.error());
    }

    private AgentToolCall pendingToolCall(List<AgentToolCall> toolCalls, List<AgentObservation> observations) {
        for (int i = 0; i < observations.size(); i++) {
            if (isApprovalRequired(observations.get(i))) {
                return toolCalls.get(i);
            }
        }
        throw new AgentLoopException("Approval required observation missing matching tool call");
    }

    private Set<String> allowedToolIdSet(List<String> allowedToolIds) {
        return allowedToolIds == null || allowedToolIds.isEmpty()
                ? Set.of()
                : new HashSet<>(allowedToolIds);
    }

    private List<ChatMessage> waitingApprovalMessages(List<ChatMessage> messages, ModelTurn turn) {
        List<ChatMessage> snapshot = new ArrayList<>(messages);
        snapshot.add(ChatMessage.assistantToolCalls(turn.content(), turn.toolCalls()));
        return snapshot;
    }

    private String waitingApprovalStateJson(ModelTurn turn, List<AgentObservation> observations) {
        return "{\"exitReason\":\"" + AgentLoopExitReason.WAITING_APPROVAL.name()
                + "\",\"toolCallCount\":" + turn.toolCalls().size()
                + ",\"observationCount\":" + observations.size() + "}";
    }

    private ModelTurn requestModelTurn(AgentLoopRequest request,
                                       List<ChatMessage> messages,
                                       AgentRunControl control,
                                       Set<String> exhaustedToolIds) {
        return requestModelTurn(
                request,
                messages,
                control,
                exposedTools(effectiveAllowedToolIds(request), request.skillRuntimeBlocks(), exhaustedToolIds),
                "auto");
    }

    private ModelTurn requestFinalModelTurn(AgentLoopRequest request,
                                            List<ChatMessage> messages,
                                            AgentRunControl control) {
        return requestModelTurn(request, messages, control, List.of(), "none");
    }

    private ModelTurn requestModelTurn(AgentLoopRequest request,
                                       List<ChatMessage> messages,
                                       AgentRunControl control,
                                       List<ToolDescriptor> tools,
                                       String toolChoice) {
        TurnBuffer callback = new TurnBuffer();
        AtomicReference<List<AgentToolCall>> collectedCalls = new AtomicReference<>();
        AtomicBoolean collectorInvoked = new AtomicBoolean(false);

        StreamCancellationHandle handle = modelPort.streamChatWithTools(ChatRequest.builder()
                .messages(List.copyOf(messages))
                .modelId(request.modelId())
                .samplingOptions(request.samplingOptions())
                .tools(tools == null ? List.of() : tools)
                .toolChoice(toolChoice)
                .build(), callback, toolCalls -> {
                    if (callback.completed()) {
                        throw new AgentLoopException("Model adapter protocol error: collector called after onComplete");
                    }
                    if (!collectorInvoked.compareAndSet(false, true)) {
                        throw new AgentLoopException("Tool call collector was called more than once");
                    }
                    collectedCalls.set(toolCalls == null ? List.of() : List.copyOf(toolCalls));
                });
        control.bindModelHandle(handle);
        try {
            callback.awaitCompletion(control);
        } finally {
            control.clearModelHandle(handle);
        }

        if (callback.error() != null) {
            throw new AgentLoopException("Model streaming call failed", callback.error());
        }
        if (!collectorInvoked.get()) {
            throw new AgentLoopException("Model adapter protocol error: collector was not called");
        }
        ModelTurn turn = new ModelTurn(callback.content(), callback.thinking(),
                Objects.requireNonNullElse(collectedCalls.get(), List.of()));
        return normalizeTextEncodedToolCalls(turn, tools);
    }

    private ModelTurn normalizeTextEncodedToolCalls(ModelTurn turn, List<ToolDescriptor> tools) {
        if (turn == null || !turn.toolCalls().isEmpty() || tools == null || tools.isEmpty()
                || turn.content().isBlank()) {
            return turn;
        }
        Set<String> exposedToolIds = tools.stream()
                .filter(Objects::nonNull)
                .map(ToolDescriptor::toolId)
                .collect(java.util.stream.Collectors.toSet());
        TextEncodedToolCalls parsed = parseTextEncodedToolCalls(turn.content(), exposedToolIds);
        if (parsed.toolCalls().isEmpty()) {
            return turn;
        }
        return new ModelTurn(parsed.content(), turn.thinking(), parsed.toolCalls());
    }

    private TextEncodedToolCalls parseTextEncodedToolCalls(String content, Set<String> exposedToolIds) {
        if (content == null || content.isBlank() || exposedToolIds == null || exposedToolIds.isEmpty()) {
            return new TextEncodedToolCalls(Objects.requireNonNullElse(content, ""), List.of());
        }
        Matcher blockMatcher = TEXT_TOOL_CALL_BLOCK_PATTERN.matcher(content);
        List<AgentToolCall> toolCalls = new ArrayList<>();
        while (blockMatcher.find()) {
            AgentToolCall toolCall = parseTextEncodedToolCall(blockMatcher.group(1), exposedToolIds);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        if (toolCalls.isEmpty()) {
            return new TextEncodedToolCalls(content, List.of());
        }
        String strippedContent = TEXT_TOOL_CALL_BLOCK_PATTERN.matcher(content).replaceAll("").trim();
        return new TextEncodedToolCalls(strippedContent, toolCalls);
    }

    private AgentToolCall parseTextEncodedToolCall(String block, Set<String> exposedToolIds) {
        if (block == null || block.isBlank()) {
            return null;
        }
        Matcher functionMatcher = TEXT_TOOL_CALL_FUNCTION_PATTERN.matcher(block);
        if (!functionMatcher.find()) {
            return null;
        }
        String toolId = functionMatcher.group(1).trim();
        if (!exposedToolIds.contains(toolId)) {
            return null;
        }
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        Matcher parameterMatcher = TEXT_TOOL_CALL_PARAMETER_PATTERN.matcher(functionMatcher.group(2));
        while (parameterMatcher.find()) {
            String key = parameterMatcher.group(1).trim();
            if (!key.isBlank()) {
                arguments.put(key, decodeTextToolCallValue(parameterMatcher.group(2).trim()));
            }
        }
        return AgentToolCall.of(nextTextToolCallId(), toolId, arguments);
    }

    private String nextTextToolCallId() {
        return "text-tool-call-" + TEXT_TOOL_CALL_SEQUENCE.incrementAndGet();
    }

    private String decodeTextToolCallValue(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private List<ToolDescriptor> exposedTools(List<String> allowedToolIds,
                                              List<SkillRuntimeBlock> skillRuntimeBlocks,
                                              Set<String> exhaustedToolIds) {
        List<ToolDescriptor> result = new ArrayList<>();
        List<ToolDescriptor> all = toolRegistry.listTools();
        List<String> safeAllowedToolIds = allowedToolIds == null ? List.of() : allowedToolIds;
        Set<String> allowed = new HashSet<>(safeAllowedToolIds);
        Set<String> exhausted = exhaustedToolIds == null ? Set.of() : exhaustedToolIds;
        Map<String, ToolDescriptor> descriptorsById = all.stream()
                .filter(tool -> allowed.contains(tool.toolId()))
                .filter(tool -> !exhausted.contains(tool.toolId()))
                .collect(java.util.stream.Collectors.toMap(
                        ToolDescriptor::toolId,
                        tool -> tool,
                        (left, right) -> left,
                        LinkedHashMap::new));
        result.addAll(safeAllowedToolIds.stream()
                .map(descriptorsById::get)
                .filter(Objects::nonNull)
                .toList());
        if (hasLoadableSkills(skillRuntimeBlocks)) {
            toolRegistry.find(LoadSkillResourceToolPortAdapter.TOOL_ID)
                    .flatMap(ignored -> toolRegistry.listTools().stream()
                            .filter(tool -> LoadSkillResourceToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                            .findFirst())
                    .ifPresent(result::add);
        }
        if (!safeAllowedToolIds.isEmpty()) {
            toolRegistry.find(ToolSearchToolPortAdapter.TOOL_ID)
                    .flatMap(ignored -> toolRegistry.listTools().stream()
                            .filter(tool -> ToolSearchToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                            .findFirst())
                    .ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private void markExhaustedTools(List<AgentToolCall> toolCalls,
                                    List<AgentObservation> observations,
                                    Set<String> exhaustedToolIds) {
        if (toolCalls == null || observations == null || exhaustedToolIds == null) {
            return;
        }
        int count = Math.min(toolCalls.size(), observations.size());
        for (int i = 0; i < count; i++) {
            AgentObservation observation = observations.get(i);
            if (observation != null
                    && !observation.success()
                    && ToolPolicyReasonCodes.TOOL_CALL_LIMIT_EXCEEDED.equals(observation.error())) {
                exhaustedToolIds.add(toolCalls.get(i).toolId());
            }
        }
    }

    private List<String> effectiveAllowedToolIds(AgentLoopRequest request) {
        if (request == null) {
            return List.of();
        }
        List<String> agentAllowedToolIds = request.allowedToolIds();
        if (request.skillToolPolicyMode() != SkillToolPolicyMode.RESTRICTIVE) {
            return agentAllowedToolIds;
        }
        List<SkillRuntimeBlock> skillRuntimeBlocks = request.skillRuntimeBlocks();
        if (skillRuntimeBlocks.isEmpty()) {
            return agentAllowedToolIds;
        }
        Set<String> skillAllowedToolIds = selectedSkillAllowedToolIds(skillRuntimeBlocks);
        return agentAllowedToolIds.stream()
                .filter(skillAllowedToolIds::contains)
                .toList();
    }

    private Set<String> selectedSkillAllowedToolIds(List<SkillRuntimeBlock> skillRuntimeBlocks) {
        if (skillRuntimeBlocks == null || skillRuntimeBlocks.isEmpty()) {
            return Set.of();
        }
        Set<String> allowedToolIds = new HashSet<>();
        for (SkillRuntimeBlock skill : skillRuntimeBlocks) {
            if (skill != null) {
                allowedToolIds.addAll(skill.allowedTools());
            }
        }
        return allowedToolIds;
    }

    private void installRuntimeContext(List<ChatMessage> messages,
                                       ContextPack contextPack,
                                       MemoryContext memoryContext,
                                       String skillRuntimeContext) {
        String contextText = contextWeaver.weave(contextPack, memoryContext, ContextBudget.defaults());
        if (skillRuntimeContext != null && !skillRuntimeContext.isBlank()) {
            contextText = contextText.isBlank()
                    ? skillRuntimeContext.trim()
                    : contextText + System.lineSeparator() + System.lineSeparator() + skillRuntimeContext.trim();
        }
        if (contextText.isBlank()) {
            return;
        }
        if (!messages.isEmpty() && messages.get(0).getRole() == ChatRole.SYSTEM) {
            ChatMessage first = messages.get(0);
            messages.set(0, ChatMessage.system(appendContextText(first.getContent(), contextText)));
            return;
        }
        messages.add(0, ChatMessage.system(contextText));
    }

    private String appendContextText(String systemPrompt, String contextText) {
        String safeSystemPrompt = Objects.requireNonNullElse(systemPrompt, "").trim();
        if (safeSystemPrompt.isBlank()) {
            return contextText;
        }
        return safeSystemPrompt + "\n\n" + contextText;
    }

    private List<AgentObservation> executeTools(List<AgentToolCall> toolCalls,
                                                List<String> allowedToolIds,
                                                Set<String> exposedToolIds,
                                                AgentLoopRequest request,
                                                AgentRunControl control,
                                                TraceRunScope traceRunScope,
                                                TraceNodeScope stepScope,
                                                StreamCallback callback) {
        Set<String> allowed = allowedToolIds == null || allowedToolIds.isEmpty()
                ? Set.of()
                : new HashSet<>(allowedToolIds);
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        int parallelism = Math.min(options.maxParallelTools(), toolCalls.size());
        List<AgentObservation> observations = new ArrayList<>(toolCalls.size());
        for (int start = 0; start < toolCalls.size(); start += parallelism) {
            int end = Math.min(start + parallelism, toolCalls.size());
            observations.addAll(executeToolBatch(
                    toolCalls.subList(start, end),
                    allowed,
                    exposedToolIds,
                    request,
                    parallelism,
                    control,
                    traceRunScope,
                    stepScope,
                    callback));
            if (Thread.currentThread().isInterrupted() && observations.size() < toolCalls.size()) {
                for (int i = observations.size(); i < toolCalls.size(); i++) {
                    observations.add(AgentObservation.failed(toolCalls.get(i).id(), "Tool execution was interrupted"));
                }
                break;
            }
        }
        return observations;
    }

    private List<AgentObservation> executeToolBatch(List<AgentToolCall> toolCalls,
                                                    Set<String> allowedToolIds,
                                                    Set<String> exposedToolIds,
                                                    AgentLoopRequest request,
                                                    int parallelism,
                                                    AgentRunControl control,
                                                    TraceRunScope traceRunScope,
                                                    TraceNodeScope stepScope,
                                                    StreamCallback callback) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, toolCalls.size()));
        control.bindToolExecutor(executor);
        try {
            for (AgentToolCall toolCall : toolCalls) {
                emitToolCallStarted(callback, request, toolCall);
            }
            List<Callable<AgentObservation>> tasks = toolCalls.stream()
                    .<Callable<AgentObservation>>map(toolCall ->
                            () -> executeToolTraced(
                                    toolCall,
                                    allowedToolIds,
                                    exposedToolIds,
                                    request,
                                    traceRunScope,
                                    stepScope))
                    .toList();
            List<Future<AgentObservation>> futures = executor.invokeAll(
                    tasks, perToolTimeoutNanos(), TimeUnit.NANOSECONDS);
            List<AgentObservation> observations = new ArrayList<>(toolCalls.size());
            for (int i = 0; i < futures.size(); i++) {
                AgentObservation observation = toObservation(toolCalls.get(i), futures.get(i));
                observations.add(observation);
                runStepRecorder.recordToolCall(request.runId(), toolCalls.get(i), observation);
                if (!isApprovalRequired(observation)) {
                    emitToolCallFinished(callback, request, toolCalls.get(i), observation);
                }
                emitToolCallWaitingUser(callback, request, toolCalls.get(i), observation);
                emitSkillResourceLoaded(callback, request, toolCalls.get(i), observation);
            }
            return observations;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (control.cancelled()) {
                throw new AgentLoopCancelledException("Agent loop cancelled", ex);
            }
            return toolCalls.stream()
                    .map(toolCall -> AgentObservation.failed(toolCall.id(), "Tool execution was interrupted"))
                    .toList();
        } finally {
            control.clearToolExecutor(executor);
            executor.shutdownNow();
        }
    }

    private long perToolTimeoutNanos() {
        try {
            return Math.max(1L, options.perToolTimeout().toNanos());
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private AgentObservation toObservation(AgentToolCall toolCall, Future<AgentObservation> future) {
        if (future.isCancelled()) {
            return AgentObservation.failed(toolCall.id(), "Tool execution timed out");
        }
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return AgentObservation.failed(toolCall.id(), "Tool execution was interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = Objects.requireNonNullElse(ex.getCause(), ex);
            return AgentObservation.failed(toolCall.id(),
                    Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getName()));
        }
    }

    private AgentObservation executeTool(AgentToolCall toolCall,
                                         Set<String> allowedToolIds,
                                         Set<String> exposedToolIds,
                                         AgentLoopRequest request) {
        if (hasRawArguments(toolCall)) {
            return AgentObservation.failed(toolCall.id(), "arguments is not valid JSON");
        }
        if (LEGACY_LOAD_SKILL_TOOL_ID.equals(toolCall.toolId())) {
            return loadSkillObservation(toolCall, request);
        }
        if (exposedToolIds != null && allowedToolIds != null && allowedToolIds.contains(toolCall.toolId())
                && !exposedToolIds.contains(toolCall.toolId())) {
            return AgentObservation.failed(toolCall.id(), unavailableToolMessage(toolCall.toolId(), exposedToolIds));
        }
        try {
            ToolInvocationResult result = toolGateway.invoke(toolInvocationRequest(toolCall, allowedToolIds, request));
            return result.success()
                    ? AgentObservation.ok(toolCall.id(), truncateObservationText(result.content()))
                    : AgentObservation.failed(
                            toolCall.id(),
                            truncateObservationText(result.error()),
                            result.approvalId());
        } catch (Exception ex) {
            return AgentObservation.failed(toolCall.id(),
                    truncateObservationText(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
        }
    }

    private boolean hasLoadableSkills(List<SkillRuntimeBlock> skills) {
        return skills != null && skills.stream().anyMatch(skill -> skill != null && !skill.content().isBlank());
    }

    private AgentObservation loadSkillObservation(AgentToolCall toolCall, AgentLoopRequest request) {
        String requestedName = loadSkillName(toolCall);
        if (requestedName.isBlank()) {
            return AgentObservation.failed(toolCall.id(), "skill name is required");
        }
        String resourcePath = loadSkillResourcePath(toolCall);
        if (!"SKILL.md".equals(resourcePath)) {
            return AgentObservation.failed(toolCall.id(), "skill resource is not available");
        }
        List<SkillRuntimeBlock> skills = request == null ? List.of() : request.skillRuntimeBlocks();
        return skills.stream()
                .filter(skill -> skill.name().equals(requestedName))
                .findFirst()
                .map(skill -> AgentObservation.ok(toolCall.id(), loadSkillPayload(skill)))
                .orElseGet(() -> AgentObservation.failed(toolCall.id(), "skill is not selected in this Agent version"));
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

    private String loadSkillPayload(SkillRuntimeBlock skill) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", skill.name());
        payload.put("revisionId", skill.revisionId());
        payload.put("contentHash", skill.contentHash());
        payload.put("description", skill.description());
        payload.put("category", skill.category().name());
        payload.put("injectMode", SkillInjectMode.METADATA_AND_BODY.name());
        payload.put("content", skill.content());
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"name\":\"" + escapeJson(skill.name()) + "\",\"content\":\""
                    + escapeJson(skill.content()) + "\"}";
        }
    }

    private ToolInvocationRequest toolInvocationRequest(AgentToolCall toolCall,
                                                        Set<String> allowedToolIds,
                                                        AgentLoopRequest request) {
        String runId = request == null ? null : request.runId();
        return new ToolInvocationRequest(
                runId,
                toolCall.id(),
                toolCall.id(),
                request == null ? null : request.agentId(),
                request == null ? null : request.versionId(),
                request == null ? null : request.rolloutId(),
                request == null ? null : request.tenantId(),
                request == null ? null : request.userId(),
                request == null ? null : request.agentIdentityId(),
                toolCall.toolId(),
                toolArguments(toolCall, request),
                java.util.Map.of(),
                idempotencyKey(runId, toolCall.id()),
                effectiveAllowedToolIds(toolCall, allowedToolIds));
    }

    private List<String> effectiveAllowedToolIds(AgentToolCall toolCall, Set<String> allowedToolIds) {
        LinkedHashMap<String, Boolean> effective = new LinkedHashMap<>();
        if (allowedToolIds != null) {
            allowedToolIds.forEach(toolId -> effective.put(toolId, true));
        }
        if (LoadSkillResourceToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            effective.put(LoadSkillResourceToolPortAdapter.TOOL_ID, true);
        }
        if (ToolSearchToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            effective.put(ToolSearchToolPortAdapter.TOOL_ID, true);
        }
        return List.copyOf(effective.keySet());
    }

    private String idempotencyKey(String runId, String toolCallId) {
        if (runId == null || runId.isBlank()) {
            return toolCallId;
        }
        return runId + IDEMPOTENCY_KEY_SEPARATOR + toolCallId;
    }

    private AgentObservation executeToolTraced(AgentToolCall toolCall,
                                               Set<String> allowedToolIds,
                                               Set<String> exposedToolIds,
                                               AgentLoopRequest request,
                                               TraceRunScope traceRunScope,
                                               TraceNodeScope stepScope) {
        TraceNodeScope toolScope = traceRecorder.startNode(traceRunScope, agentToolCommand(toolCall, stepScope));
        try {
            AgentObservation observation = executeTool(toolCall, allowedToolIds, exposedToolIds, request);
            if (observation.success()) {
                traceRecorder.finishNode(toolScope);
            } else {
                traceRecorder.finishNode(toolScope, new AgentLoopException(observation.error()));
            }
            return observation;
        } catch (RuntimeException ex) {
            traceRecorder.finishNode(toolScope, ex);
            throw ex;
        }
    }

    private boolean hasRawArguments(AgentToolCall toolCall) {
        return toolCall.arguments().size() == 1 && toolCall.arguments().containsKey(RAW_ARGUMENTS_KEY);
    }

    private java.util.Map<String, Object> toolArguments(AgentToolCall toolCall, AgentLoopRequest request) {
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>(
                Objects.requireNonNullElse(toolCall.arguments(), java.util.Map.of()));
        if (LoadSkillResourceToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            arguments.put(LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT,
                    request == null ? List.of() : request.skillRuntimeBlocks());
        }
        if (ToolSearchToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            arguments.put(ToolSearchToolPortAdapter.ALLOWED_TOOL_IDS_ARGUMENT,
                    request == null ? List.of() : effectiveAllowedToolIds(request));
        }
        if (request == null || request.memoryContext() == null) {
            return arguments;
        }
        arguments.put("_seahorseUserId", Objects.requireNonNullElse(request.memoryContext().getUserId(), ""));
        arguments.put("_seahorseConversationId", Objects.requireNonNullElse(request.memoryContext().getConversationId(), ""));
        arguments.put("_seahorseQuestion", Objects.requireNonNullElse(request.memoryContext().getCurrentQuestion(), ""));
        return arguments;
    }

    private String truncateObservationText(String text) {
        if (text == null || text.length() <= MAX_TOOL_OBSERVATION_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TOOL_OBSERVATION_CHARS) + TOOL_OBSERVATION_TRUNCATED_SUFFIX;
    }

    private Set<String> exposedToolIds(AgentLoopRequest request, Set<String> exhaustedToolIds) {
        return exposedTools(effectiveAllowedToolIds(request), request.skillRuntimeBlocks(), exhaustedToolIds).stream()
                .map(ToolDescriptor::toolId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private String unavailableToolMessage(String toolId, Set<String> exposedToolIds) {
        String available = exposedToolIds == null || exposedToolIds.isEmpty()
                ? "none"
                : String.join(", ", exposedToolIds);
        return "Tool " + toolId
                + " is no longer available in this run. Do not call it again. Continue with available tools: "
                + available + ".";
    }

    private String observationText(AgentObservation observation) {
        return observation.success() ? observation.content() : observation.error();
    }

    private String modelStepId(int stepNo) {
        return MODEL_STEP_ID_PREFIX + stepNo;
    }

    private void emitStepStarted(StreamCallback callback,
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

    private void emitStepFinished(StreamCallback callback,
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

    private void emitRecoverableError(StreamCallback callback,
                                      AgentLoopRequest request,
                                      String stepId,
                                      Throwable error) {
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

    private void emitToolCallStarted(StreamCallback callback, AgentLoopRequest request, AgentToolCall toolCall) {
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

    private void emitToolCallWaitingUser(StreamCallback callback,
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

    private void emitToolCallFinished(StreamCallback callback,
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

    private void emitSkillRuntimeEvents(StreamCallback callback, AgentLoopRequest request) {
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

    private void emitSkillResourceLoaded(StreamCallback callback,
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

    private StreamSkillEvent skillEvent(AgentLoopRequest request,
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
        if (callback != null && eventType != null) {
            callback.onEvent(eventType.value(), payload);
        }
    }

    private void recordModelTurn(AgentLoopRequest request,
                                 List<ChatMessage> messages,
                                 ModelTurn turn,
                                 Set<String> exhaustedToolIds,
                                 Throwable error) {
        runStepRecorder.recordModelTurn(
                request.runId(),
                AgentRunStepRecorder.modelTurnInput(messages,
                        exposedTools(effectiveAllowedToolIds(request), request.skillRuntimeBlocks(), exhaustedToolIds)),
                turn == null ? null : modelTurnOutputJson(turn),
                error);
    }

    private String modelTurnOutputJson(ModelTurn turn) {
        return "{\"content\":\"" + escapeJson(turn.content())
                + "\",\"thinking\":\"" + escapeJson(turn.thinking())
                + "\",\"toolCallCount\":" + turn.toolCalls().size() + "}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private void emitToolThinking(StreamCallback callback, ModelTurn turn, List<AgentObservation> observations) {
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

    private void emitSourcesFromObservations(StreamCallback callback,
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

    private void emitContent(StreamCallback callback, String content) {
        if (callback != null && content != null && !content.isEmpty()) {
            callback.onContent(content);
        }
    }

    private void emitFinalArtifact(StreamCallback callback, AgentLoopRequest request, String content) {
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

    private String applyOutputGovernance(AgentLoopRequest request, String originalContent) {
        if (outputGovernance == null) {
            return originalContent;
        }
        OutputArtifactType artifactType = Objects.requireNonNullElse(
                request.expectedOutputArtifactType(), OutputArtifactType.PLAIN_TEXT);
        OutputValidationRequest validationRequest = new OutputValidationRequest(
                request.runId(),
                request.agentId(),
                request.tenantId(),
                request.userId(),
                artifactType,
                request.expectedOutputSchemaJson(),
                originalContent,
                Map.of());
        OutputGovernanceResult result = outputGovernance.governFinalAnswer(validationRequest);
        return result.governedContent();
    }

    private String normalizeFinalMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String normalizedLineEndings = content.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(normalizedLineEndings.length() + 64);
        int offset = 0;
        while (offset < normalizedLineEndings.length()) {
            int artifactStart = indexOfArtifactStart(normalizedLineEndings, offset);
            if (artifactStart < 0) {
                result.append(normalizeMarkdownWithoutArtifacts(normalizedLineEndings.substring(offset)));
                break;
            }

            result.append(normalizeMarkdownWithoutArtifacts(normalizedLineEndings.substring(offset, artifactStart)));
            int artifactEnd = indexOfArtifactEnd(normalizedLineEndings, artifactStart);
            if (artifactEnd < 0) {
                result.append(normalizedLineEndings.substring(artifactStart));
                break;
            }
            result.append(normalizedLineEndings, artifactStart, artifactEnd);
            offset = artifactEnd;
        }
        return result.toString().trim();
    }

    private int indexOfArtifactStart(String content, int fromIndex) {
        return content.toLowerCase(java.util.Locale.ROOT).indexOf("<artifact", fromIndex);
    }

    private int indexOfArtifactEnd(String content, int artifactStart) {
        int closeTag = content.toLowerCase(java.util.Locale.ROOT).indexOf("</artifact>", artifactStart);
        if (closeTag < 0) {
            return -1;
        }
        return closeTag + "</artifact>".length();
    }

    private String normalizeMarkdownWithoutArtifacts(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = normalizeMermaidFenceOpenings(content);
        StringBuilder builder = new StringBuilder(normalized.length() + 64);
        int offset = 0;
        int openingFence = normalized.indexOf("```", offset);
        while (openingFence >= 0) {
            appendNormalizedMarkdownText(builder, normalized.substring(offset, openingFence));

            int closingFence = findClosingFence(normalized, openingFence + 3);
            if (closingFence < 0) {
                appendNormalizedCodeBlock(builder, normalized.substring(openingFence));
                offset = normalized.length();
                break;
            }

            appendNormalizedCodeBlock(builder, normalized.substring(openingFence, closingFence + 3));
            offset = closingFence + 3;
            openingFence = normalized.indexOf("```", offset);
        }
        appendNormalizedMarkdownText(builder, normalized.substring(offset));
        return builder.toString().trim();
    }

    private String normalizeMermaidFenceOpenings(String content) {
        String normalized = content;
        normalized = normalized.replaceAll("```\\s*```\\s*(?i:mermaid)\\b", "```\n\n```mermaid");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:flowchart)\\b", "```mermaid\nflowchart");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:graph)\\b", "```mermaid\ngraph");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:sequenceDiagram)\\b", "```mermaid\nsequenceDiagram");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:classDiagram)\\b", "```mermaid\nclassDiagram");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:stateDiagram-v2)\\b", "```mermaid\nstateDiagram-v2");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:erDiagram)\\b", "```mermaid\nerDiagram");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:gantt)\\b", "```mermaid\ngantt");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:journey)\\b", "```mermaid\njourney");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:pie)\\b", "```mermaid\npie");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:mindmap)\\b", "```mermaid\nmindmap");
        normalized = normalized.replaceAll("```mermaid\\s*(?i:timeline)\\b", "```mermaid\ntimeline");
        return normalized;
    }

    private void appendNormalizedMarkdownText(StringBuilder builder, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String normalized = normalizeMarkdownTextSegment(content).trim();
        if (normalized.isEmpty()) {
            return;
        }
        ensureBlankLineBefore(builder);
        builder.append(normalized);
    }

    private String normalizeMarkdownTextSegment(String content) {
        String normalized = content;
        normalized = normalized.replaceAll("([^\\n])---(?=#{1,6}\\s*\\S)", "$1\n\n---\n\n");
        normalized = normalized.replaceAll("^---(?=#{1,6}\\s*\\S)", "---\n\n");
        normalized = normalized.replaceAll("([^\\n])---(?=\\*)", "$1\n\n---\n\n");
        normalized = normalized.replaceAll("([^#\\n])(?=#{1,6}\\S)", "$1\n\n");
        normalized = normalized.replaceAll("(?m)^(#{1,6})(\\S)", "$1 $2");
        normalized = separateGeneratedReportHeadings(normalized);
        normalized = normalized.replaceAll("(?m)^(#{1,6}\\s+[^\\n|#]+)\\|", "$1\n\n|");
        normalized = normalized.replaceAll("(?m)^(#{1,6}\\s+[^\\n`#]+)```", "$1\n\n```");
        normalized = normalized.replaceAll("(?<!\\n)(\\d+\\.\\s+\\*\\*)", "\n$1");
        normalized = separateGeneratedReportListItems(normalized);
        normalized = splitCompressedListItemsInLines(normalized);
        normalized = normalized.replaceAll("(\\|[^\\n|]+\\|)(?=\\|)", "$1\n");
        normalized = normalized.replaceAll("(?<!\\n)(- \\*\\*)", "\n$1");
        normalized = normalized.replaceAll("(\\]\\([^\\n)]+\\))(?=\\*)", "$1\n\n");
        normalized = normalized.replaceAll("([^\\n])```", "$1\n```");
        normalized = normalized.replaceAll("```(?=---)", "```\n");
        normalized = normalized.replaceAll("```(?=#{1,6}\\s*\\S)", "```\n\n");
        normalized = normalized.replaceAll("(?m)([^\\n])\\n(#{1,6}\\s+)", "$1\n\n$2");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized;
    }

    private void appendNormalizedCodeBlock(StringBuilder builder, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ensureBlankLineBefore(builder);
        builder.append(normalizeCodeBlockSegment(content));
    }

    private String normalizeCodeBlockSegment(String content) {
        String normalized = ensureCodeBlockClosingFenceOnOwnLine(content);
        if (isMermaidCodeBlock(normalized)) {
            return normalizeMermaidCodeBlock(normalized);
        }
        return normalized;
    }

    private String ensureCodeBlockClosingFenceOnOwnLine(String content) {
        int closingFence = content.lastIndexOf("```");
        if (closingFence > 0 && content.charAt(closingFence - 1) != '\n') {
            return content.substring(0, closingFence) + "\n" + content.substring(closingFence);
        }
        return content;
    }

    private boolean isMermaidCodeBlock(String content) {
        if (content.length() < "```mermaid".length()
                || !content.regionMatches(true, 0, "```mermaid", 0, "```mermaid".length())) {
            return false;
        }
        if (content.length() == "```mermaid".length()) {
            return true;
        }
        char next = content.charAt("```mermaid".length());
        return Character.isWhitespace(next);
    }

    private String normalizeMermaidCodeBlock(String content) {
        int bodyStart = content.indexOf('\n');
        if (bodyStart < 0) {
            return content;
        }
        String openingFence = content.substring(0, bodyStart + 1);
        String bodyWithClosingFence = content.substring(bodyStart + 1);
        int closingFence = bodyWithClosingFence.lastIndexOf("```");
        String body = closingFence >= 0
                ? bodyWithClosingFence.substring(0, closingFence)
                : bodyWithClosingFence;
        String closing = closingFence >= 0
                ? bodyWithClosingFence.substring(closingFence)
                : "";

        String normalizedBody = normalizeMermaidBody(body).stripTrailing();
        if (normalizedBody.isEmpty()) {
            return openingFence + closing;
        }
        return openingFence + normalizedBody + "\n" + closing;
    }

    private String normalizeMermaidBody(String body) {
        String[] lines = body.split("\n", -1);
        String diagramType = "";
        List<String> normalizedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (startsWithIgnoreCase(trimmed, "flowchart ") || startsWithIgnoreCase(trimmed, "graph ")) {
                diagramType = "flowchart";
                normalizedLines.addAll(splitFlowchartMermaidLine(trimmed));
            } else if (startsWithIgnoreCase(trimmed, "sequenceDiagram")) {
                diagramType = "sequence";
                normalizedLines.addAll(splitSequenceMermaidLine(trimmed));
            } else if ("flowchart".equals(diagramType)) {
                normalizedLines.addAll(splitMermaidStatements(splitCompressedFlowchartStatements(trimmed)));
            } else if ("sequence".equals(diagramType)) {
                normalizedLines.addAll(splitMermaidStatements(splitCompressedSequenceStatements(trimmed)));
            } else {
                normalizedLines.add(trimmed);
            }
        }
        return String.join("\n", normalizedLines);
    }

    private List<String> splitFlowchartMermaidLine(String line) {
        String[] parts = line.split("\\s+", 3);
        if (parts.length <= 2) {
            return List.of(line);
        }
        List<String> lines = new ArrayList<>();
        lines.add(parts[0] + " " + parts[1]);
        lines.addAll(splitMermaidStatements(splitCompressedFlowchartStatements(parts[2])));
        return lines;
    }

    private List<String> splitSequenceMermaidLine(String line) {
        String keyword = "sequenceDiagram";
        if (line.length() == keyword.length()) {
            return List.of(line);
        }
        List<String> lines = new ArrayList<>();
        lines.add(keyword);
        lines.addAll(splitMermaidStatements(splitCompressedSequenceStatements(line.substring(keyword.length()).trim())));
        return lines;
    }

    private String splitCompressedFlowchartStatements(String line) {
        String normalized = line.replaceAll(
                "\\s+(?=(?:style|classDef|class|linkStyle|click|subgraph|direction|end)\\b)",
                "\n");
        normalized = normalized.replaceAll(
                "\\s+(?=[A-Za-z_][A-Za-z0-9_]*\\s*(?:\\[[^\\]]*]|\\([^)]*\\)|\\{[^}]*}|>[^\\n<]*]|\\(\\([^)]*\\)\\))?\\s*(?:<-->|<-.->|<==>|-->|---|==>|-.->|--[ox]|[ox]--|~~~))",
                "\n");
        return splitStandaloneFlowchartNodes(normalized);
    }

    private String splitCompressedSequenceStatements(String line) {
        String normalized = line.replaceAll(
                "\\s+(?=(?:participant|actor|create|activate|deactivate|destroy|loop|alt|else|opt|par|and|critical|option|break|rect|end|box)\\b|Note\\s+(?:over|left|right)\\b)",
                "\n");
        return normalized.replaceAll(
                "\\s+(?=[A-Za-z_][A-Za-z0-9_]*\\s*(?:-->>|->>|-->|->|--x|-x|--\\)|-\\))\\s*[A-Za-z_][A-Za-z0-9_]*)",
                "\n");
    }

    private List<String> splitMermaidStatements(String content) {
        String[] lines = content.split("\n", -1);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String splitStandaloneFlowchartNodes(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder builder = new StringBuilder(content.length() + 32);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(splitStandaloneFlowchartNodesInLine(lines[i]));
        }
        return builder.toString();
    }

    private String splitStandaloneFlowchartNodesInLine(String line) {
        if (line.isBlank() || line.startsWith("style ") || line.startsWith("classDef ")
                || line.startsWith("class ") || line.startsWith("linkStyle ") || line.startsWith("click ")) {
            return line;
        }
        StringBuilder builder = new StringBuilder(line.length() + 16);
        int groupingDepth = 0;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (isOpeningMermaidGrouping(current)) {
                groupingDepth++;
            } else if (isClosingMermaidGrouping(current) && groupingDepth > 0) {
                groupingDepth--;
            }
            if (groupingDepth == 0 && Character.isWhitespace(current)
                    && shouldBreakBeforeStandaloneFlowchartNode(line, i)) {
                builder.append('\n');
                while (i + 1 < line.length() && Character.isWhitespace(line.charAt(i + 1))) {
                    i++;
                }
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private boolean shouldBreakBeforeStandaloneFlowchartNode(String line, int whitespaceIndex) {
        int candidateIndex = whitespaceIndex + 1;
        while (candidateIndex < line.length() && Character.isWhitespace(line.charAt(candidateIndex))) {
            candidateIndex++;
        }
        if (!isFlowchartNodeDefinitionAt(line, candidateIndex)) {
            return false;
        }
        String before = line.substring(0, whitespaceIndex).trim();
        int operatorIndex = lastFlowchartEdgeOperatorIndex(before);
        if (operatorIndex < 0) {
            return true;
        }
        return hasFlowchartTargetAfterOperator(before.substring(operatorIndex));
    }

    private boolean isFlowchartNodeDefinitionAt(String line, int index) {
        if (index >= line.length() || !isMermaidIdentifierStart(line.charAt(index))) {
            return false;
        }
        int cursor = index + 1;
        while (cursor < line.length() && isMermaidIdentifierPart(line.charAt(cursor))) {
            cursor++;
        }
        return cursor < line.length() && isOpeningMermaidGrouping(line.charAt(cursor));
    }

    private int lastFlowchartEdgeOperatorIndex(String value) {
        int result = -1;
        for (String operator : flowchartEdgeOperators()) {
            result = Math.max(result, value.lastIndexOf(operator));
        }
        return result;
    }

    private boolean hasFlowchartTargetAfterOperator(String value) {
        int cursor = 0;
        while (cursor < value.length() && !Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        String afterOperator = value.substring(cursor).trim();
        if (afterOperator.startsWith("|")) {
            int endLabel = afterOperator.indexOf('|', 1);
            if (endLabel >= 0) {
                afterOperator = afterOperator.substring(endLabel + 1).trim();
            }
        }
        return !afterOperator.isEmpty();
    }

    private List<String> flowchartEdgeOperators() {
        return List.of("<-.->", "<-->", "<==>", "-.->", "-->", "---", "==>", "--x", "--o", "x--", "o--", "~~~");
    }

    private boolean isMermaidIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private boolean isMermaidIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private boolean isOpeningMermaidGrouping(char value) {
        return value == '[' || value == '(' || value == '{';
    }

    private boolean isClosingMermaidGrouping(char value) {
        return value == ']' || value == ')' || value == '}';
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private int findClosingFence(String content, int offset) {
        return content.indexOf("```", offset);
    }

    private void ensureBlankLineBefore(StringBuilder builder) {
        if (builder.isEmpty()) {
            return;
        }
        int length = builder.length();
        if (builder.charAt(length - 1) != '\n') {
            builder.append("\n\n");
            return;
        }
        if (length < 2 || builder.charAt(length - 2) != '\n') {
            builder.append('\n');
        }
    }

    private String separateGeneratedReportHeadings(String content) {
        String normalized = content;
        for (String marker : generatedReportHeadingMarkers()) {
            normalized = ensureBreakAfterMarker(normalized, marker);
        }
        return normalized;
    }

    private String separateGeneratedReportListItems(String content) {
        String normalized = content;
        for (String marker : generatedReportListMarkers()) {
            normalized = normalized.replace(marker + "-", marker + "\n\n- ");
        }
        normalized = normalized.replaceAll("([：:]\\s*)-\\s*(?=[\\p{IsHan}A-Za-z])", "$1\n\n- ");
        normalized = normalized.replaceAll("(?m)^-(?!\\s)(?=[\\p{IsHan}])", "- ");
        return normalized;
    }

    private String splitCompressedListItemsInLines(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder builder = new StringBuilder(content.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(splitCompressedListItemsInLine(lines[i]));
        }
        return builder.toString();
    }

    private String splitCompressedListItemsInLine(String line) {
        if (!line.startsWith("- ")) {
            return line;
        }
        StringBuilder builder = new StringBuilder(line.length() + 32);
        builder.append("- ");
        for (int i = 2; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '-' && i + 1 < line.length() && isListItemStart(line.charAt(i + 1))) {
                builder.append('\n').append("- ");
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private boolean isListItemStart(char value) {
        return isCjk(value) || Character.isUpperCase(value) || Character.isDigit(value);
    }

    private boolean isCjk(char value) {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return Character.UnicodeScript.HAN.equals(script);
    }

    private List<String> generatedReportListMarkers() {
        return List.of(
                "### 6.2高性能",
                "### 1.事件驱动模型",
                "### 2.数据结构实现",
                "### 3.持久化机制",
                "### 1.数据结构支持",
                "### 2.高可用架构",
                "### 3.模块扩展",
                "### 4. AI集成",
                "### 项目介绍视觉图",
                "### 长文 Markdown草稿",
                "### 演示文稿结构",
                "### 前端设计版式",
                "### 8.2演示文稿结构（ppt_generation）已生成10页演示文稿结构，包含：",
                "### 8.3前端设计版式草案（frontend_design）已生成 HTML/CSS版式草案，包含：");
    }

    private String ensureBreakAfterMarker(String content, String marker) {
        StringBuilder builder = new StringBuilder(content.length() + 64);
        int offset = 0;
        int index = content.indexOf(marker, offset);
        while (index >= 0) {
            int afterMarker = index + marker.length();
            builder.append(content, offset, afterMarker);
            if (afterMarker < content.length() && content.charAt(afterMarker) != '\n') {
                builder.append("\n\n");
            }
            offset = afterMarker;
            index = content.indexOf(marker, offset);
        }
        builder.append(content, offset, content.length());
        return builder.toString();
    }

    private List<String> generatedReportHeadingMarkers() {
        return List.of(
                "# Redis project intro",
                "## 一、项目概览",
                "## 二、架构设计",
                "## 三、架构图",
                "## 四、流程图",
                "## 五、核心逻辑",
                "## 六、重点特性",
                "## 七、关键文件证据表",
                "## 八、生成图片引用",
                "## 九、生成稿件摘要",
                "## 九、生成稿件和版式产物摘要",
                "## 十、总结",
                "## 八、生成稿件摘要",
                "## 九、总结",
                "### 关键用途",
                "### 核心定位",
                "### 关键优势",
                "### 核心架构组件",
                "### 架构层次说明",
                "### 架构要点",
                "### 命令执行流程",
                "### 持久化流程",
                "### 1.事件驱动模型",
                "### 2.数据结构实现",
                "### 3.持久化机制",
                "### 1.数据结构支持",
                "### 2.高可用架构",
                "### 3.模块扩展",
                "### 4. AI集成",
                "### 项目介绍视觉图",
                "### 长文 Markdown草稿",
                "### 演示文稿结构",
                "### 前端设计版式",
                "### 5.1单线程事件循环",
                "### 5.2数据结构实现逻辑",
                "### 5.3持久化逻辑",
                "### 6.1丰富的数据结构",
                "### 6.2高性能",
                "### 6.3高可用与扩展性",
                "### 6.4扩展能力",
                "### 6.5 AI与搜索",
                "### 新闻稿摘要",
                "### 演示文稿摘要",
                "### 前端设计摘要");
    }

    private void emitComplete(StreamCallback callback) {
        if (callback != null) {
            callback.onComplete();
        }
    }

    private record ModelTurn(String content, String thinking, List<AgentToolCall> toolCalls) {

        private ModelTurn {
            content = Objects.requireNonNullElse(content, "");
            thinking = Objects.requireNonNullElse(thinking, "");
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        private String thought() {
            if (thinking.isBlank()) {
                return content;
            }
            if (content.isBlank()) {
                return thinking;
            }
            return thinking + System.lineSeparator() + content;
        }
    }

    private record TextEncodedToolCalls(String content, List<AgentToolCall> toolCalls) {

        private TextEncodedToolCalls {
            content = Objects.requireNonNullElse(content, "");
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    private static final class TurnBuffer implements StreamCallback {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final CountDownLatch done = new CountDownLatch(1);
        private Throwable error;
        private volatile boolean completed;

        @Override
        public void onContent(String chunk) {
            if (chunk != null) {
                content.append(chunk);
            }
        }

        @Override
        public void onThinking(String chunk) {
            if (chunk != null) {
                thinking.append(chunk);
            }
        }

        @Override
        public void onComplete() {
            completed = true;
            done.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            done.countDown();
        }

        private String content() {
            return content.toString();
        }

        private String thinking() {
            return thinking.toString();
        }

        private Throwable error() {
            return error;
        }

        private boolean completed() {
            return completed;
        }

        private void awaitCompletion(AgentRunControl control) {
            while (true) {
                control.checkCancelled();
                try {
                    if (done.await(100, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AgentLoopCancelledException("Agent loop cancelled", ex);
                }
            }
        }
    }

    private TraceNodeStartCommand agentStepCommand(int step) {
        return new TraceNodeStartCommand(
                "agent-step-" + step,
                TRACE_TYPE_AGENT_STEP,
                TRACE_CLASS_NAME,
                "run",
                null,
                0);
    }

    private TraceNodeStartCommand agentToolCommand(AgentToolCall toolCall, TraceNodeScope stepScope) {
        String toolId = toolCall == null ? "unknown" : toolCall.toolId();
        return new TraceNodeStartCommand(
                "agent-tool-" + toolId,
                TRACE_TYPE_AGENT_TOOL,
                TRACE_CLASS_NAME,
                "executeTool",
                stepScope == null ? null : stepScope.nodeId(),
                1);
    }

    private static final class AgentRunControl {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<StreamCancellationHandle> modelHandle = new AtomicReference<>();
        private final AtomicReference<Future<?>> workerFuture = new AtomicReference<>();
        private final Set<ExecutorService> toolExecutors = Collections.newSetFromMap(new ConcurrentHashMap<>());

        private static AgentRunControl direct() {
            return new AgentRunControl();
        }

        private void bindWorkerFuture(Future<?> future) {
            workerFuture.set(future);
            if (cancelled.get() && future != null) {
                future.cancel(true);
            }
        }

        private void bindModelHandle(StreamCancellationHandle handle) {
            modelHandle.set(handle);
            if (cancelled.get() && handle != null) {
                handle.cancel();
            }
        }

        private void clearModelHandle(StreamCancellationHandle handle) {
            modelHandle.compareAndSet(handle, null);
        }

        private void bindToolExecutor(ExecutorService executor) {
            if (executor == null) {
                return;
            }
            toolExecutors.add(executor);
            if (cancelled.get()) {
                executor.shutdownNow();
            }
        }

        private void clearToolExecutor(ExecutorService executor) {
            if (executor != null) {
                toolExecutors.remove(executor);
            }
        }

        private boolean cancelled() {
            return cancelled.get();
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            StreamCancellationHandle handle = modelHandle.get();
            if (handle != null) {
                handle.cancel();
            }
            toolExecutors.forEach(ExecutorService::shutdownNow);
            Future<?> future = workerFuture.get();
            if (future != null) {
                future.cancel(true);
            }
        }

        private void checkCancelled() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new AgentLoopCancelledException("Agent loop cancelled");
            }
        }
    }
}
