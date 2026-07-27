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
 * 工具调用请求审计记录。
 *
 * @param invocationId     Gateway 内部生成的单次调用审计 ID
 * @param runId            AgentRun ID，用于关联一次运行内的工具调用
 * @param stepId           触发工具调用的 AgentStep ID
 * @param agentId          Agent 定义 ID
 * @param versionId        Agent 发布版本 ID
 * @param tenantId         租户 ID
 * @param userId           最终用户 ID
 * @param toolId           被请求调用的工具 ID
 * @param idempotencyKey   幂等键，用于后续重复调用治理
 * @param status           当前审计状态，创建请求记录时应为 REQUESTED
 * @param argumentsSummary 工具入参摘要，避免审计表保存完整敏感参数
 * @param startedAt        Gateway 接收调用请求的时间
 */
public record ToolInvocationAuditRecord(String invocationId,
                                        String runId,
                                        String stepId,
                                        String agentId,
                                        String versionId,
                                        String rolloutId,
                                        String tenantId,
                                        String userId,
                                        String toolId,
                                        String idempotencyKey,
                                        ToolInvocationStatus status,
                                        String argumentsSummary,
                                        Instant startedAt) {

    private static final int MAX_AUDIT_ID_LENGTH = 64;
    private static final int MAX_TOOL_ID_LENGTH = 128;

    public ToolInvocationAuditRecord(String invocationId,
                                     String runId,
                                     String stepId,
                                     String agentId,
                                     String versionId,
                                     String tenantId,
                                     String userId,
                                     String toolId,
                                     String idempotencyKey,
                                     ToolInvocationStatus status,
                                     String argumentsSummary,
                                     Instant startedAt) {
        this(invocationId,
                runId,
                stepId,
                agentId,
                versionId,
                null,
                tenantId,
                userId,
                toolId,
                idempotencyKey,
                status,
                argumentsSummary,
                startedAt);
    }

    public ToolInvocationAuditRecord {
        invocationId = requireText(invocationId, "invocationId", MAX_AUDIT_ID_LENGTH);
        stepId = requireText(stepId, "stepId", MAX_AUDIT_ID_LENGTH);
        toolId = requireText(toolId, "toolId", MAX_TOOL_ID_LENGTH);
        runId = requireText(runId, "runId", MAX_AUDIT_ID_LENGTH);
        agentId = optionalText(agentId, "agentId", MAX_AUDIT_ID_LENGTH);
        versionId = optionalText(versionId, "versionId", MAX_AUDIT_ID_LENGTH);
        rolloutId = optionalText(rolloutId, "rolloutId", MAX_AUDIT_ID_LENGTH);
        tenantId = requireText(tenantId, "tenantId", MAX_AUDIT_ID_LENGTH);
        userId = requireText(userId, "userId", MAX_AUDIT_ID_LENGTH);
        idempotencyKey = ToolInvocationIdentity.digest(tenantId, idempotencyKey);
        status = Objects.requireNonNullElse(status, ToolInvocationStatus.REQUESTED);
        argumentsSummary = trimToNull(argumentsSummary);
        startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return requireMaxLength(trimmed, field, maxLength);
    }

    private static String optionalText(String value, String field, int maxLength) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : requireMaxLength(trimmed, field, maxLength);
    }

    private static String requireMaxLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
