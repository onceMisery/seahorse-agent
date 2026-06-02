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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 防止记忆细化闭环：限制细化链的最大深度。
 *
 * <p>每条由 LLM 细化产生的记忆会在其 {@code metadata} 中携带
 * {@link #METADATA_REFINEMENT_DEPTH}（整数，0 表示原始用户输入，1 表示一级细化，以此类推）。
 * 当现有记忆中的最大深度已经达到配置上限时，本守卫会阻止进一步的细化调用，
 * 从而避免记忆信息逐步漂移、表膨胀和 Token 浪费。
 *
 * <p>设计要点：
 * <ul>
 *     <li>深度通过 metadata 传递，不修改任何端口接口签名。</li>
 *     <li>{@link #exceedsMaxDepth(List)} 在调用 refiner 之前使用；
 *     {@link #incrementedMetadata(int)} 在写入细化产物时使用。</li>
 * </ul>
 */
public final class MemoryRefinementDepthGuard {

    /** metadata key：记录当前记忆的细化代数（0 = 原始，1 = 一级细化，…）。 */
    static final String METADATA_REFINEMENT_DEPTH = "refinementDepth";

    private final int maxDepth;

    public MemoryRefinementDepthGuard(int maxDepth) {
        if (maxDepth <= 0) {
            this.maxDepth = MemoryEngineOptions.DEFAULT_MAX_REFINEMENT_DEPTH;
        } else {
            this.maxDepth = maxDepth;
        }
    }

    /**
     * 判断现有记忆中的最大细化深度是否已经达到上限。
     *
     * @param existingMemories 即将作为细化输入的记忆列表
     * @return true 表示应阻止进一步细化
     */
    public boolean exceedsMaxDepth(List<MemoryRefinementMemory> existingMemories) {
        return currentMaxDepth(existingMemories) >= maxDepth;
    }

    /**
     * 计算现有记忆中的最大细化深度。
     */
    public int currentMaxDepth(List<MemoryRefinementMemory> existingMemories) {
        if (existingMemories == null || existingMemories.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (MemoryRefinementMemory memory : existingMemories) {
            int depth = depthOf(memory);
            if (depth > max) {
                max = depth;
            }
        }
        return max;
    }

    /**
     * 返回包含递增后 refinementDepth 的新 metadata 副本。
     *
     * @param currentDepth 当前细化深度
     * @return 包含 refinementDepth = currentDepth + 1 的不可变 Map
     */
    public static Map<String, Object> incrementedMetadata(int currentDepth) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_REFINEMENT_DEPTH, currentDepth + 1);
        return Map.copyOf(metadata);
    }

    /**
     * 返回包含递增后 refinementDepth 的新 metadata（合并到已有 metadata）。
     *
     * @param existingMetadata 已有 metadata
     * @return 包含更新后 refinementDepth 的不可变 Map
     */
    public static Map<String, Object> incrementedMetadata(Map<String, Object> existingMetadata) {
        int depth = depthFromMetadata(existingMetadata);
        Map<String, Object> metadata = new LinkedHashMap<>(existingMetadata == null ? Map.of() : existingMetadata);
        metadata.put(METADATA_REFINEMENT_DEPTH, depth + 1);
        return Map.copyOf(metadata);
    }

    private static int depthOf(MemoryRefinementMemory memory) {
        if (memory == null) {
            return 0;
        }
        return depthFromMetadata(memory.metadata());
    }

    private static int depthFromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return 0;
        }
        Object value = metadata.get(METADATA_REFINEMENT_DEPTH);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
