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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.policy;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具策略裁决请求。
 *
 * @param runId              AgentRun ID，用于单次运行内的额度、审计和幂等判断
 * @param stepId             AgentStep ID，用于定位触发工具调用的推理步骤
 * @param toolCallId         模型生成的 tool_call ID，用于回填 observation
 * @param agentId            Agent 定义 ID，用于 Agent-tool 绑定判断
 * @param versionId          Agent 发布版本 ID，用于避免发布后工具集漂移
 * @param tenantId           租户 ID，用于租户级策略和配额判断
 * @param userId             最终用户 ID，用于 RBAC、ABAC 和资源 ACL 判断
 * @param agentIdentityId    Agent 执行身份 ID，用于区分用户权限和代理身份权限
 * @param toolId             被请求调用的工具 ID
 * @param arguments          工具入参快照，策略可基于参数做约束判断
 * @param resourceRefs       工具关联资源引用，策略可基于资源 ACL 做裁决
 * @param idempotencyKey     幂等键，用于重复请求识别
 * @param allowedToolIds     当前 Agent/请求暴露给模型的工具集合
 * @param toolRegistered     工具是否存在于当前 Gateway 可访问的注册表中
 */
public record ToolPolicyRequest(String runId,
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
                                List<String> allowedToolIds,
                                boolean toolRegistered) {

    public ToolPolicyRequest {
        stepId = requireText(stepId, "stepId must not be blank");
        toolCallId = requireText(toolCallId, "toolCallId must not be blank");
        toolId = requireText(toolId, "toolId must not be blank");
        runId = trimToNull(runId);
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
        tenantId = trimToNull(tenantId);
        userId = trimToNull(userId);
        agentIdentityId = trimToNull(agentIdentityId);
        arguments = Map.copyOf(Objects.requireNonNullElse(arguments, Map.of()));
        resourceRefs = Map.copyOf(Objects.requireNonNullElse(resourceRefs, Map.of()));
        idempotencyKey = trimToNull(idempotencyKey);
        allowedToolIds = List.copyOf(Objects.requireNonNullElse(allowedToolIds, List.of()));
    }

    public static ToolPolicyRequest from(ToolInvocationRequest request, boolean toolRegistered) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return new ToolPolicyRequest(
                safeRequest.runId(),
                safeRequest.stepId(),
                safeRequest.toolCallId(),
                safeRequest.agentId(),
                safeRequest.versionId(),
                safeRequest.tenantId(),
                safeRequest.userId(),
                safeRequest.agentIdentityId(),
                safeRequest.toolId(),
                safeRequest.arguments(),
                safeRequest.resourceRefs(),
                safeRequest.idempotencyKey(),
                safeRequest.allowedToolIds(),
                toolRegistered);
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
