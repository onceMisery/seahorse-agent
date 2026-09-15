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

/**
 * 任务规划中单个步骤的推进状态。
 *
 * <p>状态由 {@code KernelAgentLoop} 在循环推进时回写，仅用于可观测性（trace / 运行步骤记录），
 * 不改变循环本身的分支与终止条件——规划失败或被跳过时循环仍按既有 ReAct 语义运行。
 */
public enum PlanStepStatus {

    /** 尚未开始。 */
    PENDING,
    /** 循环正在执行该步骤对应的轮次。 */
    IN_PROGRESS,
    /** 该步骤对应的轮次已成功产出结果。 */
    COMPLETED,
    /** 本轮无需执行（例如等待审批后提前返回）。 */
    SKIPPED,
    /** 该步骤对应的轮次抛出异常。 */
    FAILED;

    /**
     * @return 是否为终态（不再变化）
     */
    public boolean terminal() {
        return this == COMPLETED || this == SKIPPED || this == FAILED;
    }
}
