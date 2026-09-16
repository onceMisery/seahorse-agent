/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.bench;

/**
 * 一条 AgentLoop 基准用例。
 *
 * <p><b>为什么用脚本化用例而不是真实模型。</b>本 harness 的目的是在四个行为开关之间做
 * <i>配对</i>比较：同一条用例在开/关两臂上跑，差异只能来自开关本身。真实模型会带来
 * 采样方差，30 条样本根本压不住；脚本桩是确定性的，方差只来自用例集本身。
 *
 * <p>代价必须说清楚：脚本桩对任意提示词都返回固定内容，因此<b>它测不出 LLM 规划器的
 * 语义收益</b>，也测不出并行带来的真实延迟收益（桩没有网络等待）。这两项只能验管道
 * 正确性，真实收益需要另起一段用真实模型评估。
 *
 * @param caseId    用例标识
 * @param goal      目标描述；带编号或顺序标记时会被确定性规划器分解
 * @param maxSteps  该用例的推理预算
 * @param workTurns 脚本桩在给出最终答案之前要产出多少个"带工具调用"的轮次，
 *                  用于控制该用例是否会触及步数上限而被截断
 */
public record AgentLoopBenchmarkCase(String caseId, String goal, int maxSteps, int workTurns) {

    public AgentLoopBenchmarkCase {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must not be blank: " + caseId);
        }
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be >= 1: " + caseId);
        }
        if (workTurns < 0) {
            throw new IllegalArgumentException("workTurns must be >= 0: " + caseId);
        }
    }

    /**
     * 该用例在给定预算下是否注定被截断——脚本桩要产出 {@code workTurns} 个工具轮次
     * 外加一个最终答案轮次，超过预算就会被截断。
     */
    boolean expectedToTruncate() {
        return workTurns + 1 > maxSteps;
    }
}
