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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Spec §10：Phase C 复跑 checkpoint diff 的轻量快照表示。
 *
 * <p>仅承载 hash + 依赖字段名集合，不保留原始 payload，避免 analyzer 调用 LLM 或访问外部资源。
 * 调用方（resume / checkpoint 服务）负责将 {@link AgentCheckpoint} 物理结构折算为 hash。
 *
 * @param inputFieldHashes 输入字段哈希表（key 为字段名、value 为该字段 stable hash；
 *                         对 null / 空值约定使用 {@code ""} 占位以区分"未提供"和"已知为空"）
 * @param artifactHash     当前 phase artifact 的 hash；首次执行可为 {@code null}
 */
public record AgentCheckpointSnapshot(Map<String, String> inputFieldHashes, String artifactHash) {

    public AgentCheckpointSnapshot {
        inputFieldHashes = inputFieldHashes == null
                ? Map.of()
                : Map.copyOf(inputFieldHashes);
        artifactHash = (artifactHash == null || artifactHash.isBlank()) ? null : artifactHash.trim();
    }

    /**
     * 返回与 {@code other} 相比 hash 发生变化（或仅一侧出现）的字段名集合。
     */
    public Set<String> changedInputFields(AgentCheckpointSnapshot other) {
        Objects.requireNonNull(other, "other must not be null");
        Set<String> changed = new LinkedHashSet<>();
        Set<String> union = new LinkedHashSet<>(this.inputFieldHashes.keySet());
        union.addAll(other.inputFieldHashes.keySet());
        for (String key : union) {
            String left = this.inputFieldHashes.get(key);
            String right = other.inputFieldHashes.get(key);
            if (!Objects.equals(left, right)) {
                changed.add(key);
            }
        }
        return Collections.unmodifiableSet(changed);
    }

    public boolean artifactChanged(AgentCheckpointSnapshot other) {
        return !Objects.equals(this.artifactHash, other.artifactHash);
    }
}
