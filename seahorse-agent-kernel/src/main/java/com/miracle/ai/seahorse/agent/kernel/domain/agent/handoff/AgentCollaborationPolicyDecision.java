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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff;

import java.util.Objects;

/**
 * 协作授权决策结果。
 *
 * @param allowed     是否允许本次 handoff
 * @param failureCode 拒绝时的失败码（allowed 为 true 时为 null）
 * @param reason      可解释的决策原因（审计与运维展示用，不含敏感值）
 */
public record AgentCollaborationPolicyDecision(boolean allowed,
                                               AgentHandoffFailureCode failureCode,
                                               String reason) {

    private static final AgentCollaborationPolicyDecision ALLOWED =
            new AgentCollaborationPolicyDecision(true, null, "no explicit collaboration policy");

    public AgentCollaborationPolicyDecision {
        reason = AgentCollaborationPolicy.normalizeOf(reason);
        if (!allowed) {
            Objects.requireNonNull(failureCode, "denied decision requires a failureCode");
        }
    }

    public static AgentCollaborationPolicyDecision allow(String reason) {
        return reason == null || reason.isBlank() ? ALLOWED
                : new AgentCollaborationPolicyDecision(true, null, reason);
    }

    public static AgentCollaborationPolicyDecision deny(AgentHandoffFailureCode failureCode, String reason) {
        return new AgentCollaborationPolicyDecision(false, failureCode, reason);
    }
}
