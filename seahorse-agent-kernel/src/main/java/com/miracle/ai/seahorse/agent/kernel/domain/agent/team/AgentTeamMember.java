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

import java.util.Objects;

/**
 * 团队成员：团队内一个被委托执行的 Agent 及其职责。
 *
 * @param memberId    团队内唯一成员标识
 * @param agentId     被委托的 Agent 定义 ID
 * @param role        成员职责描述（如 researcher / writer）
 * @param instruction 该成员的固定补充指令，分派时拼入子任务说明
 */
public record AgentTeamMember(String memberId,
                              String agentId,
                              String role,
                              String instruction) {

    public AgentTeamMember {
        memberId = requireText(memberId, "memberId 不能为空");
        agentId = requireText(agentId, "member agentId 不能为空");
        role = normalize(role);
        instruction = normalize(instruction);
    }

    public boolean matchesAgent(String candidateAgentId) {
        return agentId.equals(candidateAgentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentTeamMember other)) {
            return false;
        }
        return memberId.equals(other.memberId) && agentId.equals(other.agentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, agentId);
    }

    static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }
}
