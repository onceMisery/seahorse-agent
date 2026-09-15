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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一次运行的任务规划结果。
 *
 * <p>不可变：推进状态通过 {@link #withStepStatus(int, PlanStepStatus)} 返回新实例。
 * 这样循环里持有的引用始终是某个时刻的完整快照，记录到 trace / 运行步骤时不会出现
 * "读到一半被改掉"的中间态。
 *
 * @param planId    规划实例标识，仅用于日志与 trace 关联
 * @param goal      被规划的目标原文
 * @param strategy  产出该规划的规划器标识（例如 {@code heuristic-decomposition} 或未来的 LLM 规划器）
 * @param steps     步骤列表
 * @param createdAt 规划生成时间
 */
public record TaskPlan(
        String planId,
        String goal,
        String strategy,
        List<PlanStep> steps,
        Instant createdAt) {

    public TaskPlan {
        planId = Objects.requireNonNullElse(planId, newPlanId());
        goal = Objects.requireNonNullElse(goal, "");
        strategy = Objects.requireNonNullElse(strategy, "unknown");
        steps = steps == null ? List.of() : List.copyOf(steps);
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
    }

    public static String newPlanId() {
        return "plan-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static TaskPlan of(String goal, String strategy, List<PlanStep> steps) {
        return new TaskPlan(newPlanId(), goal, strategy, steps, Instant.now());
    }

    /**
     * @return 只有一个步骤的规划，用于"目标无法进一步分解"的场景
     */
    public static TaskPlan singleStep(String goal, String strategy) {
        return of(goal, strategy, List.of(PlanStep.pending(0, truncateTitle(goal, 80), goal, List.of())));
    }

    /**
     * @return 空规划，用于目标为空或规划器判定无需规划
     */
    public static TaskPlan empty(String goal, String strategy) {
        return of(goal, strategy, List.of());
    }

    public int stepCount() {
        return steps.size();
    }

    /**
     * @return 指定下标的步骤，越界返回 {@code null}（调用方按"没有对应步骤"处理）
     */
    public PlanStep stepAt(int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : null;
    }

    /**
     * @return 把指定步骤置为新状态后的副本；下标越界或状态未变时返回自身
     */
    public TaskPlan withStepStatus(int index, PlanStepStatus status) {
        PlanStep current = stepAt(index);
        if (current == null || current.status() == status) {
            return this;
        }
        List<PlanStep> updated = new ArrayList<>(steps);
        updated.set(index, current.withStatus(status));
        return new TaskPlan(planId, goal, strategy, List.copyOf(updated), createdAt);
    }

    /**
     * @return 已完成的步骤数
     */
    public int completedStepCount() {
        int count = 0;
        for (PlanStep step : steps) {
            if (step.status() == PlanStepStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return 所有步骤都已完成或跳过
     */
    public boolean isComplete() {
        if (steps.isEmpty()) {
            return false;
        }
        for (PlanStep step : steps) {
            if (step.status() != PlanStepStatus.COMPLETED && step.status() != PlanStepStatus.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 渲染注入提示词的规划大纲。
     *
     * <p>只使用步骤标题（规划器已截断），不回灌目标全文，避免把长目标重复塞进上下文。
     */
    public String outline() {
        if (steps.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("# Task plan (")
                .append(steps.size())
                .append(" steps)\n");
        for (PlanStep step : steps) {
            builder.append("- [")
                    .append(step.status().name())
                    .append("] ")
                    .append(step.title().isBlank() ? "(untitled step)" : step.title());
            if (!step.dependsOn().isEmpty()) {
                builder.append(" (after: ").append(String.join(", ", step.dependsOn())).append(")");
            }
            builder.append('\n');
        }
        builder.append("Work through the steps in order; a step is done only when its outcome is already")
                .append(" present in the conversation.");
        return builder.toString();
    }

    /**
     * 可观测性用的紧凑 JSON。
     *
     * <p>刻意不写入 {@code goal} 与步骤正文：它们来自用户输入，长度与内容都不可控，
     * 而这里是手写拼装、没有转义。只输出规模可控的结构信息即可满足排障需要。
     */
    public String toJson() {
        StringBuilder statuses = new StringBuilder("[");
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                statuses.append(',');
            }
            statuses.append('"').append(steps.get(i).status().name()).append('"');
        }
        statuses.append(']');
        return "{\"planId\":\"" + planId
                + "\",\"strategy\":\"" + strategy
                + "\",\"stepCount\":" + steps.size()
                + "\",\"completedStepCount\":" + completedStepCount()
                + ",\"statuses\":" + statuses
                + ",\"createdAtEpochMillis\":" + createdAt.toEpochMilli()
                + "}";
    }

    private static String truncateTitle(String value, int maxChars) {
        String safe = Objects.requireNonNullElse(value, "").trim();
        return safe.length() <= maxChars ? safe : safe.substring(0, maxChars) + "...";
    }
}
