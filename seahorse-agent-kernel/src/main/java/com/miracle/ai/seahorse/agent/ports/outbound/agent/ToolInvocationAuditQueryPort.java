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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ToolInvocationAuditQueryPort {

    /**
     * 按条件分页查询工具调用审计记录。
     */
    ToolInvocationAuditPage page(ToolInvocationAuditQuery query);

    /**
     * 查找在截止时间前完成、仍停留在 UNKNOWN 的审计记录，供对账扫描使用。
     */
    List<ToolInvocationAuditEntry> findUnresolvedUnknown(Instant finishedBefore, int limit);

    /**
     * 查找同租户同幂等键下最近一次已收敛到终态（SUCCEEDED/FAILED）的调用记录，
     * 用于把 UNKNOWN 的不确定结果对账到真实结局。
     */
    Optional<ToolInvocationAuditEntry> findLatestTerminalByIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * 空查询实现，用于未配置持久化审计查询能力时保持依赖可选。
     */
    static ToolInvocationAuditQueryPort empty() {
        return new ToolInvocationAuditQueryPort() {
            @Override
            public ToolInvocationAuditPage page(ToolInvocationAuditQuery query) {
                ToolInvocationAuditQuery safeQuery = query == null
                        ? new ToolInvocationAuditQuery(null, null, null, null, null, null, 1L, 10L)
                        : query;
                return new ToolInvocationAuditPage(List.of(), 0L, safeQuery.size(), safeQuery.current(), 0L);
            }

            @Override
            public List<ToolInvocationAuditEntry> findUnresolvedUnknown(Instant finishedBefore, int limit) {
                return List.of();
            }

            @Override
            public Optional<ToolInvocationAuditEntry> findLatestTerminalByIdempotencyKey(
                    String tenantId, String idempotencyKey) {
                return Optional.empty();
            }
        };
    }
}
