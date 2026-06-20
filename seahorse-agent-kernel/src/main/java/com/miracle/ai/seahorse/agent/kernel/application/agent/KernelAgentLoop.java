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
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopExitReason;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputGovernanceResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamAgentStepEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamApprovalRequiredEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamSkillEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamToolCallEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Kernel layer LLM-driven ReAct loop.
 */
public class KernelAgentLoop implements ReActExecutorPort {

    private static final String TRUNCATED_MESSAGE =
            "Task step limit reached. Narrow the question or check tool configuration.";
    private static final String WAITING_APPROVAL_MESSAGE = "Waiting for tool approval.";
    private static final String TRACE_TYPE_AGENT_STEP = "AGENT_STEP";
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
    private static final String TRACE_CLASS_NAME =
            "com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamingChatModelPort modelPort;
    private final ToolGatewayPort toolGateway;
    private final KernelAgentLoopOptions options;
    private final KernelRagTraceRecorder traceRecorder;
    private final AgentRunStepRecorder runStepRecorder;
    private final AgentApprovalWaitHandler approvalWaitHandler;
    // Phase D Slice 1a：可选输出治理。null 表示运行时未启用输出治理，保持引入前行为。
    private final OutputGovernanceService outputGovernance;
    private final MarkdownNormalizer markdownNormalizer;
    private final AgentStreamEmitter streamEmitter;
    private final AgentLoopStreamEvents streamEvents;
    private final AgentLoopModelTurns modelTurns;
    private final AgentLoopToolExecutor toolExecutor;

    public KernelAgentLoop(AgentLoopDependencies dependencies) {
        AgentLoopDependencies deps = Objects.requireNonNull(dependencies, "dependencies must not be null");
        this.modelPort = deps.modelPort();
        this.toolGateway = deps.toolGateway();
        this.options = deps.options();
        this.traceRecorder = deps.traceRecorder();
        this.runStepRecorder = deps.runStepRecorder();
        this.approvalWaitHandler = deps.approvalWaitHandler();
        this.outputGovernance = deps.outputGovernance();
        this.markdownNormalizer = deps.markdownNormalizer();
        this.streamEmitter = deps.streamEmitter();
        this.streamEvents = new AgentLoopStreamEvents(streamEmitter);
        this.modelTurns = new AgentLoopModelTurns(modelPort, deps.toolRegistry(), deps.contextWeaver(),
                deps.toolCallParser());
        this.toolExecutor = new AgentLoopToolExecutor(options, toolGateway, traceRecorder, runStepRecorder,
                modelTurns);
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
        modelTurns.installRuntimeContext(messages, request.contextPack(), request.memoryContext(),
                request.skillRuntimeContext());
        streamEvents.emitSkillRuntimeEvents(callback, request);
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
            streamEvents.emitStepStarted(callback, request, stepId, stepNo, stepStartedAt);
            TraceNodeScope stepScope = traceRecorder.startNode(traceRunScope, agentStepCommand(stepNo));
            boolean modelTurnRecorded = false;
            try {
                ModelTurn turn = modelTurns.requestModelTurn(request, messages, runControl, exhaustedToolIds);
                recordModelTurn(request, messages, turn, exhaustedToolIds, null);
                modelTurnRecorded = true;
                if (turn.toolCalls().isEmpty()) {
                    String finalContent = markdownNormalizer.normalizeFinalMarkdown(
                            applyOutputGovernance(request, turn.content()));
                    streamEvents.emitContent(callback, finalContent);
                    streamEvents.emitFinalArtifact(callback, request, finalContent);
                    steps.add(AgentStep.finalAnswer(finalContent));
                    streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                            null, finalContent);
                    streamEvents.emitComplete(callback);
                    traceRecorder.finishNode(stepScope);
                    return new AgentLoopResult(finalContent, steps, false);
                }

                List<String> effectiveAllowedToolIds = modelTurns.effectiveAllowedToolIds(request);
                List<AgentObservation> observations = toolExecutor.executeTools(
                        turn.toolCalls(),
                        effectiveAllowedToolIds,
                        modelTurns.exposedToolIds(request, exhaustedToolIds),
                        request,
                        runControl,
                        traceRunScope,
                        stepScope,
                        toolEvents(callback));
                markExhaustedTools(turn.toolCalls(), observations, exhaustedToolIds);
                hasToolObservations = hasToolObservations || !observations.isEmpty();
                if (requiresApproval(observations)) {
                    streamEvents.emitToolThinking(callback, turn, observations);
                    AgentStep pendingStep = AgentStep.thought(turn.thought(), turn.toolCalls(), observations);
                    steps.add(pendingStep);
                    AgentToolCall pendingToolCall = pendingToolCall(turn.toolCalls(), observations);
                    approvalWaitHandler.waitForApproval(new AgentApprovalWaitCommand(
                            toolExecutor.toolInvocationRequest(
                                    pendingToolCall,
                                    allowedToolIdSet(effectiveAllowedToolIds),
                                    request),
                            waitingApprovalStateJson(turn, observations),
                            waitingApprovalMessages(messages, turn)));
                    streamEvents.emitContent(callback, WAITING_APPROVAL_MESSAGE);
                    streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                            null, WAITING_APPROVAL_MESSAGE);
                    streamEvents.emitComplete(callback);
                    traceRecorder.finishNode(stepScope);
                    return new AgentLoopResult(
                            WAITING_APPROVAL_MESSAGE,
                            steps,
                            false,
                            AgentLoopExitReason.WAITING_APPROVAL);
                }
                runControl.checkCancelled();
                streamEvents.emitToolThinking(callback, turn, observations);
                streamEvents.emitSourcesFromObservations(callback, turn.toolCalls(), observations);
                steps.add(AgentStep.thought(turn.thought(), turn.toolCalls(), observations));
                messages.add(ChatMessage.assistantToolCalls(turn.content(), turn.toolCalls()));
                for (AgentObservation observation : observations) {
                    messages.add(ChatMessage.tool(observation.toolCallId(), observationText(observation)));
                }
                streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                        null, null);
                traceRecorder.finishNode(stepScope);
            } catch (RuntimeException ex) {
                if (!modelTurnRecorded) {
                    recordModelTurn(request, messages, null, exhaustedToolIds, ex);
                }
                streamEvents.emitRecoverableError(callback, request, stepId, ex);
                streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.FAILED,
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
        streamEvents.emitContent(callback, TRUNCATED_MESSAGE);
        streamEvents.emitComplete(callback);
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
        streamEvents.emitStepStarted(callback, request, stepId, stepNo, stepStartedAt);
        TraceNodeScope stepScope = traceRecorder.startNode(traceRunScope, agentStepCommand(stepNo));
        try {
            ModelTurn turn = modelTurns.requestFinalModelTurn(request, messages, runControl);
            recordModelTurn(request, messages, turn, Set.of(), null);
            if (!turn.toolCalls().isEmpty()) {
                streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.FAILED,
                        null, "Final answer turn attempted to call tools.");
                traceRecorder.finishNode(stepScope);
                return null;
            }
            String finalContent = markdownNormalizer.normalizeFinalMarkdown(
                    applyOutputGovernance(request, turn.content()));
            streamEvents.emitContent(callback, finalContent);
            streamEvents.emitFinalArtifact(callback, request, finalContent);
            steps.add(AgentStep.finalAnswer(finalContent));
            streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.SUCCEEDED,
                    null, finalContent);
            streamEvents.emitComplete(callback);
            traceRecorder.finishNode(stepScope);
            return new AgentLoopResult(finalContent, steps, false);
        } catch (RuntimeException ex) {
            recordModelTurn(request, messages, null, Set.of(), ex);
            streamEvents.emitRecoverableError(callback, request, stepId, ex);
            streamEvents.emitStepFinished(callback, request, stepId, stepNo, stepStartedAt, AgentStepStatus.FAILED,
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

    private String observationText(AgentObservation observation) {
        return observation.success() ? observation.content() : observation.error();
    }

    private AgentLoopToolExecutor.ToolEvents toolEvents(StreamCallback callback) {
        return new AgentLoopToolExecutor.ToolEvents() {
            @Override
            public void toolCallStarted(AgentLoopRequest request, AgentToolCall toolCall) {
                streamEvents.emitToolCallStarted(callback, request, toolCall);
            }

            @Override
            public void toolCallFinished(
                    AgentLoopRequest request,
                    AgentToolCall toolCall,
                    AgentObservation observation) {
                streamEvents.emitToolCallFinished(callback, request, toolCall, observation);
            }

            @Override
            public void toolCallWaitingUser(
                    AgentLoopRequest request,
                    AgentToolCall toolCall,
                    AgentObservation observation) {
                streamEvents.emitToolCallWaitingUser(callback, request, toolCall, observation);
            }

            @Override
            public void skillResourceLoaded(
                    AgentLoopRequest request,
                    AgentToolCall toolCall,
                    AgentObservation observation) {
                streamEvents.emitSkillResourceLoaded(callback, request, toolCall, observation);
            }
        };
    }

    private String modelStepId(int stepNo) {
        return MODEL_STEP_ID_PREFIX + stepNo;
    }

    private void recordModelTurn(AgentLoopRequest request,
                                 List<ChatMessage> messages,
                                 ModelTurn turn,
                                 Set<String> exhaustedToolIds,
                                 Throwable error) {
        runStepRecorder.recordModelTurn(
                request.runId(),
                AgentRunStepRecorder.modelTurnInput(messages,
                        modelTurns.exposedTools(modelTurns.effectiveAllowedToolIds(request),
                                request.skillRuntimeBlocks(), exhaustedToolIds)),
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

    private TraceNodeStartCommand agentStepCommand(int step) {
        return new TraceNodeStartCommand(
                "agent-step-" + step,
                TRACE_TYPE_AGENT_STEP,
                TRACE_CLASS_NAME,
                "run",
                null,
                0);
    }

}
