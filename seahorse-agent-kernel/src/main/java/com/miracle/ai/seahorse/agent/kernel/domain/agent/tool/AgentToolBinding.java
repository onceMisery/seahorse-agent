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
 * Agent 发布版本到工具的绑定快照。
 *
 * @param bindingId          绑定记录 ID
 * @param agentId            Agent 定义 ID
 * @param versionId          Agent 发布版本 ID
 * @param toolId             工具 ID
 * @param maxCallsPerRun     单次 run 最大调用次数
 * @param argumentPolicyJson 参数约束 JSON
 * @param createdBy          创建人用户 ID
 * @param createdAt          创建时间
 */
public record AgentToolBinding(String bindingId,
                               String agentId,
                               String versionId,
                               String toolId,
                               int maxCallsPerRun,
                               String argumentPolicyJson,
                               String createdBy,
                               Instant createdAt) {

    public static final String EMPTY_JSON_OBJECT = "{}";

    public AgentToolBinding {
        bindingId = requireText(bindingId, "bindingId 不能为空");
        agentId = requireText(agentId, "agentId 不能为空");
        versionId = requireText(versionId, "versionId 不能为空");
        toolId = requireText(toolId, "toolId 不能为空");
        if (maxCallsPerRun <= 0) {
            throw new IllegalArgumentException("maxCallsPerRun 必须大于 0");
        }
        argumentPolicyJson = defaultJson(argumentPolicyJson);
        createdBy = requireText(createdBy, "createdBy 不能为空");
        createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
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
