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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;

/**
 * 工具调用审计出站端口。
 */
public interface ToolInvocationAuditPort {

    /**
     * 记录 Gateway 已接收工具调用请求。
     */
    void recordRequested(ToolInvocationAuditRecord record);

    /**
     * 记录策略裁决结果。
     */
    void recordDecision(ToolInvocationAuditDecision decision);

    /**
     * 记录工具调用最终处理结果。
     */
    void recordCompleted(ToolInvocationAuditCompletion completion);

    /**
     * 空审计实现，用于未配置审计仓储时保持 Gateway 行为兼容。
     */
    static ToolInvocationAuditPort noop() {
        return new ToolInvocationAuditPort() {
            @Override
            public void recordRequested(ToolInvocationAuditRecord record) {
            }

            @Override
            public void recordDecision(ToolInvocationAuditDecision decision) {
            }

            @Override
            public void recordCompleted(ToolInvocationAuditCompletion completion) {
            }
        };
    }
}
