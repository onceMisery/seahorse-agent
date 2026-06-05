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

package com.miracle.ai.seahorse.agent.kernel.domain.knowledge;

import java.time.Instant;
import java.util.Objects;

/**
 * 知识库外部分享链接，支持密码保护和访问次数限制。
 *
 * @param id                 分享记录主键
 * @param kbId               知识库 ID
 * @param tenantId           租户 ID
 * @param shareToken         分享令牌（UUID）
 * @param passwordHash       密码哈希（可选，null 表示无密码）
 * @param expiresAt          过期时间
 * @param maxAccessCount     最大访问次数（0 表示不限制）
 * @param currentAccessCount 当前访问次数
 * @param createdAt          创建时间
 */
public record KnowledgeBaseShare(Long id,
                                 Long kbId,
                                 String tenantId,
                                 String shareToken,
                                 String passwordHash,
                                 Instant expiresAt,
                                 int maxAccessCount,
                                 int currentAccessCount,
                                 Instant createdAt) {

    public KnowledgeBaseShare {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (shareToken == null || shareToken.isBlank()) {
            throw new IllegalArgumentException("shareToken 不能为空");
        }
        if (maxAccessCount < 0) {
            throw new IllegalArgumentException("maxAccessCount 不能为负数");
        }
        if (currentAccessCount < 0) {
            throw new IllegalArgumentException("currentAccessCount 不能为负数");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    /**
     * 是否已过期。
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /**
     * 是否已达到访问次数上限。
     */
    public boolean isAccessLimitReached() {
        return maxAccessCount > 0 && currentAccessCount >= maxAccessCount;
    }

    /**
     * 分享链接是否有效（未过期且未达到访问上限）。
     */
    public boolean isActive() {
        return !isExpired(Instant.now()) && !isAccessLimitReached();
    }

    /**
     * 增加访问次数。
     */
    public KnowledgeBaseShare incrementAccess() {
        return new KnowledgeBaseShare(
                id, kbId, tenantId, shareToken, passwordHash,
                expiresAt, maxAccessCount, currentAccessCount + 1, createdAt
        );
    }
}
