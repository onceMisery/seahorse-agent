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
 * Agent 发布审核记录。
 *
 * @param id            审核记录主键
 * @param agentId       Agent ID
 * @param tenantId      租户 ID
 * @param submittedBy   提交人
 * @param status        审核状态：PENDING/APPROVED/REJECTED
 * @param reviewComment 审核意见
 * @param reviewedBy    审核人
 * @param submittedAt   提交时间
 * @param reviewedAt    审核时间
 */
public record AgentPublishReview(Long id,
                                 String agentId,
                                 String tenantId,
                                 String submittedBy,
                                 String status,
                                 String reviewComment,
                                 String reviewedBy,
                                 Instant submittedAt,
                                 Instant reviewedAt) {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public AgentPublishReview {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        if (submittedBy == null || submittedBy.isBlank()) {
            throw new IllegalArgumentException("submittedBy 不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (!PENDING.equals(status) && !APPROVED.equals(status) && !REJECTED.equals(status)) {
            throw new IllegalArgumentException("无效的审核状态：" + status);
        }
        submittedAt = Objects.requireNonNull(submittedAt, "submittedAt 不能为空");
    }

    /**
     * 批准发布。
     */
    public AgentPublishReview approve(String reviewer, String comment, Instant now) {
        return new AgentPublishReview(
                id, agentId, tenantId, submittedBy, APPROVED, comment, reviewer, submittedAt, now
        );
    }

    /**
     * 拒绝发布。
     */
    public AgentPublishReview reject(String reviewer, String comment, Instant now) {
        return new AgentPublishReview(
                id, agentId, tenantId, submittedBy, REJECTED, comment, reviewer, submittedAt, now
        );
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }
}
