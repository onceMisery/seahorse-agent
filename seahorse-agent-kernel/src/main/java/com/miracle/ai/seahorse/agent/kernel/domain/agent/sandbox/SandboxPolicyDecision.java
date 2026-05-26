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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.util.Objects;

public record SandboxPolicyDecision(String decisionId,
                                    SandboxPolicyEffect effect,
                                    SandboxPolicyReasonCode reasonCode,
                                    String reasonMessage) {

    private static final String DEFAULT_DECISION_ID = "builtin-sandbox-policy";

    public SandboxPolicyDecision {
        decisionId = defaultText(decisionId, DEFAULT_DECISION_ID);
        effect = Objects.requireNonNullElse(effect, SandboxPolicyEffect.DENY);
        reasonCode = Objects.requireNonNullElse(reasonCode, SandboxPolicyReasonCode.DEFAULT_DENY);
        reasonMessage = defaultText(reasonMessage, reasonCode.name());
    }

    public static SandboxPolicyDecision allow(SandboxPolicyReasonCode reasonCode) {
        return new SandboxPolicyDecision(null, SandboxPolicyEffect.ALLOW, reasonCode, null);
    }

    public static SandboxPolicyDecision deny(SandboxPolicyReasonCode reasonCode) {
        return new SandboxPolicyDecision(null, SandboxPolicyEffect.DENY, reasonCode, null);
    }

    public boolean allowsExecution() {
        return effect == SandboxPolicyEffect.ALLOW;
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
