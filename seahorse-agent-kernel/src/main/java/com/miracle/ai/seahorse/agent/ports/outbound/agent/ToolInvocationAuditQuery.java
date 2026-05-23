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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;

/**
 * 工具调用审计分页查询条件。
 *
 * @param tenantId  租户 ID 过滤条件，可为空
 * @param agentId   Agent ID 过滤条件，可为空
 * @param versionId Agent 版本 ID 过滤条件，可为空
 * @param runId     AgentRun ID 过滤条件，可为空
 * @param toolId    工具 ID 过滤条件，可为空
 * @param status    审计状态过滤条件，可为空
 * @param current   当前页码，从 1 开始
 * @param size      每页大小
 */
public record ToolInvocationAuditQuery(String tenantId,
                                       String agentId,
                                       String versionId,
                                       String runId,
                                       String toolId,
                                       ToolInvocationStatus status,
                                       long current,
                                       long size) {

    public static final long DEFAULT_CURRENT = 1L;
    public static final long DEFAULT_PAGE_SIZE = 10L;

    public ToolInvocationAuditQuery {
        tenantId = trimToNull(tenantId);
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
        runId = trimToNull(runId);
        toolId = trimToNull(toolId);
        current = current <= 0 ? DEFAULT_CURRENT : current;
        size = size <= 0 ? DEFAULT_PAGE_SIZE : size;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
