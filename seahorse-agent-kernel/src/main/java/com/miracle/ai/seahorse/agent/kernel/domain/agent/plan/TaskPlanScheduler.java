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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 把 {@link TaskPlan} 按 {@code dependsOn} 推导成可执行的波次序列。
 *
 * <p>规则：一个步骤的层级 = {@code 1 + max(所有前置步骤的层级)}；没有前置的步骤处于第 0 层。
 * 同一层内的步骤互相之间没有依赖路径，因此可以并发执行。这正是
 * {@link DefaultTaskPlanner} 里"命中并行标记的步骤继承前一步依赖"想表达的含义——
 * 继承同一依赖的两个步骤会被放到同一波次。
 *
 * <p>两条降级保证，都是为了"调度器绝不能让循环跑不起来"：
 * <ul>
 *   <li>依赖 id 未知或自依赖：直接忽略该条依赖，不因此阻塞；</li>
 *   <li>存在环（不应该发生，但解析来自模型输出时必须防御）：环上的步骤按原顺序拆成
 *       单步波次追加在末尾，保证拓扑排序一定终止。</li>
 * </ul>
 *
 * <p>纯函数，无状态、无 IO，可直接在单测里断言。
 */
public final class TaskPlanScheduler {

    private TaskPlanScheduler() {
    }

    /**
     * @param plan 任务规划；为 {@code null} 或没有步骤时返回空列表（调用方按"逐步串行"处理）
     * @return 按层级升序排列的波次，每波内的步骤下标升序
     */
    public static List<PlanWave> schedule(TaskPlan plan) {
        if (plan == null || plan.stepCount() == 0) {
            return List.of();
        }
        List<PlanStep> steps = plan.steps();
        int count = steps.size();
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < count; i++) {
            indexById.putIfAbsent(steps.get(i).id(), i);
        }
        int[] levels = resolveLevels(steps, indexById);
        return group(levels);
    }

    private static int[] resolveLevels(List<PlanStep> steps, Map<String, Integer> indexById) {
        int count = steps.size();
        int[] levels = new int[count];
        for (int i = 0; i < count; i++) {
            levels[i] = -1;
        }
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (int i = 0; i < count; i++) {
                if (levels[i] >= 0) {
                    continue;
                }
                int maxDependencyLevel = -1;
                boolean ready = true;
                for (String dependency : steps.get(i).dependsOn()) {
                    Integer dependencyIndex = indexById.get(dependency);
                    if (dependencyIndex == null || dependencyIndex == i) {
                        continue;
                    }
                    if (levels[dependencyIndex] < 0) {
                        ready = false;
                        break;
                    }
                    maxDependencyLevel = Math.max(maxDependencyLevel, levels[dependencyIndex]);
                }
                if (ready) {
                    levels[i] = maxDependencyLevel + 1;
                    progressed = true;
                }
            }
        }
        appendCyclicSteps(levels);
        return levels;
    }

    /**
     * 环上的步骤永远等不到前置，这里按原顺序拆成单步追加在末尾。
     * 结果是"退化成串行"而不是"调度不出来"，循环的步数上限仍然兜底。
     */
    private static void appendCyclicSteps(int[] levels) {
        int maxLevel = -1;
        for (int level : levels) {
            maxLevel = Math.max(maxLevel, level);
        }
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] < 0) {
                levels[i] = ++maxLevel;
            }
        }
    }

    private static List<PlanWave> group(int[] levels) {
        TreeMap<Integer, List<Integer>> byLevel = new TreeMap<>();
        for (int i = 0; i < levels.length; i++) {
            byLevel.computeIfAbsent(levels[i], key -> new ArrayList<>()).add(i);
        }
        List<PlanWave> waves = new ArrayList<>(byLevel.size());
        int waveIndex = 0;
        for (Map.Entry<Integer, List<Integer>> entry : byLevel.entrySet()) {
            waves.add(new PlanWave(waveIndex++, List.copyOf(entry.getValue())));
        }
        return List.copyOf(waves);
    }
}
