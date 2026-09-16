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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentLoopCancelledException;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentLoopException;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 波次内的并行模型轮次：让互不依赖的步骤同时向模型发问，再把结果确定性地合并。
 *
 * <p><b>只并行"发问"，不并行"落地"</b>。每个步骤拿的是同一份基础消息快照 + 一条只描述
 * 自己那一步的 system 聚焦消息，模型轮次在线程池里并发执行；工具调用、观察结果回灌、
 * 事件发送、状态记账仍然由主线程按波次顺序完成。这样既拿到了并行收益（模型轮次是
 * 长时程任务里最慢的一环），又不需要让工具网关、流式发射器、trace 记录器都变成线程安全的。
 *
 * <p>失败语义与串行保持一致：任意一个步骤的模型轮次抛异常，整个波次就失败并把原始异常
 * 抛回主循环——并行只是加速手段，不能变成"部分成功"的新语义。
 */
final class AgentLoopParallelTurns {

    private static final String STEP_FOCUS_PREFIX =
            "现在只推进规划中的这一步，不要输出最终答案：";

    private final int maxParallelSteps;

    AgentLoopParallelTurns(int maxParallelSteps) {
        this.maxParallelSteps = Math.max(1, maxParallelSteps);
    }

    /**
     * 单次模型轮次的发起动作。由主循环提供，便于把 trace / 遥测留在主循环里决定。
     */
    interface TurnRunner {

        ModelTurn run(List<ChatMessage> messages, Set<String> exhaustedToolIds);
    }

    /**
     * 按步骤顺序执行（或并发执行）一个波次内的模型轮次。
     *
     * @return 与 {@code steps} 顺序一一对应的轮次结果
     */
    List<ModelTurn> runTurns(List<PlanStep> steps,
                             List<ChatMessage> baseMessages,
                             Set<String> exhaustedToolIds,
                             TurnRunner runner) {
        List<PlanStep> safe = steps == null ? List.of() : steps;
        if (safe.isEmpty()) {
            return List.of();
        }
        if (safe.size() == 1 || maxParallelSteps <= 1) {
            List<ModelTurn> sequential = new ArrayList<>(safe.size());
            for (PlanStep step : safe) {
                sequential.add(runner.run(focusedMessages(baseMessages, step), exhaustedToolIds));
            }
            return List.copyOf(sequential);
        }
        return runConcurrently(safe, baseMessages, exhaustedToolIds, runner);
    }

    /**
     * 把多个步骤的轮次合并成一个"聚合轮次"，让主循环后续的工具执行与消息回灌逻辑不必区分
     * 单步还是多步。合并顺序遵循波次顺序，因此结果是可重现的。
     */
    ModelTurn merge(List<ModelTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return new ModelTurn("", "", List.of(), null);
        }
        if (turns.size() == 1) {
            return turns.get(0);
        }
        List<AgentToolCall> toolCalls = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        ModelContextEnvelopeEvidence evidence = null;
        for (ModelTurn turn : turns) {
            toolCalls.addAll(turn.toolCalls());
            appendSection(content, turn.content());
            appendSection(thinking, turn.thinking());
            if (evidence == null && turn.contextEvidence() != null) {
                evidence = turn.contextEvidence();
            }
        }
        return new ModelTurn(content.toString(), thinking.toString(), List.copyOf(toolCalls), evidence);
    }

    /**
     * @return 只聚焦于指定步骤的消息列表；基础消息被复制，并发分支之间不会互相看到对方的写入
     */
    List<ChatMessage> focusedMessages(List<ChatMessage> baseMessages, PlanStep step) {
        List<ChatMessage> messages = new ArrayList<>(
                Objects.requireNonNullElse(baseMessages, List.of()));
        String instruction = step == null ? "" : step.instruction();
        if (!instruction.isBlank()) {
            messages.add(ChatMessage.system(STEP_FOCUS_PREFIX + instruction));
        }
        return messages;
    }

    private List<ModelTurn> runConcurrently(List<PlanStep> steps,
                                            List<ChatMessage> baseMessages,
                                            Set<String> exhaustedToolIds,
                                            TurnRunner runner) {
        int parallelism = Math.min(maxParallelSteps, steps.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(runnable, "seahorse-agent-plan-step");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Callable<ModelTurn>> tasks = new ArrayList<>(steps.size());
            for (PlanStep step : steps) {
                List<ChatMessage> stepMessages = focusedMessages(baseMessages, step);
                tasks.add(() -> runner.run(stepMessages, exhaustedToolIds));
            }
            List<Future<ModelTurn>> futures = executor.invokeAll(tasks);
            List<ModelTurn> turns = new ArrayList<>(futures.size());
            for (int i = 0; i < futures.size(); i++) {
                turns.add(await(futures.get(i)));
            }
            return List.copyOf(turns);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentLoopCancelledException(
                    "Agent loop cancelled while running parallel plan steps", ex);
        } finally {
            executor.shutdownNow();
        }
    }

    private static ModelTurn await(Future<ModelTurn> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentLoopCancelledException(
                    "Agent loop cancelled while running parallel plan steps", ex);
        } catch (ExecutionException ex) {
            Throwable cause = Objects.requireNonNullElse(ex.getCause(), ex);
            throw cause instanceof RuntimeException runtime
                    ? runtime
                    : new AgentLoopException("Parallel plan step failed", cause);
        }
    }

    private static void appendSection(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(System.lineSeparator());
        }
        builder.append(value);
    }

    int maxParallelSteps() {
        return maxParallelSteps;
    }
}
