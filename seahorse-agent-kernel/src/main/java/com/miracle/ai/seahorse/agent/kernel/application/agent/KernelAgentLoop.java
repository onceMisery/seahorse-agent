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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kernel 层 LLM-driven ReAct 循环。
 */
public class KernelAgentLoop {

    private static final String TRUNCATED_MESSAGE = "任务步骤已达上限，请缩小问题范围或检查工具配置。";
    private static final int MAX_TOOL_OBSERVATION_CHARS = 8 * 1024;
    private static final String TOOL_OBSERVATION_TRUNCATED_SUFFIX = "...[truncated]";

    private final StreamingChatModelPort modelPort;
    private final ToolRegistryPort toolRegistry;
    private final KernelAgentLoopOptions options;

    public KernelAgentLoop(StreamingChatModelPort modelPort,
                           ToolRegistryPort toolRegistry,
                           KernelAgentLoopOptions options) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort 不能为 null");
        this.toolRegistry = Objects.requireNonNullElse(toolRegistry, ToolRegistryPort.empty());
        this.options = Objects.requireNonNullElseGet(options, KernelAgentLoopOptions::defaults);
    }

    public AgentLoopResult execute(AgentLoopRequest request) {
        return run(request, null);
    }

    public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
        try {
            run(request, callback);
        } catch (Exception ex) {
            if (callback != null) {
                callback.onError(ex);
            } else {
                throw ex;
            }
        }
        return () -> {
        };
    }

    private AgentLoopResult run(AgentLoopRequest request, StreamCallback callback) {
        Objects.requireNonNull(request, "AgentLoopRequest 不能为 null");
        List<ChatMessage> messages = new ArrayList<>(request.history());
        messages.add(ChatMessage.user(request.question()));

        List<AgentStep> steps = new ArrayList<>();
        int maxSteps = Math.min(request.maxSteps(), options.maxSteps());
        for (int step = 0; step < maxSteps; step++) {
            ModelTurn turn = requestModelTurn(request, messages);
            if (turn.toolCalls().isEmpty()) {
                emitContent(callback, turn.content());
                steps.add(AgentStep.finalAnswer(turn.content()));
                emitComplete(callback);
                return new AgentLoopResult(turn.content(), steps, false);
            }

            List<AgentObservation> observations = executeTools(turn.toolCalls(), request.allowedToolIds());
            emitToolThinking(callback, turn, observations);
            steps.add(AgentStep.thought(turn.thought(), turn.toolCalls(), observations));
            messages.add(ChatMessage.assistantToolCalls(turn.content(), turn.toolCalls()));
            for (AgentObservation observation : observations) {
                messages.add(ChatMessage.tool(observation.toolCallId(), observationText(observation)));
            }
        }
        emitContent(callback, TRUNCATED_MESSAGE);
        emitComplete(callback);
        return new AgentLoopResult(TRUNCATED_MESSAGE, steps, true);
    }

    private ModelTurn requestModelTurn(AgentLoopRequest request, List<ChatMessage> messages) {
        TurnBuffer callback = new TurnBuffer();
        AtomicReference<List<AgentToolCall>> collectedCalls = new AtomicReference<>();
        AtomicBoolean collectorInvoked = new AtomicBoolean(false);

        modelPort.streamChatWithTools(ChatRequest.builder()
                .messages(List.copyOf(messages))
                .samplingOptions(request.samplingOptions())
                .tools(exposedTools(request.allowedToolIds()))
                .toolChoice("auto")
                .build(), callback, toolCalls -> {
                    if (callback.completed()) {
                        throw new AgentLoopException("模型适配器协议错误：collector 晚于 onComplete 调用");
                    }
                    if (!collectorInvoked.compareAndSet(false, true)) {
                        throw new AgentLoopException("工具调用 collector 被重复调用");
                    }
                    collectedCalls.set(toolCalls == null ? List.of() : List.copyOf(toolCalls));
                });

        if (callback.error() != null) {
            throw new AgentLoopException("模型流式调用失败", callback.error());
        }
        if (!collectorInvoked.get()) {
            throw new AgentLoopException("模型适配器协议错误：collector 未被调用");
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

    private List<AgentObservation> executeTools(List<AgentToolCall> toolCalls, List<String> allowedToolIds) {
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
            observations.addAll(executeToolBatch(toolCalls.subList(start, end), allowed, parallelism));
            if (Thread.currentThread().isInterrupted() && observations.size() < toolCalls.size()) {
                for (int i = observations.size(); i < toolCalls.size(); i++) {
                    observations.add(AgentObservation.failed(toolCalls.get(i).id(), "工具执行被中断"));
                }
                break;
            }
        }
        return observations;
    }

    private List<AgentObservation> executeToolBatch(List<AgentToolCall> toolCalls,
                                                    Set<String> allowedToolIds,
                                                    int parallelism) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, toolCalls.size()));
        try {
            List<Callable<AgentObservation>> tasks = toolCalls.stream()
                    .<Callable<AgentObservation>>map(toolCall -> () -> executeTool(toolCall, allowedToolIds))
                    .toList();
            List<Future<AgentObservation>> futures = executor.invokeAll(
                    tasks, perToolTimeoutNanos(), TimeUnit.NANOSECONDS);
            List<AgentObservation> observations = new ArrayList<>(toolCalls.size());
            for (int i = 0; i < futures.size(); i++) {
                observations.add(toObservation(toolCalls.get(i), futures.get(i)));
            }
            return observations;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return toolCalls.stream()
                    .map(toolCall -> AgentObservation.failed(toolCall.id(), "工具执行被中断"))
                    .toList();
        } finally {
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
            return AgentObservation.failed(toolCall.id(), "工具超时");
        }
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return AgentObservation.failed(toolCall.id(), "工具执行被中断");
        } catch (ExecutionException ex) {
            Throwable cause = Objects.requireNonNullElse(ex.getCause(), ex);
            return AgentObservation.failed(toolCall.id(),
                    Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getName()));
        }
    }

    private AgentObservation executeTool(AgentToolCall toolCall, Set<String> allowedToolIds) {
        if (!allowedToolIds.isEmpty() && !allowedToolIds.contains(toolCall.toolId())) {
            return AgentObservation.failed(toolCall.id(), "Tool 不在 allowlist 中: " + toolCall.toolId());
        }
        try {
            ToolPort toolPort = toolRegistry.find(toolCall.toolId())
                    .orElseGet(() -> ToolPort.notFound(toolCall.toolId()));
            ToolInvocationResult result = toolPort.invoke(toolCall.id(), toolCall.toolId(), toolCall.arguments());
            return result.success()
                    ? AgentObservation.ok(toolCall.id(), truncateObservationText(result.content()))
                    : AgentObservation.failed(toolCall.id(), truncateObservationText(result.error()));
        } catch (Exception ex) {
            return AgentObservation.failed(toolCall.id(),
                    truncateObservationText(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
        }
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
            callback.onThinking("[工具调用] " + toolCall.toolId() + " -> "
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
            if (!thinking.isBlank()) {
                return thinking;
            }
            return content;
        }
    }

    private static final class TurnBuffer implements StreamCallback {
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private Throwable error;
        private boolean completed;

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
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
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
    }
}
