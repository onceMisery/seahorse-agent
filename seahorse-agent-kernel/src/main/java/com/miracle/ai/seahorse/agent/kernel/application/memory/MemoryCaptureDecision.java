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

import java.util.List;
import java.util.Objects;

record MemoryCaptureDecision(boolean accepted,
                             String content,
                             String type,
                             double importanceScore,
                             double confidenceLevel,
                             double valueScore,
                             double riskScore,
                             String policyVersion,
                             List<String> reasons,
                             List<String> signals) {

    static MemoryCaptureDecision reject(MemoryCaptureCandidate candidate,
                                        double valueScore,
                                        double riskScore,
                                        List<String> reasons) {
        return new MemoryCaptureDecision(
                false,
                candidate == null ? "" : candidate.content(),
                candidate == null ? "FACT" : candidate.type(),
                0D,
                0D,
                valueScore,
                riskScore,
                MemoryValueAssessor.POLICY_VERSION,
                reasons,
                candidate == null ? List.of() : candidate.signals());
    }

    static MemoryCaptureDecision refinedAdd(String content,
                                            String type,
                                            double importanceScore,
                                            double confidenceLevel,
                                            double valueScore,
                                            double riskScore,
                                            List<String> reasons,
                                            List<String> signals) {
        return new MemoryCaptureDecision(
                true,
                content,
                type,
                importanceScore,
                confidenceLevel,
                valueScore,
                riskScore,
                "llm_refiner_v1",
                reasons,
                signals);
    }

    MemoryCaptureDecision {
        content = Objects.requireNonNullElse(content, "").trim();
        type = Objects.requireNonNullElse(type, "FACT");
        policyVersion = Objects.requireNonNullElse(policyVersion, MemoryValueAssessor.POLICY_VERSION);
        reasons = List.copyOf(Objects.requireNonNullElse(reasons, List.of()));
        signals = List.copyOf(Objects.requireNonNullElse(signals, List.of()));
    }
}
