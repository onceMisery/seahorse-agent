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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.PlanStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlan;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.plan.TaskPlanningRequest;
import com.miracle.ai.seahorse.agent.kernel.policy.TaskPlanningPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTaskPlannerTests {

    private static final String STRATEGY = DefaultTaskPlanner.STRATEGY;

    @Test
    void shouldSplitExplicitEnumerationIntoSequentialSteps() {
        TaskPlan plan = plan("1. 收集 Redis 资料 2. 撰写对比报告 3. 校对输出", TaskPlanningPolicy.defaults());

        assertEquals(3, plan.stepCount());
        assertEquals(STRATEGY, plan.strategy());
        assertEquals(List.of(), plan.stepAt(0).dependsOn());
        assertEquals(List.of("plan-step-1"), plan.stepAt(1).dependsOn());
        assertEquals(List.of("plan-step-2"), plan.stepAt(2).dependsOn());
        assertTrue(plan.stepAt(0).title().contains("收集 Redis 资料"));
        assertTrue(plan.stepAt(2).title().contains("校对输出"));
    }

    @Test
    void shouldSplitSequentialMarkersIntoSteps() {
        TaskPlan plan = plan("首先收集资料，然后撰写报告，最后校对输出", TaskPlanningPolicy.defaults());

        assertEquals(3, plan.stepCount());
        assertEquals("收集资料", plan.stepAt(0).title());
        assertEquals("撰写报告", plan.stepAt(1).title());
        assertEquals("校对输出", plan.stepAt(2).title());
    }

    @Test
    void shouldSplitLatinSequentialMarkersCaseInsensitively() {
        TaskPlan plan = plan("First collect data, then write the report, finally review it",
                TaskPlanningPolicy.defaults());

        assertEquals(3, plan.stepCount());
        assertEquals("collect data", plan.stepAt(0).title());
        assertEquals("write the report", plan.stepAt(1).title());
        assertEquals("review it", plan.stepAt(2).title());
    }

    @Test
    void shouldSplitChineseNumberedSteps() {
        TaskPlan plan = plan("第一步收集资料 第二步撰写报告 第三步校对输出", TaskPlanningPolicy.defaults());

        assertEquals(3, plan.stepCount());
        assertEquals("收集资料", plan.stepAt(0).title());
        assertEquals("校对输出", plan.stepAt(2).title());
    }

    @Test
    void shouldInheritDependencyForParallelSteps() {
        TaskPlan plan = plan("第一步收集资料。第二步同时整理表格。第三步汇总输出", TaskPlanningPolicy.defaults());

        assertEquals(3, plan.stepCount());
        // "同时"命中并行标记：与第 1 步并列，因此继承空依赖，而不是依赖第 1 步
        assertTrue(plan.stepAt(1).independent());
        assertEquals(List.of("plan-step-2"), plan.stepAt(2).dependsOn());
    }

    @Test
    void shouldFallBackToStrongSeparatorsWhenNoMarkersPresent() {
        TaskPlan plan = plan("收集 Redis 资料；撰写对比报告", TaskPlanningPolicy.defaults());

        assertEquals(2, plan.stepCount());
        assertEquals("收集 Redis 资料", plan.stepAt(0).title());
        assertEquals("撰写对比报告", plan.stepAt(1).title());
    }

    @Test
    void shouldReturnSingleStepWhenGoalIsNotDecomposable() {
        TaskPlan plan = plan("介绍一下 Redis 的核心数据结构", TaskPlanningPolicy.defaults());

        assertEquals(1, plan.stepCount());
        assertEquals(List.of(), plan.stepAt(0).dependsOn());
        assertEquals(PlanStepStatus.PENDING, plan.stepAt(0).status());
    }

    @Test
    void shouldNotSplitVersionLikeNumbersIntoSteps() {
        TaskPlan plan = plan("比较 Redis 7.0 与 6.2 的差异", TaskPlanningPolicy.defaults());

        assertEquals(1, plan.stepCount(), "版本号不应被当成编号切分");
    }

    @Test
    void shouldCapStepsAndMergeTailInsteadOfDroppingIt() {
        TaskPlan plan = plan("1. 收集资料 2. 撰写报告 3. 校对输出 4. 定稿 5. 发布",
                TaskPlanningPolicy.builder().maxSteps(3).build());

        assertEquals(3, plan.stepCount());
        String last = plan.stepAt(2).instruction();
        assertTrue(last.contains("校对输出"), last);
        assertTrue(last.contains("定稿"), last);
        assertTrue(last.contains("发布"), last);
    }

    @Test
    void shouldHonourRequestMaxStepsBelowPolicyLimit() {
        TaskPlan plan = new DefaultTaskPlanner(TaskPlanningPolicy.builder().maxSteps(5).build())
                .plan(TaskPlanningRequest.of("1. a 2. b 3. c 4. d", "run-1", 2));

        assertEquals(2, plan.stepCount());
        assertTrue(plan.stepAt(1).instruction().contains("b"));
        assertTrue(plan.stepAt(1).instruction().contains("d"));
    }

    @Test
    void shouldTruncateLongStepTitles() {
        String longSegment = "1. " + "x".repeat(200) + " 2. 收尾";
        TaskPlan plan = plan(longSegment, TaskPlanningPolicy.builder().maxStepTitleChars(20).build());

        assertEquals(2, plan.stepCount());
        assertTrue(plan.stepAt(0).title().endsWith("..."));
        assertEquals(23, plan.stepAt(0).title().length());
    }

    @Test
    void shouldReturnEmptyPlanForBlankGoal() {
        TaskPlan plan = plan("   ", TaskPlanningPolicy.defaults());

        assertEquals(0, plan.stepCount());
        assertTrue(plan.outline().isEmpty());
    }

    @Test
    void shouldReturnEmptyPlanForNullRequest() {
        TaskPlan plan = new DefaultTaskPlanner().plan(null);

        assertNotNull(plan);
        assertEquals(0, plan.stepCount());
    }

    @Test
    void shouldSupportCustomMarkerLists() {
        TaskPlanningPolicy policy = TaskPlanningPolicy.builder()
                .sequentialMarkers(List.of("阶段一", "阶段二"))
                .sequentialMarkersLatin(List.of())
                .build();

        TaskPlan plan = plan("阶段一收集资料 阶段二撰写报告", policy);

        assertEquals(2, plan.stepCount());
        assertEquals("收集资料", plan.stepAt(0).title());
    }

    @Test
    void shouldKeepEveryStepPendingUntilTheLoopAdvancesIt() {
        TaskPlan plan = plan("1. 收集 2. 撰写", TaskPlanningPolicy.defaults());

        for (PlanStep step : plan.steps()) {
            assertEquals(PlanStepStatus.PENDING, step.status());
        }
        assertFalse(plan.isComplete());
    }

    private static TaskPlan plan(String goal, TaskPlanningPolicy policy) {
        return new DefaultTaskPlanner(policy).plan(TaskPlanningRequest.of(goal, "run-1", 0));
    }
}
