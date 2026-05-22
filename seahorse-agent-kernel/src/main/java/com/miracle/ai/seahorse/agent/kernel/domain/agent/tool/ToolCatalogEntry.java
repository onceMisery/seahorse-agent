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
 * 工具目录条目，是 Tool Gateway 做策略、审批和审计的元数据来源。
 *
 * @param toolId           工具稳定 ID
 * @param provider         工具来源
 * @param name             工具展示名称
 * @param description      工具描述
 * @param schemaJson       输入 schema JSON
 * @param outputSchemaJson 输出 schema JSON，可为空
 * @param riskLevel        风险等级
 * @param actionType       动作类型
 * @param resourceType     关联资源类型，如 MEMORY、KNOWLEDGE_BASE、EMAIL
 * @param ownerTeam        工具 owner 团队
 * @param enabled          是否启用
 * @param requiresApproval 是否默认需要人工审批
 * @param createdAt        创建时间
 * @param updatedAt        最近更新时间
 */
public record ToolCatalogEntry(String toolId,
                               ToolProvider provider,
                               String name,
                               String description,
                               String schemaJson,
                               String outputSchemaJson,
                               ToolRiskLevel riskLevel,
                               ToolActionType actionType,
                               String resourceType,
                               String ownerTeam,
                               boolean enabled,
                               boolean requiresApproval,
                               Instant createdAt,
                               Instant updatedAt) {

    public static final String EMPTY_JSON_OBJECT = "{}";

    public ToolCatalogEntry {
        toolId = requireText(toolId, "toolId 不能为空");
        provider = Objects.requireNonNullElse(provider, ToolProvider.BUILTIN);
        name = requireText(name, "工具名称不能为空");
        description = trimToNull(description);
        schemaJson = defaultJson(schemaJson);
        outputSchemaJson = trimToNull(outputSchemaJson);
        riskLevel = Objects.requireNonNullElse(riskLevel, ToolRiskLevel.MEDIUM);
        actionType = Objects.requireNonNullElse(actionType, ToolActionType.EXECUTE);
        resourceType = trimToNull(resourceType);
        ownerTeam = trimToNull(ownerTeam);
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
    }

    private static String defaultJson(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? EMPTY_JSON_OBJECT : trimmed;
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
