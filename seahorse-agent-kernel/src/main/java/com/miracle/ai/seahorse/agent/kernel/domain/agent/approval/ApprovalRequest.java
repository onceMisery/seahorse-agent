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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.approval;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;

import java.time.Instant;
import java.util.Objects;

/**
 * 人工审批请求，是 Tool Gateway 在高风险工具调用被中断时写出的可恢复运行时证据。
 *
 * @param approvalId          审批请求 ID
 * @param runId               AgentRun ID，用于恢复运行时定位原始执行上下文
 * @param stepId              AgentStep ID，用于定位触发审批的执行步骤
 * @param toolInvocationId    工具调用审计 ID，用于串联 requested、decision、completion 和审批记录
 * @param tenantId            租户 ID，用于租户隔离和审批队列过滤
 * @param userId              最终用户 ID，用于展示审批发起人和权限上下文
 * @param agentId             Agent 定义 ID
 * @param toolId              被拦截的工具 ID
 * @param approvalType        审批类型
 * @param riskLevel           风险等级，当前 Gateway 对审批请求采用保守默认值
 * @param summary             审批摘要，供管理界面列表展示
 * @param argumentsPreviewJson 脱敏后的参数预览 JSON，不应保存完整敏感入参
 * @param status              审批状态
 * @param requestedAt         审批创建时间
 * @param expiresAt           审批过期时间，未配置时为空
 * @param decidedBy           审批人 ID，未审批时为空
 * @param decidedAt           审批时间，未审批时为空
 * @param decisionComment     审批备注
 */
public record ApprovalRequest(String approvalId,
                              String runId,
                              String stepId,
                              String toolInvocationId,
                              String tenantId,
                              String userId,
                              String agentId,
                              String toolId,
                              ApprovalType approvalType,
                              ToolRiskLevel riskLevel,
                              String summary,
                              String argumentsPreviewJson,
                              ApprovalRequestStatus status,
                              Instant requestedAt,
                              Instant expiresAt,
                              String decidedBy,
                              Instant decidedAt,
                              String decisionComment) {

    public ApprovalRequest {
        approvalId = requireText(approvalId, "approvalId 不能为空");
        runId = requireText(runId, "runId 不能为空");
        stepId = trimToNull(stepId);
        toolInvocationId = trimToNull(toolInvocationId);
        tenantId = requireText(tenantId, "tenantId 不能为空");
        userId = requireText(userId, "userId 不能为空");
        agentId = trimToNull(agentId);
        toolId = requireText(toolId, "toolId 不能为空");
        approvalType = Objects.requireNonNullElse(approvalType, ApprovalType.TOOL_EXECUTION);
        riskLevel = Objects.requireNonNullElse(riskLevel, ToolRiskLevel.HIGH);
        summary = requireText(summary, "summary 不能为空");
        argumentsPreviewJson = trimToNull(argumentsPreviewJson);
        status = Objects.requireNonNullElse(status, ApprovalRequestStatus.PENDING);
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt 不能为空");
        decidedBy = trimToNull(decidedBy);
        decisionComment = trimToNull(decisionComment);
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
