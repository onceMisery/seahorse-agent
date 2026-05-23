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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * 审批状态变更命令，Repository 必须按 fromStatus 做乐观状态更新。
 *
 * @param approvalId           审批 ID
 * @param fromStatus           期望原状态
 * @param toStatus             目标状态
 * @param decidedBy            审批人
 * @param decidedAt            审批时间
 * @param decisionComment      审批备注
 * @param argumentsPreviewJson 修改后的脱敏参数预览，仅 MODIFY 时使用
 */
public record ApprovalRequestDecision(String approvalId,
                                      ApprovalRequestStatus fromStatus,
                                      ApprovalRequestStatus toStatus,
                                      String decidedBy,
                                      Instant decidedAt,
                                      String decisionComment,
                                      String argumentsPreviewJson) {

    public ApprovalRequestDecision {
        approvalId = requireText(approvalId, "approvalId 不能为空");
        fromStatus = Objects.requireNonNull(fromStatus, "fromStatus 不能为空");
        toStatus = Objects.requireNonNull(toStatus, "toStatus 不能为空");
        decidedBy = requireText(decidedBy, "decidedBy 不能为空");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt 不能为空");
        decisionComment = trimToNull(decisionComment);
        argumentsPreviewJson = trimToNull(argumentsPreviewJson);
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

