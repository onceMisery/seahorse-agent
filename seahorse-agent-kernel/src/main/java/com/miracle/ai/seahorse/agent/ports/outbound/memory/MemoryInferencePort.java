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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.InferredMemory;

import java.util.List;

/**
 * 记忆推理端口。
 *
 * <p>从用户近期短期记忆和已有语义记忆中推理出新的长期/语义记忆。
 * 该操作由 {@code KernelMemoryGovernanceService} 在显式配置开启时调用。
 */
public interface MemoryInferencePort {

    /**
     * 从短期记忆中推理新的长期/语义记忆。
     *
     * @param userId           用户 ID
     * @param shortTermMemories 近期短期记忆
     * @param semanticMemories  已有语义记忆（用于冲突检测）
     * @return 推理出的新记忆列表
     */
    List<InferredMemory> infer(String userId,
                               List<MemoryRecord> shortTermMemories,
                               List<MemoryRecord> semanticMemories);

    static MemoryInferencePort noop() {
        return (userId, shortTerm, semantic) -> List.of();
    }
}
