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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.policy;

import java.util.Objects;

/**
 * 工具策略裁决结果。
 *
 * @param decisionId         单次策略裁决 ID，后续审计表会用它串联请求、裁决和执行结果
 * @param effect             裁决效果，决定 Gateway 是否可以继续执行真实工具
 * @param reasonCode         机器可读原因码，建议用于审计、前端展示映射和自动化告警
 * @param reasonMessage      人类可读原因说明
 * @param policyId           命中的策略 ID；内置规则可使用固定 ID
 * @param policyVersion      策略版本，用于回放历史裁决
 * @param approvalPolicyId   需要人工审批时的审批策略 ID
 * @param redactionRulesJson 需要脱敏时的规则 JSON，后续输出脱敏阶段使用
 */
public record PolicyDecision(String decisionId,
                             Effect effect,
                             String reasonCode,
                             String reasonMessage,
                             String policyId,
                             String policyVersion,
                             String approvalPolicyId,
                             String redactionRulesJson) {

    public static final String BUILTIN_POLICY_ID = "builtin-tool-policy";
    public static final String BUILTIN_POLICY_VERSION = "1";

    public enum Effect {
        ALLOW,
        DENY,
        APPROVAL_REQUIRED,
        REDACT,
        SANDBOX_REQUIRED
    }

    public PolicyDecision {
        decisionId = defaultText(decisionId, "builtin");
        effect = Objects.requireNonNullElse(effect, Effect.DENY);
        reasonCode = defaultText(reasonCode, effect.name());
        reasonMessage = defaultText(reasonMessage, reasonCode);
        policyId = defaultText(policyId, BUILTIN_POLICY_ID);
        policyVersion = defaultText(policyVersion, BUILTIN_POLICY_VERSION);
        approvalPolicyId = trimToNull(approvalPolicyId);
        redactionRulesJson = trimToNull(redactionRulesJson);
    }

    /**
     * 当前 Gateway 最小实现只允许 ALLOW 继续执行真实工具；其他效果先作为拦截结果返回。
     */
    public boolean allowsExecution() {
        return effect == Effect.ALLOW;
    }

    public static PolicyDecision allow(String decisionId) {
        return new PolicyDecision(decisionId, Effect.ALLOW, ToolPolicyReasonCodes.ALLOW, "Allowed",
                BUILTIN_POLICY_ID, BUILTIN_POLICY_VERSION, null, null);
    }

    public static PolicyDecision deny(String decisionId, String reasonCode, String reasonMessage) {
        return new PolicyDecision(decisionId, Effect.DENY, reasonCode, reasonMessage,
                BUILTIN_POLICY_ID, BUILTIN_POLICY_VERSION, null, null);
    }

    public static PolicyDecision approvalRequired(String decisionId, String reasonCode, String reasonMessage) {
        return new PolicyDecision(decisionId, Effect.APPROVAL_REQUIRED, reasonCode, reasonMessage,
                BUILTIN_POLICY_ID, BUILTIN_POLICY_VERSION, "builtin-approval", null);
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
