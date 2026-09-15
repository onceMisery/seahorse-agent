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

import java.util.List;
import java.util.Objects;

/**
 * 任务规划中的单个步骤。
 *
 * <p>不可变。{@code dependsOn} 保存前置步骤 id，用来表达"哪些步骤可以并行"：
 * 依赖同一步骤的两个步骤之间没有先后约束，因此可以被并行调度；本次最小切片只用它做
 * 依赖表达与提示词渲染，真正的并行执行留给后续迭代。
 *
 * @param id          步骤标识，形如 {@code plan-step-1}
 * @param index       步骤序号，从 0 开始
 * @param title       简短标题，用于提示词与日志
 * @param instruction 步骤的完整表述，取自目标原文的一段
 * @param dependsOn   前置步骤 id 列表，为空表示可直接开始
 * @param status      推进状态
 */
public record PlanStep(
        String id,
        int index,
        String title,
        String instruction,
        List<String> dependsOn,
        PlanStepStatus status) {

    public PlanStep {
        Objects.requireNonNull(id, "PlanStep.id 不能为 null");
        if (index < 0) {
            throw new IllegalArgumentException("PlanStep.index 不能为负数");
        }
        title = Objects.requireNonNullElse(title, "");
        instruction = Objects.requireNonNullElse(instruction, "");
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        status = Objects.requireNonNullElse(status, PlanStepStatus.PENDING);
    }

    /**
     * 创建一个 {@link PlanStepStatus#PENDING} 的步骤，id 由序号推导。
     */
    public static PlanStep pending(int index, String title, String instruction, List<String> dependsOn) {
        return new PlanStep(stepId(index), index, title, instruction, dependsOn, PlanStepStatus.PENDING);
    }

    public static String stepId(int index) {
        return "plan-step-" + (index + 1);
    }

    /**
     * @return 状态更新后的副本；状态相同则直接返回自身，避免产生无意义的新对象
     */
    public PlanStep withStatus(PlanStepStatus newStatus) {
        PlanStepStatus safe = Objects.requireNonNull(newStatus, "status 不能为 null");
        return safe == status ? this : new PlanStep(id, index, title, instruction, dependsOn, safe);
    }

    /**
     * @return 是否可以与其它步骤并行（无前置依赖）
     */
    public boolean independent() {
        return dependsOn.isEmpty();
    }
}
