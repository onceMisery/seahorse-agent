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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent 团队定义聚合：成员、协作边与编排模式的持久化快照。
 *
 * <p>校验规则（见 Multi-Agent A2A 设计 §6.1/§6.3）：
 * SUPERVISOR 模式必须指定属于团队的 supervisor 成员；WORKFLOW_DAG 模式的边
 * 必须构成有向无环图且所有成员连通；成员 agentId 在团队内唯一。
 */
public final class AgentTeamDefinition {

    private final String teamId;
    private final String tenantId;
    private final String name;
    private final AgentTeamMode mode;
    private final String ownerTeam;
    private final String supervisorMemberId;
    private final List<AgentTeamMember> members;
    private final List<AgentTeamEdge> edges;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final AgentTeamFailurePolicy failurePolicy;
    private final Integer maxRetries;

    public AgentTeamDefinition(String teamId,
                               String tenantId,
                               String name,
                               AgentTeamMode mode,
                               String ownerTeam,
                               String supervisorMemberId,
                               List<AgentTeamMember> members,
                               List<AgentTeamEdge> edges,
                               boolean active,
                               Instant createdAt,
                               Instant updatedAt) {
        this(teamId, tenantId, name, mode, ownerTeam, supervisorMemberId, members, edges, active,
                createdAt, updatedAt, null, null);
    }

    public AgentTeamDefinition(String teamId,
                               String tenantId,
                               String name,
                               AgentTeamMode mode,
                               String ownerTeam,
                               String supervisorMemberId,
                               List<AgentTeamMember> members,
                               List<AgentTeamEdge> edges,
                               boolean active,
                               Instant createdAt,
                               Instant updatedAt,
                               AgentTeamFailurePolicy failurePolicy,
                               Integer maxRetries) {
        this.teamId = AgentTeamMember.requireText(teamId, "teamId 不能为空");
        this.tenantId = AgentTeamMember.requireText(tenantId, "tenantId 不能为空");
        this.name = AgentTeamMember.requireText(name, "team name 不能为空");
        this.mode = Objects.requireNonNull(mode, "mode 不能为空");
        this.ownerTeam = AgentTeamMember.normalize(ownerTeam);
        this.members = List.copyOf(Objects.requireNonNull(members, "members 不能为空"));
        this.edges = List.copyOf(Objects.requireNonNullElse(edges, List.of()));
        this.supervisorMemberId = AgentTeamMember.normalize(supervisorMemberId);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
        this.failurePolicy = failurePolicy == null
                ? AgentTeamFailurePolicy.FAIL_FAST
                : failurePolicy;
        if (maxRetries != null && maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能为负数");
        }
        this.maxRetries = maxRetries == null ? 0 : maxRetries;
        validate();
    }

    private void validate() {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("team 至少需要一个成员");
        }
        Set<String> memberIds = new HashSet<>();
        Set<String> agentIds = new HashSet<>();
        for (AgentTeamMember member : members) {
            if (!memberIds.add(member.memberId())) {
                throw new IllegalArgumentException("成员 ID 重复: " + member.memberId());
            }
            if (!agentIds.add(member.agentId())) {
                throw new IllegalArgumentException("成员 agentId 重复: " + member.agentId());
            }
        }
        Map<String, AgentTeamMember> byId = memberIndex();
        for (AgentTeamEdge edge : edges) {
            if (!byId.containsKey(edge.sourceMemberId())) {
                throw new IllegalArgumentException("边引用了未知成员: " + edge.sourceMemberId());
            }
            if (!byId.containsKey(edge.targetMemberId())) {
                throw new IllegalArgumentException("边引用了未知成员: " + edge.targetMemberId());
            }
        }
        if (mode == AgentTeamMode.SUPERVISOR) {
            if (supervisorMemberId.isBlank()) {
                throw new IllegalArgumentException("SUPERVISOR 模式必须指定 supervisorMemberId");
            }
            if (!byId.containsKey(supervisorMemberId)) {
                throw new IllegalArgumentException("supervisorMemberId 不在团队成员中: " + supervisorMemberId);
            }
        }
        if (mode == AgentTeamMode.WORKFLOW_DAG) {
            requireAcyclicAndConnected();
        }
    }

    private void requireAcyclicAndConnected() {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        for (String memberId : memberIndex().keySet()) {
            inDegree.putIfAbsent(memberId, 0);
            adjacency.putIfAbsent(memberId, new ArrayList<>());
        }
        for (AgentTeamEdge edge : edges) {
            adjacency.get(edge.sourceMemberId()).add(edge.targetMemberId());
            inDegree.merge(edge.targetMemberId(), 1, Integer::sum);
        }
        Deque<String> ready = new java.util.ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String current = ready.poll();
            visited++;
            for (String next : adjacency.get(current)) {
                int remaining = inDegree.merge(next, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(next);
                }
            }
        }
        if (visited != inDegree.size()) {
            throw new IllegalArgumentException("WORKFLOW_DAG 模式的边不允许成环");
        }
    }

    public Map<String, AgentTeamMember> memberIndex() {
        Map<String, AgentTeamMember> index = new LinkedHashMap<>();
        for (AgentTeamMember member : members) {
            index.put(member.memberId(), member);
        }
        return index;
    }

    public AgentTeamMember member(String memberId) {
        AgentTeamMember member = memberIndex().get(memberId);
        if (member == null) {
            throw new IllegalArgumentException("团队成员不存在: " + memberId);
        }
        return member;
    }

    public boolean isMemberAgent(String agentId) {
        return members.stream().anyMatch(member -> member.matchesAgent(agentId));
    }

    public String defaultSourceAgentId() {
        if (!supervisorMemberId.isBlank()) {
            return member(supervisorMemberId).agentId();
        }
        return members.get(0).agentId();
    }

    public AgentTeamDefinition disable(Instant now) {
        return new AgentTeamDefinition(teamId, tenantId, name, mode, ownerTeam, supervisorMemberId,
                members, edges, false, createdAt, now, failurePolicy, maxRetries);
    }

    public String teamId() {
        return teamId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public AgentTeamMode mode() {
        return mode;
    }

    public String ownerTeam() {
        return ownerTeam;
    }

    public String supervisorMemberId() {
        return supervisorMemberId;
    }

    public List<AgentTeamMember> members() {
        return members;
    }

    public List<AgentTeamEdge> edges() {
        return edges;
    }

    public boolean isActive() {
        return active;
    }

    public AgentTeamFailurePolicy failurePolicy() {
        return failurePolicy;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentTeamDefinition that)) {
            return false;
        }
        return teamId.equals(that.teamId) && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, updatedAt);
    }
}
