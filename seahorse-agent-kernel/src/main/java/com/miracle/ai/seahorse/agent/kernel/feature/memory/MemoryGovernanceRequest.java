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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 记忆治理请求。
 * <p>
 * 该请求描述一次治理任务的边界，避免治理 Feature 直接读取调度器或数据库上下文。
 *
 * @param tenantId       租户 ID
 * @param conversationId 会话 ID
 * @param reason         治理触发原因
 * @param triggeredAt    触发时间
 * @param attributes     额外属性
 */
public record MemoryGovernanceRequest(
        String tenantId,
        String conversationId,
        String reason,
        Instant triggeredAt,
        Map<String, Object> attributes
) {

    /**
     * 构造不可变治理请求。
     */
    public MemoryGovernanceRequest {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        conversationId = Objects.requireNonNullElse(conversationId, "");
        reason = Objects.requireNonNullElse(reason, "");
        triggeredAt = Objects.requireNonNullElse(triggeredAt, Instant.EPOCH);
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
    }
}
