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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Spec §10 checkpoint 复跑 diff 决策结果。
 *
 * <p>三档决策：
 * <ul>
 *     <li>{@link Decision#UNCHANGED}：跳过本 phase 重算。</li>
 *     <li>{@link Decision#SOFT_PATCH}：仅重算当前 artifact 摘要，不级联下游。</li>
 *     <li>{@link Decision#HARD_CASCADE}：依赖字段或上游变更，必须级联重算。</li>
 * </ul>
 *
 * @param decision        决策档位
 * @param changedFields   触发决策的输入字段；UNCHANGED 时为空集
 * @param reason          人类可读 hint，用于日志 / trace（不参与决策语义）
 */
public record AgentCheckpointDiff(Decision decision, Set<String> changedFields, String reason) {

    public AgentCheckpointDiff {
        decision = Objects.requireNonNull(decision, "decision must not be null");
        changedFields = changedFields == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(changedFields));
        reason = reason == null ? "" : reason.trim();
    }

    public static AgentCheckpointDiff unchanged() {
        return new AgentCheckpointDiff(Decision.UNCHANGED, Set.of(), "no input or artifact change");
    }

    public static AgentCheckpointDiff softPatch(Set<String> changedFields, String reason) {
        return new AgentCheckpointDiff(Decision.SOFT_PATCH, changedFields, reason);
    }

    public static AgentCheckpointDiff hardCascade(Set<String> changedFields, String reason) {
        return new AgentCheckpointDiff(Decision.HARD_CASCADE, changedFields, reason);
    }

    public enum Decision {
        /** 输入 & artifact 均未变化。 */
        UNCHANGED,
        /** 仅当前 artifact 受影响，无需级联下游。 */
        SOFT_PATCH,
        /** 上游依赖或关键契约变化，必须级联重算。 */
        HARD_CASCADE
    }
}
