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

package com.miracle.ai.seahorse.agent.kernel.feature.memory;

import java.util.Map;
import java.util.Objects;

/**
 * 记忆治理结果。
 * <p>
 * 结果记录治理影响范围，便于追踪晋升、衰减、冲突检测和质量快照行为。
 *
 * @param featureName      Feature 名称
 * @param success          是否成功
 * @param affectedCount    影响的记忆数量
 * @param message          结果说明
 * @param metrics          额外指标
 */
public record MemoryGovernanceResult(
        String featureName,
        boolean success,
        int affectedCount,
        String message,
        Map<String, Object> metrics
) {

    /**
     * 构造不可变治理结果。
     */
    public MemoryGovernanceResult {
        featureName = Objects.requireNonNullElse(featureName, "");
        message = Objects.requireNonNullElse(message, "");
        metrics = Map.copyOf(Objects.requireNonNullElse(metrics, Map.of()));
    }
}
