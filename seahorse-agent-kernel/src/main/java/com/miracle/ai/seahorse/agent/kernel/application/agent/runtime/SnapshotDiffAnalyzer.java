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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointDiff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointSnapshot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Spec §10 Phase C 复跑：基于 hash + 依赖字段提示的轻量 checkpoint diff。
 *
 * <p>本分析器纯函数、不访问外部资源、不调 LLM；保证决策可预测且可单测。
 *
 * <p>决策表（spec §10.2）：
 * <ol>
 *     <li>previous == {@code null} → HARD_CASCADE（首跑）。</li>
 *     <li>input & artifact hash 均未变 → UNCHANGED。</li>
 *     <li>input 未变、artifact 变 → SOFT_PATCH。</li>
 *     <li>input 仅触及非依赖字段（如 user trace metadata）→ SOFT_PATCH。</li>
 *     <li>input 触及依赖字段（上游 phase、agent definition、allowed tools、output contract）
 *         → HARD_CASCADE。</li>
 *     <li>依赖字段无法判断 → HARD_CASCADE（保守）。</li>
 * </ol>
 */
public final class SnapshotDiffAnalyzer {

    /**
     * 默认依赖字段集合：上游 phase、agent definition、allowed tools、output contract。
     */
    public static final Set<String> DEFAULT_DEPENDENCY_FIELDS = Set.of(
            "upstreamPhase",
            "agentDefinition",
            "allowedTools",
            "outputContract");

    private final Set<String> dependencyFields;

    public SnapshotDiffAnalyzer() {
        this(DEFAULT_DEPENDENCY_FIELDS);
    }

    public SnapshotDiffAnalyzer(Set<String> dependencyFields) {
        this.dependencyFields = dependencyFields == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(dependencyFields));
    }

    /**
     * 比较先后两个 checkpoint snapshot，给出 diff 决策。
     *
     * @param previous 上次成功 checkpoint 的 snapshot；{@code null} 表示首跑
     * @param current  当前待处理 snapshot；不可为 {@code null}
     */
    public AgentCheckpointDiff analyze(AgentCheckpointSnapshot previous, AgentCheckpointSnapshot current) {
        Objects.requireNonNull(current, "current snapshot must not be null");
        if (previous == null) {
            return AgentCheckpointDiff.hardCascade(Set.of(), "previous checkpoint missing");
        }
        Set<String> changedInputs = previous.changedInputFields(current);
        boolean artifactChanged = previous.artifactChanged(current);
        if (changedInputs.isEmpty() && !artifactChanged) {
            return AgentCheckpointDiff.unchanged();
        }
        if (changedInputs.isEmpty()) {
            return AgentCheckpointDiff.softPatch(Set.of(), "artifact hash changed, inputs unchanged");
        }
        Set<String> changedDependencies = intersect(changedInputs, dependencyFields);
        if (!changedDependencies.isEmpty()) {
            return AgentCheckpointDiff.hardCascade(changedDependencies,
                    "dependency input fields changed: " + changedDependencies);
        }
        return AgentCheckpointDiff.softPatch(changedInputs,
                "non-dependency input fields changed: " + changedInputs);
    }

    private static Set<String> intersect(Set<String> changed, Set<String> dependency) {
        Set<String> result = new LinkedHashSet<>();
        for (String field : changed) {
            if (dependency.contains(field)) {
                result.add(field);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
