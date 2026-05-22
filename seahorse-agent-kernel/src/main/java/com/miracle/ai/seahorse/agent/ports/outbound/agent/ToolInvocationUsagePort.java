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

/**
 * 工具调用用量查询端口。
 */
@FunctionalInterface
public interface ToolInvocationUsagePort {

    /**
     * 统计单次 run 内指定 Agent 版本与工具的已请求调用次数。
     *
     * <p>Gateway 会先写入 REQUESTED 审计事件再进入策略裁决，因此该计数包含当前请求。
     */
    long countRequestedCalls(String runId, String agentId, String versionId, String toolId);

    /**
     * 空用量实现，用于未配置持久审计仓储时保持策略兼容。
     */
    static ToolInvocationUsagePort empty() {
        return (runId, agentId, versionId, toolId) -> 0L;
    }
}
