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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class MemoryValueAssessor {

    static final String POLICY_VERSION = "high_precision_rule_v1";

    private final MemoryPolicyConfigPort policyConfigPort;

    MemoryValueAssessor() {
        this(MemoryPolicyConfigPort.defaults());
    }

    MemoryValueAssessor(MemoryPolicyConfigPort policyConfigPort) {
        this.policyConfigPort = Objects.requireNonNullElseGet(policyConfigPort, MemoryPolicyConfigPort::defaults);
    }

    MemoryCaptureDecision assess(MemoryCaptureCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        MemoryPolicyConfig policy = policyConfigPort.current();
        List<String> reasons = new ArrayList<>();
        double riskScore = riskScore(candidate);
        if (riskScore >= policy.riskRejectThreshold()) {
            reasons.add("high_risk");
            return MemoryCaptureDecision.reject(candidate, 0D, riskScore, reasons);
        }

        double explicitness = candidate.explicitImportant() || candidate.signals().contains("explicit_memory_request")
                ? 1D : 0D;
        double profileValue = "PROFILE".equals(candidate.type()) ? 1D : 0D;
        double preferenceValue = "PREFERENCE".equals(candidate.type()) ? 1D : 0D;
        double personalFactValue = candidate.signals().contains("personal_fact") ? 0.85D : 0D;
        double stability = (profileValue > 0D || preferenceValue > 0D || personalFactValue > 0D) ? 0.85D : 0.2D;
        double specificity = candidate.content().length() >= 5 ? 0.8D : 0.3D;

        if (explicitness > 0D) {
            reasons.add(candidate.explicitImportant() ? "explicit_important" : "explicit_memory_request");
        }
        if (profileValue > 0D) {
            reasons.add("profile_value");
        }
        if (preferenceValue > 0D) {
            reasons.add("preference_value");
        }
        if (personalFactValue > 0D) {
            reasons.add("personal_fact_value");
        }

        double valueScore = (0.25D * explicitness)
                + (0.25D * Math.max(profileValue, personalFactValue))
                + (0.20D * preferenceValue)
                + (0.15D * stability)
                + (0.15D * specificity)
                - (0.30D * riskScore);
        if (candidate.explicitImportant()) {
            valueScore += 0.15D;
        }
        valueScore = clamp(valueScore);

        if (valueScore < policy.captureAcceptThreshold()) {
            reasons.add("low_value");
            return MemoryCaptureDecision.reject(candidate, valueScore, riskScore, reasons);
        }

        boolean highValue = valueScore >= policy.highValueThreshold() || candidate.explicitImportant();
        double importanceScore = highValue ? 0.80D : 0.60D;
        double confidenceLevel = highValue ? 0.90D : 0.80D;
        return new MemoryCaptureDecision(
                true,
                candidate.content(),
                candidate.type(),
                importanceScore,
                confidenceLevel,
                valueScore,
                riskScore,
                POLICY_VERSION,
                reasons,
                candidate.signals());
    }

    private double riskScore(MemoryCaptureCandidate candidate) {
        if (candidate.signals().contains("sensitive_credential")) {
            return 1D;
        }
        if (candidate.signals().contains("personal_expression")) {
            return 0.1D;
        }
        return 0D;
    }

    private double clamp(double value) {
        return Math.max(0D, Math.min(1D, value));
    }
}
