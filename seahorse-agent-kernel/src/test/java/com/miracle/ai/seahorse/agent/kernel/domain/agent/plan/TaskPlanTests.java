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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPlanTests {

    @Test
    void statusChangeReturnsANewInstanceAndLeavesTheOriginalUntouched() {
        TaskPlan plan = TaskPlan.of("收集并汇总", "test", List.of(
                PlanStep.pending(0, "收集", "收集资料", List.of()),
                PlanStep.pending(1, "汇总", "汇总输出", List.of("plan-step-1"))));

        TaskPlan advanced = plan.withStepStatus(0, PlanStepStatus.COMPLETED);

        assertNotSame(plan, advanced);
        assertEquals(PlanStepStatus.PENDING, plan.stepAt(0).status());
        assertEquals(PlanStepStatus.COMPLETED, advanced.stepAt(0).status());
    }

    @Test
    void identicalStatusChangeReturnsTheSameInstance() {
        TaskPlan plan = TaskPlan.singleStep("收集", "test");

        assertSame(plan, plan.withStepStatus(0, PlanStepStatus.PENDING));
    }

    @Test
    void outOfRangeStatusChangeIsANoOp() {
        TaskPlan plan = TaskPlan.singleStep("收集", "test");

        assertSame(plan, plan.withStepStatus(9, PlanStepStatus.COMPLETED));
        assertSame(plan, plan.withStepStatus(-1, PlanStepStatus.COMPLETED));
    }

    @Test
    void outlineRendersStatusesAndDependencies() {
        TaskPlan plan = TaskPlan.of("目标", "test", List.of(
                PlanStep.pending(0, "收集", "收集资料", List.of()),
                PlanStep.pending(1, "汇总", "汇总输出", List.of("plan-step-1"))));
        TaskPlan advanced = plan.withStepStatus(0, PlanStepStatus.COMPLETED)
                .withStepStatus(1, PlanStepStatus.IN_PROGRESS);

        String outline = advanced.outline();

        assertTrue(outline.contains("# Task plan (2 steps)"), outline);
        assertTrue(outline.contains("[COMPLETED] 收集"), outline);
        assertTrue(outline.contains("[IN_PROGRESS] 汇总 (after: plan-step-1)"), outline);
    }

    @Test
    void outlineOfEmptyPlanIsBlank() {
        assertTrue(TaskPlan.empty("目标", "test").outline().isEmpty());
    }

    @Test
    void isCompleteOnlyWhenEveryStepIsDoneOrSkipped() {
        TaskPlan plan = TaskPlan.of("目标", "test", List.of(
                PlanStep.pending(0, "a", "a", List.of()),
                PlanStep.pending(1, "b", "b", List.of("plan-step-1"))));

        assertFalse(plan.isComplete());
        assertFalse(plan.withStepStatus(0, PlanStepStatus.COMPLETED).isComplete());
        assertTrue(plan.withStepStatus(0, PlanStepStatus.COMPLETED)
                .withStepStatus(1, PlanStepStatus.SKIPPED)
                .isComplete());
        assertFalse(plan.withStepStatus(0, PlanStepStatus.FAILED).isComplete());
    }

    @Test
    void completedStepCountCountsOnlyCompletedSteps() {
        TaskPlan plan = TaskPlan.of("目标", "test", List.of(
                PlanStep.pending(0, "a", "a", List.of()),
                PlanStep.pending(1, "b", "b", List.of("plan-step-1"))));

        assertEquals(0, plan.completedStepCount());
        assertEquals(1, plan.withStepStatus(0, PlanStepStatus.COMPLETED).completedStepCount());
    }

    /**
     * toJson 供 trace 与运行步骤记录使用，内容可能落到外部系统，
     * 因此不能夹带用户输入（这里是手写拼装、没有转义）。
     */
    @Test
    void toJsonExcludesUserContent() {
        TaskPlan plan = TaskPlan.of("机密目标 \"quoted\" \\ backslash", "test",
                List.of(PlanStep.pending(0, "步骤", "步骤正文", List.of())));

        String json = plan.toJson();

        assertTrue(json.contains("\"stepCount\":1"), json);
        assertTrue(json.contains("\"strategy\":\"test\""), json);
        assertFalse(json.contains("机密目标"), json);
        assertFalse(json.contains("quoted"), json);
        assertFalse(json.contains("backslash"), json);
    }
}
