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

import java.util.Objects;

/**
 * 任务规划入参。
 *
 * <p>只携带规划所需的最小上下文：目标文本、身份字段与步数上限。刻意不传消息历史、
 * 工具清单等运行时对象——规划器应当是纯函数式的"目标 → 步骤"，便于单测与替换。
 *
 * @param goal     待规划的目标（通常是用户问题）
 * @param runId    运行标识，用于日志与 trace 关联
 * @param agentId  Agent 标识
 * @param tenantId 租户标识
 * @param userId   用户标识
 * @param maxSteps 步数上限，{@code <= 0} 表示由策略决定
 */
public record TaskPlanningRequest(
        String goal,
        String runId,
        String agentId,
        String tenantId,
        String userId,
        int maxSteps) {

    public TaskPlanningRequest {
        goal = Objects.requireNonNullElse(goal, "");
        maxSteps = Math.max(0, maxSteps);
    }

    public static TaskPlanningRequest of(String goal, String runId, int maxSteps) {
        return new TaskPlanningRequest(goal, runId, null, null, null, maxSteps);
    }
}
