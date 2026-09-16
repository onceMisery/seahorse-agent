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

package com.miracle.ai.seahorse.agent.kernel.application.agent.plan;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlanningRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.TaskPlannerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmTaskPlannerTests {

    private static final String LLM_STRATEGY = LlmTaskPlanner.STRATEGY;
    private static final String HEURISTIC_STRATEGY = DefaultTaskPlanner.STRATEGY;

    /** 启发式也能拆开的目标，这样"走了模型"和"走了兜底"在断言上可区分。 */
    private static final String DECOMPOSABLE_GOAL = "首先收集 Redis 资料，然后撰写对比报告";

    @Test
    void shouldAdoptModelDecompositionWhenOutputIsParsable() {
        StubModel model = StubModel.replying("1. 收集 Redis 官方基准数据\n2. 对比持久化策略\n3. 输出结论");

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6, "planner-model"));

        assertEquals(LLM_STRATEGY, plan.strategy());
        assertEquals(3, plan.stepCount());
        // 规划的目标必须是原始目标，而不是模型改写后的文本。
        assertEquals(DECOMPOSABLE_GOAL, plan.goal());
        assertTrue(plan.stepAt(0).title().contains("收集 Redis 官方基准数据"));
        assertEquals(List.of("plan-step-1"), plan.stepAt(1).dependsOn());
    }

    @Test
    void shouldSendPlanningRequestWithoutToolsAndWithZeroTemperature() {
        StubModel model = StubModel.replying("1. 第一步内容\n2. 第二步内容");

        new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6, "planner-model"));

        ChatRequest request = model.captured();
        assertNotNull(request);
        assertEquals("planner-model", request.getModelId());
        assertTrue(request.getTools().isEmpty(), "规划阶段不应带工具");
        assertEquals(Boolean.FALSE, request.getEnableTools());
        assertEquals(0.0D, request.getTemperature());
        assertEquals(2, request.getMessages().size());
        assertTrue(request.getMessages().get(0).getContent().contains(DECOMPOSABLE_GOAL));
        assertEquals(DECOMPOSABLE_GOAL, request.getMessages().get(1).getContent());
    }

    @Test
    void shouldFallBackToHeuristicWhenModelThrows() {
        StubModel model = StubModel.throwing(new IllegalStateException("model unavailable"));

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6));

        assertEquals(HEURISTIC_STRATEGY, plan.strategy());
        assertEquals(2, plan.stepCount(), "兜底必须对原始目标重新规划，而不是放弃规划");
        assertEquals(DECOMPOSABLE_GOAL, plan.goal());
    }

    @Test
    void shouldFallBackToHeuristicWhenModelOutputIsBlank() {
        StubModel model = StubModel.replying("   ");

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6));

        assertEquals(HEURISTIC_STRATEGY, plan.strategy());
        assertEquals(2, plan.stepCount());
    }

    @Test
    void shouldFallBackToHeuristicWhenModelOutputIsNotDecomposable() {
        StubModel model = StubModel.replying("这个问题需要综合考虑，无法简单拆分。");

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6));

        assertEquals(HEURISTIC_STRATEGY, plan.strategy());
        assertEquals(2, plan.stepCount());
    }

    @Test
    void shouldFallBackToHeuristicWhenModelReportsError() {
        StubModel model = StubModel.erroring(new RuntimeException("upstream 500"));

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6));

        assertEquals(HEURISTIC_STRATEGY, plan.strategy());
        assertEquals(2, plan.stepCount());
    }

    @Test
    void shouldCancelStreamAndFallBackWhenModelTimesOut() {
        StubModel model = StubModel.hanging();

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofMillis(150)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6));

        assertEquals(HEURISTIC_STRATEGY, plan.strategy());
        assertEquals(2, plan.stepCount());
        assertTrue(model.cancelled(), "超时后必须取消流，避免遗留悬挂的模型连接");
    }

    @Test
    void shouldSkipModelWhenGoalIsBlank() {
        StubModel model = StubModel.replying("1. a\n2. b");

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of("   ", "run-1", 6));

        assertEquals(HEURISTIC_STRATEGY, plan.strategy());
        assertEquals(0, model.calls(), "空目标没有可规划内容，不应浪费一次模型调用");
        assertEquals(0, plan.stepCount());
    }

    @Test
    void shouldRespectMaxStepsWhenParsingModelOutput() {
        StubModel model = StubModel.replying("1. a\n2. b\n3. c\n4. d");

        TaskPlan plan = new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)))
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 2));

        assertEquals(LLM_STRATEGY, plan.strategy());
        assertEquals(2, plan.stepCount(), "请求里的 maxSteps 必须生效，模型输出不能突破上限");
    }

    @Test
    void shouldUseExplicitFallbackPlannerOnFailure() {
        StubModel model = StubModel.throwing(new IllegalStateException("boom"));
        TaskPlannerStub fallback = new TaskPlannerStub();

        new LlmTaskPlanner(model, policy(Duration.ofSeconds(5)), fallback)
                .plan(TaskPlanningRequest.of(DECOMPOSABLE_GOAL, "run-1", 6));

        assertEquals(1, fallback.calls());
        assertEquals(DECOMPOSABLE_GOAL, fallback.lastGoal(), "回退必须针对原始目标，而不是模型改写后的文本");
    }

    private static TaskPlanningPolicy policy(Duration timeout) {
        return TaskPlanningPolicy.builder()
                .enabled(true)
                .maxSteps(6)
                .plannerMode(TaskPlanningPolicy.PlannerMode.LLM_WITH_HEURISTIC_FALLBACK)
                .plannerTimeout(timeout)
                .build();
    }

    private static final class TaskPlannerStub implements TaskPlannerPort {

        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastGoal = new AtomicReference<>();

        @Override
        public TaskPlan plan(TaskPlanningRequest request) {
            calls.incrementAndGet();
            lastGoal.set(request == null ? null : request.goal());
            return TaskPlan.empty(request == null ? "" : request.goal(), "stub");
        }

        private int calls() {
            return calls.get();
        }

        private String lastGoal() {
            return lastGoal.get();
        }
    }

    /** 可编程的模型桩：返回文本、抛异常、回调 onError，或永不结束（用于超时）。 */
    private static final class StubModel implements StreamingChatModelPort {

        private enum Mode {REPLY, THROW, ERROR, HANG}

        private final Mode mode;
        private final String text;
        private final RuntimeException failure;
        private final Throwable reportedError;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<ChatRequest> captured = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private StubModel(Mode mode, String text, RuntimeException failure, Throwable reportedError) {
            this.mode = mode;
            this.text = text;
            this.failure = failure;
            this.reportedError = reportedError;
        }

        private static StubModel replying(String text) {
            return new StubModel(Mode.REPLY, text, null, null);
        }

        private static StubModel throwing(RuntimeException failure) {
            return new StubModel(Mode.THROW, null, failure, null);
        }

        private static StubModel erroring(Throwable reportedError) {
            return new StubModel(Mode.ERROR, null, null, reportedError);
        }

        private static StubModel hanging() {
            return new StubModel(Mode.HANG, null, null, null);
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            calls.incrementAndGet();
            captured.set(request);
            return switch (mode) {
                case THROW -> throw failure;
                case ERROR -> {
                    callback.onError(reportedError);
                    yield () -> cancelled.set(true);
                }
                case HANG -> () -> cancelled.set(true);
                default -> {
                    callback.onContent(text);
                    callback.onComplete();
                    yield () -> cancelled.set(true);
                }
            };
        }

        private ChatRequest captured() {
            return captured.get();
        }

        private int calls() {
            return calls.get();
        }

        private boolean cancelled() {
            return cancelled.get();
        }
    }
}
