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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 7：验证 {@link MemoryCaptureRejectionReason} 的 wireValue 与
 * {@link MemoryCaptureCandidateExtractor#lastRejection()} 暴露行为；
 * 历史字符串契约必须保持。
 */
class MemoryCaptureRejectionReasonTests {

    private final MemoryCaptureCandidateExtractor extractor = new MemoryCaptureCandidateExtractor();

    @Test
    void wireValuesMatchHistoricalLowercaseTokens() {
        assertThat(MemoryCaptureRejectionReason.BLANK.wireValue()).isEqualTo("blank");
        assertThat(MemoryCaptureRejectionReason.TOO_SHORT.wireValue()).isEqualTo("too_short");
        assertThat(MemoryCaptureRejectionReason.TOO_LONG.wireValue()).isEqualTo("too_long");
        assertThat(MemoryCaptureRejectionReason.QUESTION.wireValue()).isEqualTo("question");
        assertThat(MemoryCaptureRejectionReason.NO_HIGH_VALUE_SIGNAL.wireValue())
                .isEqualTo("no_high_value_signal");
        assertThat(MemoryCaptureRejectionReason.SENSITIVE_CREDENTIAL.wireValue())
                .isEqualTo("sensitive_credential");
    }

    @Test
    void extractorExposesEnumForCallers() {
        extractor.extract("请记住：我的密码是 123456");

        assertThat(extractor.lastRejection())
                .isEqualTo(MemoryCaptureRejectionReason.SENSITIVE_CREDENTIAL);
        assertThat(extractor.lastRejectionReason()).isEqualTo("sensitive_credential");
    }

    @Test
    void extractorReturnsBlankRejectionOnEmptyContent() {
        extractor.extract("   ");

        assertThat(extractor.lastRejection()).isEqualTo(MemoryCaptureRejectionReason.BLANK);
        assertThat(extractor.lastRejectionReason()).isEqualTo("blank");
    }

    @Test
    void extractorReturnsNoHighValueOnNonSignalText() {
        extractor.extract("今天天气不错");

        assertThat(extractor.lastRejection())
                .isEqualTo(MemoryCaptureRejectionReason.NO_HIGH_VALUE_SIGNAL);
    }

    @Test
    void successfulExtractionClearsLastRejection() {
        extractor.extract("我是一名工程师");

        assertThat(extractor.lastRejection()).isNull();
        assertThat(extractor.lastRejectionReason()).isEmpty();
    }
}
