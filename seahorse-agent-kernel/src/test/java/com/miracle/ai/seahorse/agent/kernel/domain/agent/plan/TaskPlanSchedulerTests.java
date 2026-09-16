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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPlanSchedulerTests {

    @Test
    void shouldReturnNoWavesWithoutAPlan() {
        assertTrue(TaskPlanScheduler.schedule(null).isEmpty());
        assertTrue(TaskPlanScheduler.schedule(TaskPlan.empty("目标", "test")).isEmpty());
    }

    @Test
    void shouldPutFullySequentialStepsIntoOneWaveEach() {
        TaskPlan plan = plan(
                PlanStep.pending(0, "a", "a", List.of()),
                PlanStep.pending(1, "b", "b", List.of("plan-step-1")),
                PlanStep.pending(2, "c", "c", List.of("plan-step-2")));

        List<PlanWave> waves = TaskPlanScheduler.schedule(plan);

        assertEquals(3, waves.size());
        assertEquals(List.of(0), waves.get(0).stepIndexes());
        assertEquals(List.of(1), waves.get(1).stepIndexes());
        assertEquals(List.of(2), waves.get(2).stepIndexes());
        waves.forEach(wave -> assertFalse(wave.parallelizable()));
    }

    @Test
    void shouldGroupStepsSharingTheSameDependencyIntoOneParallelWave() {
        // "同时" 这类并行标记会让两个步骤都继承同一步骤的依赖——这正是可并行的信号
        TaskPlan plan = plan(
                PlanStep.pending(0, "收集资料", "收集资料", List.of()),
                PlanStep.pending(1, "查官方文档", "查官方文档", List.of("plan-step-1")),
                PlanStep.pending(2, "同时查社区 benchmark", "同时查社区 benchmark", List.of("plan-step-1")),
                PlanStep.pending(3, "汇总", "汇总", List.of("plan-step-2", "plan-step-3")));

        List<PlanWave> waves = TaskPlanScheduler.schedule(plan);

        assertEquals(3, waves.size());
        assertEquals(List.of(0), waves.get(0).stepIndexes());
        assertEquals(List.of(1, 2), waves.get(1).stepIndexes());
        assertTrue(waves.get(1).parallelizable());
        assertEquals(List.of(3), waves.get(2).stepIndexes());
    }

    @Test
    void shouldTreatRootlessStepsAsOneWave() {
        TaskPlan plan = plan(
                PlanStep.pending(0, "a", "a", List.of()),
                PlanStep.pending(1, "b", "b", List.of()),
                PlanStep.pending(2, "c", "c", List.of()));

        List<PlanWave> waves = TaskPlanScheduler.schedule(plan);

        assertEquals(1, waves.size());
        assertEquals(List.of(0, 1, 2), waves.get(0).stepIndexes());
    }

    @Test
    void shouldIgnoreUnknownAndSelfDependencies() {
        TaskPlan plan = plan(
                PlanStep.pending(0, "a", "a", List.of("plan-step-99")),
                PlanStep.pending(1, "b", "b", List.of("plan-step-2")));

        List<PlanWave> waves = TaskPlanScheduler.schedule(plan);

        assertEquals(1, waves.size(), "未知依赖与自依赖都不应让步骤永远等不到执行");
        assertEquals(List.of(0, 1), waves.get(0).stepIndexes());
    }

    @Test
    void shouldDegradeToSequentialWavesWhenThereIsACycle() {
        TaskPlan plan = plan(
                PlanStep.pending(0, "a", "a", List.of("plan-step-2")),
                PlanStep.pending(1, "b", "b", List.of("plan-step-1")));

        List<PlanWave> waves = TaskPlanScheduler.schedule(plan);

        assertEquals(2, waves.size(), "成环时按原顺序拆成单步，保证调度一定终止");
        assertFalse(waves.get(0).parallelizable());
        assertFalse(waves.get(1).parallelizable());
        assertEquals(List.of(0), waves.get(0).stepIndexes());
        assertEquals(List.of(1), waves.get(1).stepIndexes());
    }

    @Test
    void shouldNumberWavesSequentially() {
        TaskPlan plan = plan(
                PlanStep.pending(0, "a", "a", List.of()),
                PlanStep.pending(1, "b", "b", List.of("plan-step-1")));

        List<PlanWave> waves = TaskPlanScheduler.schedule(plan);

        assertEquals(0, waves.get(0).index());
        assertEquals(1, waves.get(1).index());
    }

    private static TaskPlan plan(PlanStep... steps) {
        return TaskPlan.of("目标", "test", List.of(steps));
    }
}
