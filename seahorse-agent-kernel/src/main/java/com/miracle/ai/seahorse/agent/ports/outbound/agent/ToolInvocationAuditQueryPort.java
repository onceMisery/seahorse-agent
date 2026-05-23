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

import java.util.List;

public interface ToolInvocationAuditQueryPort {

    /**
     * 按条件分页查询工具调用审计记录。
     */
    ToolInvocationAuditPage page(ToolInvocationAuditQuery query);

    /**
     * 空查询实现，用于未配置持久化审计查询能力时保持依赖可选。
     */
    static ToolInvocationAuditQueryPort empty() {
        return query -> {
            ToolInvocationAuditQuery safeQuery = query == null
                    ? new ToolInvocationAuditQuery(null, null, null, null, null, null, 1L, 10L)
                    : query;
            return new ToolInvocationAuditPage(List.of(), 0L, safeQuery.size(), safeQuery.current(), 0L);
        };
    }
}
