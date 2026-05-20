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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class MemoryCapturePolicyTests {

    @Test
    void shouldExtractExplainableProfileCandidateFromWhitespaceAndSocialTail() {
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor();

        Optional<MemoryCaptureCandidate> candidate = extractor.extract("我 是一名学生，很高兴认识你");

        Assertions.assertTrue(candidate.isPresent());
        Assertions.assertEquals("我是一名学生", candidate.get().content());
        Assertions.assertEquals("PROFILE", candidate.get().type());
        Assertions.assertTrue(candidate.get().signals().contains("profile_statement"));
        Assertions.assertTrue(candidate.get().signals().contains("normalized_chinese_whitespace"));
        Assertions.assertTrue(candidate.get().signals().contains("trimmed_social_tail"));
    }

    @Test
    void shouldRejectSensitiveExplicitCandidateWithReason() {
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor();

        Optional<MemoryCaptureCandidate> candidate = extractor.extract("请记住：我的密码是 123456");

        Assertions.assertTrue(candidate.isEmpty());
        Assertions.assertEquals("sensitive_credential", extractor.lastRejectionReason());
    }

    @Test
    void shouldAssessExplicitImportantCandidateWithHighValueAndReasonCodes() {
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor();
        MemoryValueAssessor assessor = new MemoryValueAssessor();

        MemoryCaptureCandidate candidate = extractor.extract("以后都按这个来：我喜欢简短回答").orElseThrow();
        MemoryCaptureDecision decision = assessor.assess(candidate);

        Assertions.assertTrue(decision.accepted());
        Assertions.assertEquals("PREFERENCE", decision.type());
        Assertions.assertEquals("我喜欢简短回答", decision.content());
        Assertions.assertTrue(decision.importanceScore() >= 0.75D);
        Assertions.assertTrue(decision.confidenceLevel() >= 0.85D);
        Assertions.assertTrue(decision.valueScore() >= 0.75D);
        Assertions.assertEquals("high_precision_rule_v1", decision.policyVersion());
        Assertions.assertTrue(decision.reasons().contains("explicit_important"));
        Assertions.assertTrue(decision.reasons().contains("preference_value"));
    }

    @Test
    void shouldRejectLowValueCandidateByAssessor() {
        MemoryValueAssessor assessor = new MemoryValueAssessor();

        MemoryCaptureDecision decision = assessor.assess(new MemoryCaptureCandidate(
                "我的天这个太难了",
                "FACT",
                false,
                java.util.List.of("personal_expression")));

        Assertions.assertFalse(decision.accepted());
        Assertions.assertTrue(decision.reasons().contains("low_value"));
    }
}
