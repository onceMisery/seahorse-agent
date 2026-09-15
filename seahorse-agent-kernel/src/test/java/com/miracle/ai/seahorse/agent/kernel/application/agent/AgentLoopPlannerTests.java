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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.TaskPlannerPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentLoopPlanner} 的行为契约：关闭时不介入、失败时退化、状态推进可追踪。
 */
class AgentLoopPlannerTests {

    private static final String GOAL = "1. 收集 Redis 资料 2. 撰写对比报告";

    @Test
    void shouldReturnNullWhenPolicyIsDisabled() {
        RecordingRecorder recorder = new RecordingRecorder();
        AgentLoopPlanner planner = new AgentLoopPlanner(TaskPlanningPolicy.defaults(), null, recorder);

        assertNull(planner.planRun(request(), null, 3));
        assertTrue(recorder.plans.isEmpty());
        assertTrue(recorder.errors.isEmpty());
    }

    @Test
    void shouldReturnPlanWhenPolicyIsEnabled() {
        RecordingRecorder recorder = new RecordingRecorder();
        AgentLoopPlanner planner = new AgentLoopPlanner(
                TaskPlanningPolicy.builder().enabled(true).build(), null, recorder);

        TaskPlan plan = planner.planRun(request(), null, 3);

        assertNotNull(plan);
        assertEquals(2, plan.stepCount());
        assertEquals(1, recorder.plans.size());
    }

    @Test
    void shouldDegradeToNoPlanWhenThePlannerThrows() {
        RecordingRecorder recorder = new RecordingRecorder();
        AgentLoopPlanner planner = new AgentLoopPlanner(
                TaskPlanningPolicy.builder().enabled(true).build(),
                null,
                recorder,
                request -> {
                    throw new IllegalStateException("planner unavailable");
                });

        assertNull(planner.planRun(request(), null, 3));
        assertEquals(1, recorder.errors.size());
        assertEquals("planner unavailable", recorder.errors.get(0).getMessage());
        assertTrue(recorder.plans.isEmpty());
    }

    @Test
    void shouldDegradeToNoPlanWhenThePlannerReturnsAnEmptyPlan() {
        RecordingRecorder recorder = new RecordingRecorder();
        AgentLoopPlanner planner = new AgentLoopPlanner(
                TaskPlanningPolicy.builder().enabled(true).build(),
                null,
                recorder,
                request -> TaskPlan.empty("目标", "empty-planner"));

        assertNull(planner.planRun(request(), null, 3));
        assertTrue(recorder.plans.isEmpty());
        assertTrue(recorder.errors.isEmpty());
    }

    @Test
    void shouldInjectOutlineAsASystemMessage() {
        AgentLoopPlanner planner = new AgentLoopPlanner(
                TaskPlanningPolicy.builder().enabled(true).build(), null, new RecordingRecorder());
        TaskPlan plan = TaskPlan.of("目标", "test", List.of(
                PlanStep.pending(0, "收集资料", "收集资料", List.of())));
        List<ChatMessage> messages = new ArrayList<>();

        planner.appendPlan(plan, messages);

        assertEquals(1, messages.size());
        assertEquals(ChatRole.SYSTEM, messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("# Task plan (1 steps)"));
    }

    @Test
    void shouldNotInjectOutlineWhenDisabledOrInjectionIsOff() {
        List<ChatMessage> disabled = new ArrayList<>();
        new AgentLoopPlanner(TaskPlanningPolicy.defaults(), null, new RecordingRecorder())
                .appendPlan(TaskPlan.singleStep("目标", "test"), disabled);
        assertTrue(disabled.isEmpty());

        List<ChatMessage> injectionOff = new ArrayList<>();
        new AgentLoopPlanner(TaskPlanningPolicy.builder()
                .enabled(true)
                .includeOutlineInPrompt(false)
                .build(), null, new RecordingRecorder())
                .appendPlan(TaskPlan.singleStep("目标", "test"), injectionOff);
        assertTrue(injectionOff.isEmpty());
    }

    @Test
    void shouldNotInjectAnythingWithoutAPlan() {
        List<ChatMessage> messages = new ArrayList<>();

        new AgentLoopPlanner(TaskPlanningPolicy.builder().enabled(true).build(), null, new RecordingRecorder())
                .appendPlan(null, messages);

        assertTrue(messages.isEmpty());
    }

    @Test
    void shouldAdvanceStepStatusAndRecordEachTransition() {
        RecordingRecorder recorder = new RecordingRecorder();
        AgentLoopPlanner planner = new AgentLoopPlanner(
                TaskPlanningPolicy.defaults(), null, recorder);
        TaskPlan plan = TaskPlan.of("目标", "test", List.of(
                PlanStep.pending(0, "收集", "收集", List.of()),
                PlanStep.pending(1, "汇总", "汇总", List.of("plan-step-1"))));

        TaskPlan started = planner.startStep(plan, request(), 0);
        TaskPlan completed = planner.completeStep(started, request(), 0);
        TaskPlan failed = planner.failStep(completed, request(), 0);

        assertEquals(PlanStepStatus.IN_PROGRESS, started.stepAt(0).status());
        assertEquals(PlanStepStatus.COMPLETED, completed.stepAt(0).status());
        assertEquals(PlanStepStatus.FAILED, failed.stepAt(0).status());
        assertEquals(3, recorder.plans.size());
    }

    @Test
    void shouldReuseTheLastStepWhenTheLoopRunsMoreIterationsThanPlanned() {
        AgentLoopPlanner planner = new AgentLoopPlanner(TaskPlanningPolicy.defaults(), null, new RecordingRecorder());
        TaskPlan plan = TaskPlan.singleStep("目标", "test");

        TaskPlan advanced = planner.completeStep(plan, request(), 5);

        assertEquals(PlanStepStatus.COMPLETED, advanced.stepAt(0).status());
    }

    @Test
    void shouldTolerateANullPlanAndNegativeStepIndex() {
        AgentLoopPlanner planner = new AgentLoopPlanner(TaskPlanningPolicy.defaults(), null, new RecordingRecorder());

        assertNull(planner.startStep(null, request(), 0));
        TaskPlan plan = TaskPlan.singleStep("目标", "test");
        assertSame(plan, planner.completeStep(plan, request(), -3));
    }

    @Test
    void shouldKeepLoopSemanticsWhenTheRecorderItselfFails() {
        AgentRunStepRecorder exploding = new AgentRunStepRecorder() {
            @Override
            public void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error) {
            }

            @Override
            public void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation) {
            }

            @Override
            public void recordTaskPlan(String runId, TaskPlan plan, Throwable error) {
                throw new IllegalStateException("recorder down");
            }
        };
        AgentLoopPlanner planner = new AgentLoopPlanner(
                TaskPlanningPolicy.builder().enabled(true).build(), null, exploding);

        // 记账失败不得冒泡到循环：规划本身仍然可用
        TaskPlan plan = planner.planRun(request(), null, 3);

        assertNotNull(plan);
        assertEquals(2, plan.stepCount());
    }

    private static AgentLoopRequest request() {
        return AgentLoopRequest.builder()
                .question(GOAL)
                .runId("run-planner-1")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(3)
                .build();
    }

    private static final class RecordingRecorder implements AgentRunStepRecorder {

        private final List<TaskPlan> plans = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void recordModelTurn(String runId, String inputJson, String outputJson, Throwable error) {
        }

        @Override
        public void recordToolCall(String runId, AgentToolCall toolCall, AgentObservation observation) {
        }

        @Override
        public void recordTaskPlan(String runId, TaskPlan plan, Throwable error) {
            if (error != null) {
                errors.add(error);
            } else if (plan != null) {
                plans.add(plan);
            }
        }
    }
}
