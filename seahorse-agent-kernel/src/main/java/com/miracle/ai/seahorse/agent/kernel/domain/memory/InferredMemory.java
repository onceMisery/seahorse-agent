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

package com.miracle.ai.seahorse.agent.kernel.domain.memory;

import java.util.List;
import java.util.Objects;

/**
 * 推理出的新记忆。
 *
 * @param targetLayer  目标记忆层（long_term 或 semantic）
 * @param semanticKey  语义记忆的唯一键（semantic 层必须提供）
 * @param type         记忆类型（PROFILE / PREFERENCE / KNOWLEDGE / FACT）
 * @param content      推理出的内容
 * @param confidence   推理置信度（0-1）
 * @param sourceIds    来源短期记忆 ID 列表
 * @param reasoning    推理过程说明
 */
public record InferredMemory(
        String targetLayer,
        String semanticKey,
        String type,
        String content,
        double confidence,
        List<String> sourceIds,
        String reasoning
) {

    public InferredMemory {
        targetLayer = Objects.requireNonNullElse(targetLayer, "long_term");
        semanticKey = Objects.requireNonNullElse(semanticKey, "");
        type = Objects.requireNonNullElse(type, "FACT");
        content = Objects.requireNonNullElse(content, "");
        sourceIds = List.copyOf(Objects.requireNonNullElse(sourceIds, List.of()));
        reasoning = Objects.requireNonNullElse(reasoning, "");
    }
}
