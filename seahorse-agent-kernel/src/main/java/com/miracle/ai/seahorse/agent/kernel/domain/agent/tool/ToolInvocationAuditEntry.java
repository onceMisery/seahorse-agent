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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.tool;

import java.time.Instant;
import java.util.Objects;

/**
 * 工具调用审计查询视图。
 *
 * @param invocationId      单次工具调用审计 ID
 * @param runId             AgentRun ID
 * @param stepId            触发工具调用的 AgentStep ID
 * @param agentId           Agent 定义 ID
 * @param versionId         Agent 发布版本 ID
 * @param tenantId          租户 ID
 * @param userId            最终用户 ID
 * @param toolId            被请求调用的工具 ID
 * @param idempotencyKey    幂等键
 * @param status            当前审计状态
 * @param policyDecisionId  策略裁决 ID
 * @param argumentsSummary  工具入参摘要
 * @param resultSummary     工具输出摘要
 * @param errorMessage      失败或拒绝原因
 * @param startedAt         Gateway 接收请求时间
 * @param finishedAt        Gateway 完成处理时间
 */
public record ToolInvocationAuditEntry(String invocationId,
                                       String runId,
                                       String stepId,
                                       String agentId,
                                       String versionId,
                                       String tenantId,
                                       String userId,
                                       String toolId,
                                       String idempotencyKey,
                                       ToolInvocationStatus status,
                                       String policyDecisionId,
                                       String argumentsSummary,
                                       String resultSummary,
                                       String errorMessage,
                                       Instant startedAt,
                                       Instant finishedAt) {

    public ToolInvocationAuditEntry {
        invocationId = requireText(invocationId, "invocationId 不能为空");
        runId = requireText(runId, "runId 不能为空");
        stepId = requireText(stepId, "stepId 不能为空");
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
        tenantId = requireText(tenantId, "tenantId 不能为空");
        userId = requireText(userId, "userId 不能为空");
        toolId = requireText(toolId, "toolId 不能为空");
        idempotencyKey = trimToNull(idempotencyKey);
        status = Objects.requireNonNull(status, "status 不能为空");
        policyDecisionId = trimToNull(policyDecisionId);
        argumentsSummary = trimToNull(argumentsSummary);
        resultSummary = trimToNull(resultSummary);
        errorMessage = trimToNull(errorMessage);
        startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
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
