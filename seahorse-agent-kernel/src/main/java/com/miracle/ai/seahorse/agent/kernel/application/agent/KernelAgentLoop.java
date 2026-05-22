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

import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kernel layer LLM-driven ReAct loop.
 */
public class KernelAgentLoop {

    private static final String TRUNCATED_MESSAGE =
            "Task step limit reached. Narrow the question or check tool configuration.";
    private static final int MAX_TOOL_OBSERVATION_CHARS = 8 * 1024;
    private static final String TOOL_OBSERVATION_TRUNCATED_SUFFIX = "...[truncated]";
    private static final String RAW_ARGUMENTS_KEY = "_raw";
    private static final String IDEMPOTENCY_KEY_SEPARATOR = ":";
    private static final String TRACE_TYPE_AGENT_STEP = "AGENT_STEP";
    private static final String TRACE_TYPE_AGENT_TOOL = "AGENT_TOOL";
    private static final String TRACE_CLASS_NAME =
            "com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop";

    private final StreamingChatModelPort modelPort;
    private final ToolRegistryPort toolRegistry;
    private final ToolGatewayPort toolGateway;
    private final KernelAgentLoopOptions options;
    private final KernelRagTraceRecorder traceRecorder;
    private final ContextWeaverPort contextWeaver;
    private final AgentRunStepRecorder runStepRecorder;

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
    }

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options,
                           KernelRagTraceRecorder traceRecorder,
                           ContextWeaverPort contextWeaver,
                           AgentRunStepRecorder runStepRecorder) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        ToolRegistryPort effectiveToolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.toolRegistry = effectiveToolRegistry;
        this.toolGateway = new LocalToolGatewayPort(effectiveToolRegistry);
        this.options = Objects.requireNonNullElseGet(options, KernelAgentLoopOptions::defaults);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.contextWeaver = Objects.requireNonNullElseGet(contextWeaver, DefaultContextWeaver::new);
        this.runStepRecorder = Objects.requireNonNullElseGet(runStepRecorder, AgentRunStepRecorder::noop);
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
        installMemoryContext(messages, request.memoryContext());
        messages.add(ChatMessage.user(request.question()));

        List<AgentStep> steps = new ArrayList<>();
        int maxSteps = Math.min(request.maxSteps(), options.maxSteps());
        for (int step = 0; step < maxSteps; step++) {
            runControl.checkCancelled();
            TraceNodeScope stepScope = traceRecorder.startNode(traceRunScope, agentStepCommand(step + 1));
            boolean modelTurnRecorded = false;
            try {
                ModelTurn turn = requestModelTurn(request, messages, runControl);
                recordModelTurn(request, messages, turn, null);
                modelTurnRecorded = true;
                if (turn.toolCalls().isEmpty()) {
                    emitContent(callback, turn.content());
                    steps.add(AgentStep.finalAnswer(turn.content()));
                    emitComplete(callback);
                    traceRecorder.finishNode(stepScope);
                    return new AgentLoopResult(turn.content(), steps, false);
                }

                List<AgentObservation> observations = executeTools(
                        turn.toolCalls(), request.allowedToolIds(), request, runControl, traceRunScope, stepScope);
                runControl.checkCancelled();
                emitToolThinking(callback, turn, observations);
                steps.add(AgentStep.thought(turn.thought(), turn.toolCalls(), observations));
                messages.add(ChatMessage.assistantToolCalls(turn.content(), turn.toolCalls()));
                for (AgentObservation observation : observations) {
                    messages.add(ChatMessage.tool(observation.toolCallId(), observationText(observation)));
                }
                traceRecorder.finishNode(stepScope);
            } catch (RuntimeException ex) {
                if (!modelTurnRecorded) {
                    recordModelTurn(request, messages, null, ex);
                }
                traceRecorder.finishNode(stepScope, ex);
                throw ex;
            }
        }
        emitContent(callback, TRUNCATED_MESSAGE);
        emitComplete(callback);
        return new AgentLoopResult(TRUNCATED_MESSAGE, steps, true);
    }

    private ModelTurn requestModelTurn(AgentLoopRequest request,
                                       List<ChatMessage> messages,
                                       AgentRunControl control) {
        TurnBuffer callback = new TurnBuffer();
        AtomicReference<List<AgentToolCall>> collectedCalls = new AtomicReference<>();
        AtomicBoolean collectorInvoked = new AtomicBoolean(false);

        StreamCancellationHandle handle = modelPort.streamChatWithTools(ChatRequest.builder()
                .messages(List.copyOf(messages))
                .samplingOptions(request.samplingOptions())
                .tools(exposedTools(request.allowedToolIds()))
                .toolChoice("auto")
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
        return new ModelTurn(callback.content(), callback.thinking(),
                Objects.requireNonNullElse(collectedCalls.get(), List.of()));
    }

    private List<ToolDescriptor> exposedTools(List<String> allowedToolIds) {
        List<ToolDescriptor> all = toolRegistry.listTools();
        if (allowedToolIds == null || allowedToolIds.isEmpty()) {
            return all;
        }
        Set<String> allowed = new HashSet<>(allowedToolIds);
        return all.stream()
                .filter(tool -> allowed.contains(tool.toolId()))
                .toList();
    }

    private void installMemoryContext(List<ChatMessage> messages, MemoryContext memoryContext) {
        String memoryText = contextWeaver.weave(memoryContext, ContextBudget.defaults());
        if (memoryText.isBlank()) {
            return;
        }
        if (!messages.isEmpty() && messages.get(0).getRole() == ChatRole.SYSTEM) {
            ChatMessage first = messages.get(0);
            messages.set(0, ChatMessage.system(appendMemoryText(first.getContent(), memoryText)));
            return;
        }
        messages.add(0, ChatMessage.system(memoryText));
    }

    private String appendMemoryText(String systemPrompt, String memoryText) {
        String safeSystemPrompt = Objects.requireNonNullElse(systemPrompt, "").trim();
        if (safeSystemPrompt.isBlank()) {
            return memoryText;
        }
        return safeSystemPrompt + "\n\n" + memoryText;
    }

    private List<AgentObservation> executeTools(List<AgentToolCall> toolCalls,
                                                List<String> allowedToolIds,
                                                AgentLoopRequest request,
                                                AgentRunControl control,
                                                TraceRunScope traceRunScope,
                                                TraceNodeScope stepScope) {
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
                    toolCalls.subList(start, end), allowed, request, parallelism, control, traceRunScope, stepScope));
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
                                                    AgentLoopRequest request,
                                                    int parallelism,
                                                    AgentRunControl control,
                                                    TraceRunScope traceRunScope,
                                                    TraceNodeScope stepScope) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, toolCalls.size()));
        control.bindToolExecutor(executor);
        try {
            List<Callable<AgentObservation>> tasks = toolCalls.stream()
                    .<Callable<AgentObservation>>map(toolCall ->
                            () -> executeToolTraced(toolCall, allowedToolIds, request, traceRunScope, stepScope))
                    .toList();
            List<Future<AgentObservation>> futures = executor.invokeAll(
                    tasks, perToolTimeoutNanos(), TimeUnit.NANOSECONDS);
            List<AgentObservation> observations = new ArrayList<>(toolCalls.size());
            for (int i = 0; i < futures.size(); i++) {
                AgentObservation observation = toObservation(toolCalls.get(i), futures.get(i));
                observations.add(observation);
                runStepRecorder.recordToolCall(request.runId(), toolCalls.get(i), observation);
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

    private AgentObservation executeTool(AgentToolCall toolCall, Set<String> allowedToolIds, AgentLoopRequest request) {
        if (hasRawArguments(toolCall)) {
            return AgentObservation.failed(toolCall.id(), "arguments is not valid JSON");
        }
        try {
            ToolInvocationResult result = toolGateway.invoke(toolInvocationRequest(toolCall, allowedToolIds, request));
            return result.success()
                    ? AgentObservation.ok(toolCall.id(), truncateObservationText(result.content()))
                    : AgentObservation.failed(toolCall.id(), truncateObservationText(result.error()));
        } catch (Exception ex) {
            return AgentObservation.failed(toolCall.id(),
                    truncateObservationText(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
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
                request == null ? null : request.tenantId(),
                request == null ? null : request.userId(),
                request == null ? null : request.agentIdentityId(),
                toolCall.toolId(),
                toolArguments(toolCall, request),
                java.util.Map.of(),
                idempotencyKey(runId, toolCall.id()),
                allowedToolIds == null ? List.of() : List.copyOf(allowedToolIds));
    }

    private String idempotencyKey(String runId, String toolCallId) {
        if (runId == null || runId.isBlank()) {
            return toolCallId;
        }
        return runId + IDEMPOTENCY_KEY_SEPARATOR + toolCallId;
    }

    private AgentObservation executeToolTraced(AgentToolCall toolCall,
                                               Set<String> allowedToolIds,
                                               AgentLoopRequest request,
                                               TraceRunScope traceRunScope,
                                               TraceNodeScope stepScope) {
        TraceNodeScope toolScope = traceRecorder.startNode(traceRunScope, agentToolCommand(toolCall, stepScope));
        try {
            AgentObservation observation = executeTool(toolCall, allowedToolIds, request);
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

    private String observationText(AgentObservation observation) {
        return observation.success() ? observation.content() : observation.error();
    }

    private void recordModelTurn(AgentLoopRequest request,
                                 List<ChatMessage> messages,
                                 ModelTurn turn,
                                 Throwable error) {
        runStepRecorder.recordModelTurn(
                request.runId(),
                AgentRunStepRecorder.modelTurnInput(messages, exposedTools(request.allowedToolIds())),
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

    private void emitContent(StreamCallback callback, String content) {
        if (callback != null && content != null && !content.isEmpty()) {
            callback.onContent(content);
        }
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
