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
 * Agent 评分评论。
 *
 * @param id        评分记录主键
 * @param agentId   Agent ID
 * @param userId    用户 ID
 * @param rating    评分（1-5）
 * @param comment   评论内容
 * @param createdAt 创建时间
 */
public record AgentRating(Long id,
                          String agentId,
                          Long userId,
                          int rating,
                          String comment,
                          Instant createdAt) {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    public AgentRating {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("评分必须在 " + MIN_RATING + "-" + MAX_RATING + " 之间");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        comment = comment == null ? "" : comment;
    }

    /**
     * 更新评分。
     */
    public AgentRating update(int newRating, String newComment, Instant now) {
        return new AgentRating(id, agentId, userId, newRating, newComment, now);
    }
}
