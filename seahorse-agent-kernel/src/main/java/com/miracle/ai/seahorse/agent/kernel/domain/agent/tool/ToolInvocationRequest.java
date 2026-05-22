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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tool Gateway 的标准工具调用请求。
 *
 * @param runId             AgentRun ID，用于串联单次运行内的工具审计与幂等判断
 * @param stepId            当前推理步骤 ID，标识哪一步触发了工具调用
 * @param toolCallId        模型返回的 tool_call ID，用于把 observation 回填给模型
 * @param agentId           Agent 定义 ID；兼容 legacy ReAct 运行时的默认 Agent
 * @param versionId         Agent 发布版本 ID，后续用于版本级工具绑定与策略回放
 * @param tenantId          租户 ID，用于租户隔离、配额和策略裁决
 * @param userId            最终用户 ID，用于用户权限、审计和配额判断
 * @param agentIdentityId   Agent 执行身份 ID，用于区分“用户本人权限”和“代理身份权限”
 * @param toolId            被请求调用的工具 ID
 * @param arguments         工具入参快照，Gateway 和策略层不得直接修改调用方对象
 * @param resourceRefs      工具调用关联的资源引用，后续用于资源 ACL 判断
 * @param idempotencyKey    幂等键，用于重复工具调用去重
 * @param allowedToolIds    当前 Agent 运行上下文暴露给模型的工具集合
 */
public record ToolInvocationRequest(String runId,
                                    String stepId,
                                    String toolCallId,
                                    String agentId,
                                    String versionId,
                                    String tenantId,
                                    String userId,
                                    String agentIdentityId,
                                    String toolId,
                                    Map<String, Object> arguments,
                                    Map<String, String> resourceRefs,
                                    String idempotencyKey,
                                    List<String> allowedToolIds) {

    public ToolInvocationRequest {
        stepId = requireText(stepId, "stepId must not be blank");
        toolCallId = requireText(toolCallId, "toolCallId must not be blank");
        toolId = requireText(toolId, "toolId must not be blank");
        runId = trimToNull(runId);
        agentId = defaultText(agentId, AgentRuntimeConstants.LEGACY_REACT_AGENT_ID);
        versionId = trimToNull(versionId);
        tenantId = defaultText(tenantId, AgentDefinition.DEFAULT_TENANT_ID);
        userId = defaultText(userId, "");
        agentIdentityId = defaultText(agentIdentityId, userId);
        arguments = Map.copyOf(Objects.requireNonNullElse(arguments, Map.of()));
        resourceRefs = Map.copyOf(Objects.requireNonNullElse(resourceRefs, Map.of()));
        idempotencyKey = trimToNull(idempotencyKey);
        allowedToolIds = List.copyOf(Objects.requireNonNullElse(allowedToolIds, List.of()));
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? Objects.requireNonNullElse(fallback, "") : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
