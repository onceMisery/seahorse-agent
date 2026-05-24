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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 7 续：验证 {@link MemoryCaptureRules} 的注入真正改变
 * {@link MemoryCaptureCandidateExtractor} 行为。
 */
class MemoryCaptureRulesIntegrationTests {

    @Test
    void rulesDefaultsPreserveHistoricalBehavior() {
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor();

        // 历史断言：默认拒绝太短，最小长度 2
        extractor.extract("我");
        assertThat(extractor.lastRejection()).isEqualTo(MemoryCaptureRejectionReason.NO_HIGH_VALUE_SIGNAL);

        // "我是一名工程师" 走 profile_statement 路径并通过
        Optional<MemoryCaptureCandidate> candidate = extractor.extract("我是一名工程师");
        assertThat(candidate).isPresent();
        assertThat(candidate.get().type()).isEqualTo("PROFILE");
    }

    @Test
    void customProfilePrefixesEnableNewLanguageBranch() {
        MemoryCaptureRules rules = new MemoryCaptureRules(
                MemoryCaptureRules.DEFAULT_MIN_CANDIDATE_LENGTH,
                MemoryCaptureRules.DEFAULT_MAX_CANDIDATE_LENGTH,
                List.of("I am ", "I work at "),
                MemoryCaptureRules.DEFAULT_PREFERENCE_STATEMENT_PREFIXES,
                MemoryCaptureRules.DEFAULT_QUESTION_ENDING_CHARACTERS,
                MemoryCaptureRules.DEFAULT_QUESTION_CONTAINS_KEYWORDS);
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor(rules);

        Optional<MemoryCaptureCandidate> candidate = extractor.extract("I am an engineer");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().signals()).contains("profile_statement");
    }

    @Test
    void customMaxLengthShortensRejectionThreshold() {
        MemoryCaptureRules rules = new MemoryCaptureRules(
                2,
                5,
                MemoryCaptureRules.DEFAULT_PROFILE_STATEMENT_PREFIXES,
                MemoryCaptureRules.DEFAULT_PREFERENCE_STATEMENT_PREFIXES,
                MemoryCaptureRules.DEFAULT_QUESTION_ENDING_CHARACTERS,
                MemoryCaptureRules.DEFAULT_QUESTION_CONTAINS_KEYWORDS);
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor(rules);

        extractor.extract("我是非常资深的工程师");

        assertThat(extractor.lastRejection()).isEqualTo(MemoryCaptureRejectionReason.TOO_LONG);
    }

    @Test
    void customQuestionKeywordsBlockNewPatterns() {
        MemoryCaptureRules rules = new MemoryCaptureRules(
                MemoryCaptureRules.DEFAULT_MIN_CANDIDATE_LENGTH,
                MemoryCaptureRules.DEFAULT_MAX_CANDIDATE_LENGTH,
                MemoryCaptureRules.DEFAULT_PROFILE_STATEMENT_PREFIXES,
                MemoryCaptureRules.DEFAULT_PREFERENCE_STATEMENT_PREFIXES,
                MemoryCaptureRules.DEFAULT_QUESTION_ENDING_CHARACTERS,
                List.of("能不能"));
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor(rules);

        extractor.extract("我是一个能不能这样的人");

        assertThat(extractor.lastRejection()).isEqualTo(MemoryCaptureRejectionReason.QUESTION);
    }

    @Test
    void nullRulesFallbackToDefaults() {
        MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor(null);
        assertThat(extractor.rules()).isNotNull();
        assertThat(extractor.rules().minCandidateLength())
                .isEqualTo(MemoryCaptureRules.DEFAULT_MIN_CANDIDATE_LENGTH);
    }
}
