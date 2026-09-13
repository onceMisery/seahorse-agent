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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.team;

import java.time.Instant;
import java.util.Objects;

/**
 * 团队运行内单个节点的执行记录。
 *
 * @param nodeRunId     节点运行 ID
 * @param memberId      成员 ID
 * @param agentId       成员 Agent ID
 * @param instruction   分派给成员的子任务说明
 * @param status        节点状态
 * @param handoffId     分派产生的 handoff ID（未分派时为空）
 * @param childRunId    handoff 创建的 child run ID（用于可追溯）
 * @param outputSummary 节点输出摘要
 * @param errorCode     失败码
 * @param errorMessage  失败信息
 * @param startedAt     开始时间
 * @param finishedAt    结束时间
 */
public record AgentTeamNodeRun(String nodeRunId,
                               String memberId,
                               String agentId,
                               String instruction,
                               AgentTeamNodeStatus status,
                               String handoffId,
                               String childRunId,
                               String outputSummary,
                               String errorCode,
                               String errorMessage,
                               Instant startedAt,
                               Instant finishedAt) {

    public AgentTeamNodeRun {
        Objects.requireNonNull(nodeRunId, "nodeRunId 不能为空");
        memberId = AgentTeamMember.requireText(memberId, "memberId 不能为空");
        agentId = AgentTeamMember.requireText(agentId, "agentId 不能为空");
        instruction = AgentTeamMember.normalize(instruction);
        status = Objects.requireNonNull(status, "status 不能为空");
        outputSummary = AgentTeamMember.normalize(outputSummary);
        errorCode = AgentTeamMember.normalize(errorCode);
        errorMessage = AgentTeamMember.normalize(errorMessage);
    }

    public static AgentTeamNodeRun pending(String nodeRunId, String memberId, String agentId, String instruction) {
        return new AgentTeamNodeRun(nodeRunId, memberId, agentId, instruction,
                AgentTeamNodeStatus.PENDING, null, null, null, null, null, null, null);
    }

    public AgentTeamNodeRun running(Instant now) {
        return new AgentTeamNodeRun(nodeRunId, memberId, agentId, instruction,
                AgentTeamNodeStatus.RUNNING, handoffId, childRunId, outputSummary,
                errorCode, errorMessage, now, null);
    }

    public AgentTeamNodeRun withDispatch(String handoffId, String childRunId) {
        return new AgentTeamNodeRun(nodeRunId, memberId, agentId, instruction,
                status, handoffId, childRunId, outputSummary,
                errorCode, errorMessage, startedAt, finishedAt);
    }

    public AgentTeamNodeRun succeed(String outputSummary, Instant now) {
        return new AgentTeamNodeRun(nodeRunId, memberId, agentId, instruction,
                AgentTeamNodeStatus.SUCCEEDED, handoffId, childRunId, outputSummary,
                null, null, startedAt, now);
    }

    public AgentTeamNodeRun fail(String errorCode, String errorMessage, Instant now) {
        return new AgentTeamNodeRun(nodeRunId, memberId, agentId, instruction,
                AgentTeamNodeStatus.FAILED, handoffId, childRunId, outputSummary,
                errorCode, errorMessage, startedAt, now);
    }

    public AgentTeamNodeRun skip(Instant now) {
        return new AgentTeamNodeRun(nodeRunId, memberId, agentId, instruction,
                AgentTeamNodeStatus.SKIPPED, handoffId, childRunId, outputSummary,
                errorCode, errorMessage, startedAt, now);
    }
}
