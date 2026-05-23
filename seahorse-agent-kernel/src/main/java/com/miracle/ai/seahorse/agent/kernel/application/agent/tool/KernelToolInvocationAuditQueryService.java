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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ToolInvocationAuditQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.Objects;

public class KernelToolInvocationAuditQueryService implements ToolInvocationAuditQueryInboundPort {

    private static final String ADMIN_ROLE = "admin";

    private final ToolInvocationAuditQueryPort queryPort;
    private final CurrentUserPort currentUserPort;

    public KernelToolInvocationAuditQueryService(ToolInvocationAuditQueryPort queryPort,
                                                 CurrentUserPort currentUserPort) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public ToolInvocationAuditPage page(String tenantId,
                                        String agentId,
                                        String versionId,
                                        String runId,
                                        String toolId,
                                        ToolInvocationStatus status,
                                        long current,
                                        long size) {
        currentUserPort.requireRole(ADMIN_ROLE);
        // 查询服务只负责鉴权和条件归一化，分页 SQL 与字段映射由只读查询端口负责。
        return queryPort.page(new ToolInvocationAuditQuery(
                tenantId,
                agentId,
                versionId,
                runId,
                toolId,
                status,
                current,
                size));
    }
}
