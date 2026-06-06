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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.definition;

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants;

import java.time.Instant;
import java.util.Objects;

/**
 * 企业级 AI Infra 中的 Agent 定义草稿/发布态元数据。
 *
 * @param agentId         Agent 稳定 ID，作为编排、运行和版本管理的主键
 * @param tenantId        租户 ID，缺省为 default，用于多租户隔离
 * @param name            Agent 展示名称
 * @param description     Agent 业务描述
 * @param ownerUserId     Agent 负责人用户 ID
 * @param ownerTeam       Agent 所属团队，用于治理、审批和告警路由
 * @param agentType       Agent 类型，区分助手、流程型、后台任务等形态
 * @param baseAgentId     派生来源 Agent ID，用于后续 Agent 衍生链路追踪
 * @param status          当前生命周期状态
 * @param riskLevel       Agent 风险等级，用于默认策略和审批基线
 * @param latestVersionId 最新发布版本 ID；草稿阶段可以为空
 * @param createdAt       创建时间
 * @param updatedAt       最近更新时间
 */
public record AgentDefinition(String agentId,
                              String tenantId,
                              String name,
                              String description,
                              String ownerUserId,
                              String ownerTeam,
                              AgentType agentType,
                              String baseAgentId,
                              AgentStatus status,
                              AgentRiskLevel riskLevel,
                              String latestVersionId,
                              Instant createdAt,
                              Instant updatedAt) {

    /**
     * @deprecated 使用 {@link com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants#DEFAULT_TENANT_ID}
     */
    @Deprecated
    public static final String DEFAULT_TENANT_ID = com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants.DEFAULT_TENANT_ID;
    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    public AgentDefinition {
        agentId = requireText(agentId, "agentId 不能为空");
        tenantId = defaultTenant(tenantId);
        name = requireLength(requireText(name, "Agent 名称不能为空"), MAX_NAME_LENGTH, "Agent 名称不能超过 80 字符");
        description = optionalLength(description, MAX_DESCRIPTION_LENGTH, "Agent 描述不能超过 500 字符");
        ownerUserId = requireText(ownerUserId, "ownerUserId 不能为空");
        ownerTeam = trimToNull(ownerTeam);
        agentType = agentType == null ? AgentType.ASSISTANT : agentType;
        baseAgentId = trimToNull(baseAgentId);
        status = Objects.requireNonNull(status, "Agent status 不能为空");
        riskLevel = riskLevel == null ? AgentRiskLevel.LOW : riskLevel;
        latestVersionId = trimToNull(latestVersionId);
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
    }

    /**
     * 只允许修改 DRAFT 状态，避免已发布 Agent 的运行配置被原地漂移。
     */
    public AgentDefinition updateDraft(String nextName,
                                       String nextDescription,
                                       String nextOwnerTeam,
                                       AgentType nextAgentType,
                                       AgentRiskLevel nextRiskLevel,
                                       Instant now) {
        if (status != AgentStatus.DRAFT) {
            throw new IllegalStateException("只有 DRAFT 状态的 Agent 可以修改");
        }
        return new AgentDefinition(
                agentId,
                tenantId,
                nextName == null ? name : nextName,
                nextDescription == null ? description : nextDescription,
                ownerUserId,
                nextOwnerTeam == null ? ownerTeam : nextOwnerTeam,
                nextAgentType == null ? agentType : nextAgentType,
                baseAgentId,
                status,
                nextRiskLevel == null ? riskLevel : nextRiskLevel,
                latestVersionId,
                createdAt,
                Objects.requireNonNull(now, "updatedAt 不能为空"));
    }

    /**
     * 发布 Agent 时只更新定义的生命周期和 latestVersionId；版本内容保存在 AgentVersion。
     */
    public AgentDefinition publish(String versionId, Instant now) {
        return new AgentDefinition(
                agentId,
                tenantId,
                name,
                description,
                ownerUserId,
                ownerTeam,
                agentType,
                baseAgentId,
                AgentStatus.PUBLISHED,
                riskLevel,
                requireText(versionId, "latestVersionId 不能为空"),
                createdAt,
                Objects.requireNonNull(now, "updatedAt 不能为空"));
    }

    /**
     * 禁用 Agent 后，运行入口必须拒绝创建新的 AgentRun。
     */
    public AgentDefinition disable(Instant now) {
        return new AgentDefinition(
                agentId,
                tenantId,
                name,
                description,
                ownerUserId,
                ownerTeam,
                agentType,
                baseAgentId,
                AgentStatus.DISABLED,
                riskLevel,
                latestVersionId,
                createdAt,
                Objects.requireNonNull(now, "updatedAt must not be null"));
    }

    /**
     * 重新启用之前被禁用的 Agent，恢复到 DRAFT 或 PUBLISHED 状态。
     */
    public AgentDefinition enable(Instant now) {
        if (status != AgentStatus.DISABLED) {
            throw new IllegalStateException("只有 DISABLED 状态的 Agent 才能启用，当前状态：" + status);
        }
        AgentStatus restoredStatus = (latestVersionId != null) ? AgentStatus.PUBLISHED : AgentStatus.DRAFT;
        return new AgentDefinition(
                agentId, tenantId, name, description, ownerUserId, ownerTeam,
                agentType, baseAgentId, restoredStatus, riskLevel, latestVersionId,
                createdAt, Objects.requireNonNull(now, "updatedAt must not be null"));
    }

    public boolean disabled() {
        return status == AgentStatus.DISABLED;
    }

    private static String defaultTenant(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? DEFAULT_TENANT_ID : trimmed;
    }

    private static String optionalLength(String value, int maxLength, String message) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : requireLength(trimmed, maxLength, message);
    }

    private static String requireLength(String value, int maxLength, String message) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return value;
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
