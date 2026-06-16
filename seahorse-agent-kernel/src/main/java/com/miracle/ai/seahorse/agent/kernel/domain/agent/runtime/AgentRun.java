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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Agent 单次运行记录。
 *
 * @param runId          运行 ID，用于关联步骤、工具审计和前端追踪
 * @param agentId        Agent 定义 ID；legacy ReAct 运行可以使用兼容 ID
 * @param versionId      Agent 版本 ID，用于运行时配置回放
 * @param tenantId       租户 ID
 * @param userId         触发运行的最终用户 ID
 * @param conversationId 关联会话 ID，可为空
 * @param triggerType    触发来源，如 API、聊天或计划任务
 * @param inputSummary   输入摘要，避免 run 表直接承载完整敏感上下文
 * @param status         运行状态
 * @param traceId        外部链路追踪 ID
 * @param tokenInput     输入 token 计数
 * @param tokenOutput    输出 token 计数
 * @param costTotal      运行成本汇总
 * @param errorCode      失败原因码
 * @param errorMessage   失败原因说明
 * @param startedAt      开始时间
 * @param finishedAt     结束时间；运行中为空
 */
public record AgentRun(String runId,
                       String agentId,
                       String versionId,
                       String rolloutId,
                       String tenantId,
                       String userId,
                       String conversationId,
                       AgentRunTriggerType triggerType,
                       String inputSummary,
                       AgentRunStatus status,
                       String traceId,
                       long tokenInput,
                       long tokenOutput,
                       BigDecimal costTotal,
                       String errorCode,
                       String errorMessage,
                       Instant startedAt,
                       Instant finishedAt) {

    public static final BigDecimal ZERO_COST = BigDecimal.ZERO;

    public AgentRun(String runId,
                    String agentId,
                    String versionId,
                    String tenantId,
                    String userId,
                    String conversationId,
                    AgentRunTriggerType triggerType,
                    String inputSummary,
                    AgentRunStatus status,
                    String traceId,
                    long tokenInput,
                    long tokenOutput,
                    BigDecimal costTotal,
                    String errorCode,
                    String errorMessage,
                    Instant startedAt,
                    Instant finishedAt) {
        this(runId,
                agentId,
                versionId,
                null,
                tenantId,
                userId,
                conversationId,
                triggerType,
                inputSummary,
                status,
                traceId,
                tokenInput,
                tokenOutput,
                costTotal,
                errorCode,
                errorMessage,
                startedAt,
                finishedAt);
    }

    public AgentRun {
        runId = requireText(runId, "runId 不能为空");
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
        rolloutId = trimToNull(rolloutId);
        tenantId = defaultTenant(tenantId);
        userId = requireText(userId, "userId 不能为空");
        conversationId = trimToNull(conversationId);
        triggerType = triggerType == null ? AgentRunTriggerType.API : triggerType;
        inputSummary = trimToNull(inputSummary);
        status = Objects.requireNonNull(status, "status 不能为空");
        traceId = trimToNull(traceId);
        if (tokenInput < 0 || tokenOutput < 0) {
            throw new IllegalArgumentException("token 计数不能为负数");
        }
        costTotal = costTotal == null ? ZERO_COST : costTotal;
        if (costTotal.signum() < 0) {
            throw new IllegalArgumentException("costTotal 不能为负数");
        }
        errorCode = trimToNull(errorCode);
        errorMessage = trimToNull(errorMessage);
        startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
    }

    /**
     * 取消运行保持幂等，重复取消不会产生新的状态漂移。
     */
    public AgentRun cancel(Instant finishedAt) {
        if (status == AgentRunStatus.CANCELLED) {
            return this;
        }
        return withStatus(AgentRunStatus.CANCELLED, null, null, finishedAt);
    }

    /**
     * 失败运行可进入重试等待态，等待后续 worker 或 orchestrator 接管。
     */
    public AgentRun retry() {
        if (status == AgentRunStatus.RETRYING) {
            return this;
        }
        if (status != AgentRunStatus.FAILED) {
            throw new IllegalStateException("Only FAILED runs can be retried");
        }
        return withStatus(AgentRunStatus.RETRYING, null, null, null);
    }

    /**
     * 统一生成状态变更后的不可变副本，避免调用方原地修改运行记录。
     */
    public AgentRun withStatus(AgentRunStatus nextStatus, String nextErrorCode, String nextErrorMessage, Instant doneAt) {
        return new AgentRun(runId,
                agentId,
                versionId,
                rolloutId,
                tenantId,
                userId,
                conversationId,
                triggerType,
                inputSummary,
                Objects.requireNonNull(nextStatus, "status 不能为空"),
                traceId,
                tokenInput,
                tokenOutput,
                costTotal,
                nextErrorCode,
                nextErrorMessage,
                startedAt,
                doneAt);
    }

    private static String defaultTenant(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? AgentDefinition.DEFAULT_TENANT_ID : trimmed;
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
