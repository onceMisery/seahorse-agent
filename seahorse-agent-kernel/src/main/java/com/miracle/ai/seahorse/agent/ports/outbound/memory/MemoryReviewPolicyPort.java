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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

@FunctionalInterface
public interface MemoryReviewPolicyPort {

    String REFINER_ADD_LOW_CONFIDENCE = "refiner_add_low_confidence";
    String REFINER_ADD_REVIEW_CONFIDENCE = "confidence_below_auto_commit";
    String REFINER_ADD_REVIEW_RISK = "risk_score_threshold";

    MemoryReviewPolicyDecision evaluateRefinedAdd(RefinedMemoryOperation operation, MemoryPolicyConfig policy);

    static MemoryReviewPolicyPort defaults() {
        return (operation, policy) -> {
            MemoryPolicyConfig activePolicy = policy == null ? MemoryPolicyConfig.defaults() : policy;
            if (operation == null) {
                return MemoryReviewPolicyDecision.drop(REFINER_ADD_LOW_CONFIDENCE);
            }
            if (operation.confidence() < activePolicy.refinerDropConfidenceThreshold()) {
                return MemoryReviewPolicyDecision.drop(REFINER_ADD_LOW_CONFIDENCE);
            }
            if (operation.riskScore() >= activePolicy.refinerReviewRiskThreshold()) {
                return MemoryReviewPolicyDecision.review(REFINER_ADD_REVIEW_RISK);
            }
            if (activePolicy.reviewEnabled()
                    && operation.confidence() < activePolicy.refinerAutoCommitConfidenceThreshold()) {
                return MemoryReviewPolicyDecision.review(REFINER_ADD_REVIEW_CONFIDENCE);
            }
            return MemoryReviewPolicyDecision.autoCommit();
        };
    }
}
