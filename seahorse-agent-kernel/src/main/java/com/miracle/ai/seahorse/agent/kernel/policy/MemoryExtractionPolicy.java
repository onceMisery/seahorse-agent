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

package com.miracle.ai.seahorse.agent.kernel.policy;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 规则式记忆候选抽取策略：正则模式、置信度与最小取值长度。
 *
 * <p>这些模式与置信度此前写死在 {@code RuleBasedMemoryCandidateExtractor} 中；置信度是拍定的常数
 * （0.75 / 0.7），并不反映模型或统计得出的事实可靠程度。抽成载体后可按租户调整。
 */
public final class MemoryExtractionPolicy {

    private static final String DEFAULT_PROFILE_PATTERN = "我(?:是|在|做|从事|负责|擅长|来自)(.{2,30})";
    private static final String DEFAULT_PREFERENCE_PATTERN = "我(?:喜欢|偏好|习惯|常用|prefer|like)(.{2,30})";
    /** 需求声明模式；当前抽取器尚未启用该模式，保留以便按配置打开。 */
    private static final String DEFAULT_NEED_PATTERN = "我(?:需要|想要|希望|想|要)(.{2,30})";

    private static final double DEFAULT_PROFILE_CONFIDENCE = 0.75D;
    private static final double DEFAULT_PREFERENCE_CONFIDENCE = 0.7D;
    private static final int DEFAULT_MIN_VALUE_LENGTH = 2;

    private final Pattern profilePattern;
    private final Pattern preferencePattern;
    private final Pattern needPattern;
    private final double profileConfidence;
    private final double preferenceConfidence;
    private final int minValueLength;

    private MemoryExtractionPolicy(Builder b) {
        this.profilePattern = compile(b.profilePattern, "profilePattern");
        this.preferencePattern = compile(b.preferencePattern, "preferencePattern");
        this.needPattern = compile(b.needPattern, "needPattern");
        this.profileConfidence = requireConfidence(b.profileConfidence, "profileConfidence");
        this.preferenceConfidence = requireConfidence(b.preferenceConfidence, "preferenceConfidence");
        if (b.minValueLength < 0) {
            throw new IllegalArgumentException("minValueLength 必须 >= 0");
        }
        this.minValueLength = b.minValueLength;
    }

    public Pattern profilePattern() {
        return profilePattern;
    }

    public Pattern preferencePattern() {
        return preferencePattern;
    }

    public Pattern needPattern() {
        return needPattern;
    }

    public double profileConfidence() {
        return profileConfidence;
    }

    public double preferenceConfidence() {
        return preferenceConfidence;
    }

    public int minValueLength() {
        return minValueLength;
    }

    public static MemoryExtractionPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Pattern compile(String regex, String name) {
        Objects.requireNonNull(regex, name + " must not be null");
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException(name + " 不是合法正则: " + regex, ex);
        }
    }

    private static double requireConfidence(double value, String name) {
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " 必须在 [0, 1] 之间");
        }
        return value;
    }

    public static final class Builder {
        private String profilePattern = DEFAULT_PROFILE_PATTERN;
        private String preferencePattern = DEFAULT_PREFERENCE_PATTERN;
        private String needPattern = DEFAULT_NEED_PATTERN;
        private double profileConfidence = DEFAULT_PROFILE_CONFIDENCE;
        private double preferenceConfidence = DEFAULT_PREFERENCE_CONFIDENCE;
        private int minValueLength = DEFAULT_MIN_VALUE_LENGTH;

        public Builder profilePattern(String profilePattern) {
            this.profilePattern = profilePattern;
            return this;
        }

        public Builder preferencePattern(String preferencePattern) {
            this.preferencePattern = preferencePattern;
            return this;
        }

        public Builder needPattern(String needPattern) {
            this.needPattern = needPattern;
            return this;
        }

        public Builder profileConfidence(double profileConfidence) {
            this.profileConfidence = profileConfidence;
            return this;
        }

        public Builder preferenceConfidence(double preferenceConfidence) {
            this.preferenceConfidence = preferenceConfidence;
            return this;
        }

        public Builder minValueLength(int minValueLength) {
            this.minValueLength = minValueLength;
            return this;
        }

        public MemoryExtractionPolicy build() {
            return new MemoryExtractionPolicy(this);
        }
    }
}
