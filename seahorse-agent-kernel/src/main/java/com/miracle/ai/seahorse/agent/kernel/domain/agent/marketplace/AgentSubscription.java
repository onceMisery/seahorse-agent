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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace;

import java.time.Instant;
import java.util.Objects;

/**
 * Agent 订阅记录。
 *
 * @param id           订阅记录主键
 * @param agentId      Agent ID
 * @param userId       用户 ID
 * @param tenantId     租户 ID
 * @param subscribedAt 订阅时间
 * @param active       是否有效
 */
public record AgentSubscription(Long id,
                                String agentId,
                                Long userId,
                                String tenantId,
                                Instant subscribedAt,
                                boolean active) {

    public AgentSubscription {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        subscribedAt = Objects.requireNonNull(subscribedAt, "subscribedAt 不能为空");
    }

    /**
     * 取消订阅。
     */
    public AgentSubscription cancel() {
        return new AgentSubscription(id, agentId, userId, tenantId, subscribedAt, false);
    }

    /**
     * 重新激活订阅。
     */
    public AgentSubscription activate(Instant now) {
        return new AgentSubscription(id, agentId, userId, tenantId, now, true);
    }
}
