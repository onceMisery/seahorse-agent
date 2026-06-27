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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;

import java.util.Map;

/**
 * 启动 AgentRun 的入站命令。
 *
 * @param agentId        Agent 定义 ID
 * @param versionId      指定版本 ID；为空时使用 Agent 最新发布版本
 * @param tenantId       租户 ID
 * @param conversationId 关联会话 ID
 * @param triggerType    触发来源
 * @param inputSummary   输入摘要
 * @param traceId        外部链路追踪 ID
 * @param metadataJson   运行时快照 JSON
 */
public record AgentRunStartCommand(String agentId,
                                   String versionId,
                                   String rolloutId,
                                   String tenantId,
                                   String conversationId,
                                   AgentRunTriggerType triggerType,
                                   String inputSummary,
                                   String traceId,
                                   String metadataJson,
                                   Long runProfileId,
                                   String executorEngine,
                                   Map<String, Object> executorConfig,
                                   CurrentUser currentUser) {

    public AgentRunStartCommand {
        executorConfig = executorConfig == null ? Map.of() : Map.copyOf(executorConfig);
    }

    public AgentRunStartCommand(String agentId,
                                String versionId,
                                String rolloutId,
                                String tenantId,
                                String conversationId,
                                AgentRunTriggerType triggerType,
                                String inputSummary,
                                String traceId,
                                String metadataJson,
                                Long runProfileId,
                                String executorEngine,
                                Map<String, Object> executorConfig) {
        this(agentId, versionId, rolloutId, tenantId, conversationId, triggerType, inputSummary, traceId, metadataJson,
                runProfileId, executorEngine, executorConfig, null);
    }

    public AgentRunStartCommand(String agentId,
                                String versionId,
                                String rolloutId,
                                String tenantId,
                                String conversationId,
                                AgentRunTriggerType triggerType,
                                String inputSummary,
                                String traceId) {
        this(agentId, versionId, rolloutId, tenantId, conversationId, triggerType, inputSummary, traceId, null, null,
                null, Map.of(), null);
    }

    public AgentRunStartCommand(String agentId,
                                String versionId,
                                String tenantId,
                                String conversationId,
                                AgentRunTriggerType triggerType,
                                String inputSummary,
                                String traceId) {
        this(agentId, versionId, null, tenantId, conversationId, triggerType, inputSummary, traceId, null, null, null,
                Map.of(), null);
    }

    public AgentRunStartCommand(String agentId,
                                String versionId,
                                String rolloutId,
                                String tenantId,
                                String conversationId,
                                AgentRunTriggerType triggerType,
                                String inputSummary,
                                String traceId,
                                String metadataJson) {
        this(agentId, versionId, rolloutId, tenantId, conversationId, triggerType, inputSummary, traceId, metadataJson,
                null, null, Map.of(), null);
    }
}
