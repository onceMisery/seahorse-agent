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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 团队运行聚合：一次团队任务的编排状态与全部节点运行。
 *
 * <p>parentRunId 指向锚定本次团队运行的 {@code AgentRun}（triggerType=TEAM），
 * 所有成员分派产生的 handoff 都以它为 parentRunId，保证可追溯。
 */
public final class AgentTeamRun {

    private final String teamRunId;
    private final String teamId;
    private final String tenantId;
    private final String userId;
    private final AgentTeamMode mode;
    private final String objective;
    private final AgentTeamRunStatus status;
    private final String parentRunId;
    private final String summary;
    private final String errorCode;
    private final String errorMessage;
    private final List<AgentTeamNodeRun> nodeRuns;
    private final Instant startedAt;
    private final Instant finishedAt;

    public AgentTeamRun(String teamRunId,
                        String teamId,
                        String tenantId,
                        String userId,
                        AgentTeamMode mode,
                        String objective,
                        AgentTeamRunStatus status,
                        String parentRunId,
                        String summary,
                        String errorCode,
                        String errorMessage,
                        List<AgentTeamNodeRun> nodeRuns,
                        Instant startedAt,
                        Instant finishedAt) {
        this.teamRunId = AgentTeamMember.requireText(teamRunId, "teamRunId 不能为空");
        this.teamId = AgentTeamMember.requireText(teamId, "teamId 不能为空");
        this.tenantId = AgentTeamMember.requireText(tenantId, "tenantId 不能为空");
        this.userId = AgentTeamMember.normalize(userId);
        this.mode = Objects.requireNonNull(mode, "mode 不能为空");
        this.objective = AgentTeamMember.normalize(objective);
        this.status = Objects.requireNonNull(status, "status 不能为空");
        this.parentRunId = AgentTeamMember.normalize(parentRunId);
        this.summary = AgentTeamMember.normalize(summary);
        this.errorCode = AgentTeamMember.normalize(errorCode);
        this.errorMessage = AgentTeamMember.normalize(errorMessage);
        this.nodeRuns = List.copyOf(Objects.requireNonNullElse(nodeRuns, List.of()));
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt 不能为空");
        this.finishedAt = finishedAt;
    }

    public static AgentTeamRun start(String teamRunId,
                                     AgentTeamDefinition definition,
                                     String userId,
                                     String objective,
                                     String parentRunId,
                                     List<AgentTeamNodeRun> initialNodes,
                                     Instant now) {
        return new AgentTeamRun(teamRunId, definition.teamId(), definition.tenantId(), userId,
                definition.mode(), objective, AgentTeamRunStatus.RUNNING, parentRunId,
                null, null, null, initialNodes, now, null);
    }

    public AgentTeamRun withNodeRun(AgentTeamNodeRun updated) {
        List<AgentTeamNodeRun> next = new ArrayList<>(nodeRuns.size());
        boolean replaced = false;
        for (AgentTeamNodeRun nodeRun : nodeRuns) {
            if (nodeRun.nodeRunId().equals(updated.nodeRunId())) {
                next.add(updated);
                replaced = true;
            } else {
                next.add(nodeRun);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("团队运行不包含该节点: " + updated.nodeRunId());
        }
        return new AgentTeamRun(teamRunId, teamId, tenantId, userId, mode, objective, status,
                parentRunId, summary, errorCode, errorMessage, next, startedAt, finishedAt);
    }

    public AgentTeamRun withNodeRuns(List<AgentTeamNodeRun> nextNodeRuns) {
        return new AgentTeamRun(teamRunId, teamId, tenantId, userId, mode, objective, status,
                parentRunId, summary, errorCode, errorMessage, nextNodeRuns, startedAt, finishedAt);
    }

    public AgentTeamRun succeed(String summary, Instant now) {
        requireRunning();
        return new AgentTeamRun(teamRunId, teamId, tenantId, userId, mode, objective,
                AgentTeamRunStatus.SUCCEEDED, parentRunId, summary, null, null, nodeRuns, startedAt, now);
    }

    public AgentTeamRun fail(String errorCode, String errorMessage, Instant now) {
        return fail(errorCode, errorMessage, null, now);
    }

    /**
     * 失败并可携带部分输出摘要（SKIP 策略下其余成功分支的输出仍有价值）。
     */
    public AgentTeamRun fail(String errorCode, String errorMessage, String summary, Instant now) {
        requireRunning();
        return new AgentTeamRun(teamRunId, teamId, tenantId, userId, mode, objective,
                AgentTeamRunStatus.FAILED, parentRunId,
                summary == null ? this.summary : summary, errorCode, errorMessage,
                nodeRuns, startedAt, now);
    }

    public AgentTeamRun cancel(Instant now) {
        requireRunning();
        List<AgentTeamNodeRun> skipped = nodeRuns.stream()
                .map(node -> node.status() == AgentTeamNodeStatus.PENDING
                        || node.status() == AgentTeamNodeStatus.RUNNING
                        ? node.skip(now)
                        : node)
                .toList();
        return new AgentTeamRun(teamRunId, teamId, tenantId, userId, mode, objective,
                AgentTeamRunStatus.CANCELLED, parentRunId, summary, null, null, skipped, startedAt, now);
    }

    private void requireRunning() {
        if (status.isTerminal()) {
            throw new IllegalStateException("团队运行已进入终态: " + status);
        }
    }

    public Map<String, AgentTeamNodeRun> nodeRunIndex() {
        Map<String, AgentTeamNodeRun> index = new LinkedHashMap<>();
        for (AgentTeamNodeRun nodeRun : nodeRuns) {
            index.put(nodeRun.memberId(), nodeRun);
        }
        return index;
    }

    public String teamRunId() {
        return teamRunId;
    }

    public String teamId() {
        return teamId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public AgentTeamMode mode() {
        return mode;
    }

    public String objective() {
        return objective;
    }

    public AgentTeamRunStatus status() {
        return status;
    }

    public String parentRunId() {
        return parentRunId;
    }

    public String summary() {
        return summary;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public List<AgentTeamNodeRun> nodeRuns() {
        return nodeRuns;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
