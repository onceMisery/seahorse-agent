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

import java.time.Instant;
import java.util.Objects;

/**
 * Agent 发布版本快照。
 *
 * @param versionId           版本稳定 ID，一经发布不再修改
 * @param agentId             所属 Agent ID
 * @param versionNo           单 Agent 内递增版本号
 * @param instructions        发布时的系统指令/编排说明
 * @param toolSetJson         发布时绑定的工具集快照 JSON
 * @param modelConfigJson     发布时模型配置 JSON
 * @param memoryConfigJson    发布时记忆配置 JSON
 * @param guardrailConfigJson 发布时安全护栏配置 JSON
 * @param publishedBy         发布人用户 ID
 * @param publishedAt         发布时间
 * @param changeSummary       发布变更说明，用于审计和回滚判断
 */
public record AgentVersion(String versionId,
                           String agentId,
                           long versionNo,
                           String instructions,
                           String toolSetJson,
                           String modelConfigJson,
                           String memoryConfigJson,
                           String guardrailConfigJson,
                           String publishedBy,
                           Instant publishedAt,
                           String changeSummary) {

    public static final String EMPTY_JSON_OBJECT = "{}";
    public static final int MAX_CHANGE_SUMMARY_LENGTH = 500;

    public AgentVersion {
        versionId = requireText(versionId, "versionId 不能为空");
        agentId = requireText(agentId, "agentId 不能为空");
        if (versionNo <= 0) {
            throw new IllegalArgumentException("versionNo 必须大于 0");
        }
        instructions = requireText(instructions, "instructions 不能为空");
        toolSetJson = defaultJson(toolSetJson);
        modelConfigJson = defaultJson(modelConfigJson);
        memoryConfigJson = defaultJson(memoryConfigJson);
        guardrailConfigJson = defaultJson(guardrailConfigJson);
        publishedBy = requireText(publishedBy, "publishedBy 不能为空");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt 不能为空");
        changeSummary = requireChangeSummary(changeSummary);
    }

    private static String defaultJson(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? EMPTY_JSON_OBJECT : trimmed;
    }

    private static String requireChangeSummary(String value) {
        String trimmed = requireText(value, "changeSummary 不能为空");
        if (trimmed.length() > MAX_CHANGE_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("changeSummary 不能超过 500 字符");
        }
        return trimmed;
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
