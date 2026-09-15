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

package com.miracle.ai.seahorse.agent.adapters.spring.properties;

import com.miracle.ai.seahorse.agent.kernel.policy.MemoryExtractionPolicy;
import com.miracle.ai.seahorse.agent.kernel.policy.MemoryRoutingPolicy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 记忆策略配置（前缀 {@code seahorse-agent.memory-policy}）。
 *
 * <p>对应内核的 {@link MemoryRoutingPolicy} 与 {@link MemoryExtractionPolicy}：路由判定词表与规则抽取
 * 的正则、置信度。未配置的项沿用内核默认值，默认值只在内核定义一次。
 */
@ConfigurationProperties(prefix = "seahorse-agent.memory-policy")
public class MemoryPolicyProperties {

    private final Routing routing = new Routing();
    private final Extraction extraction = new Extraction();

    public Routing getRouting() {
        return routing;
    }

    public Extraction getExtraction() {
        return extraction;
    }

    public MemoryRoutingPolicy toRoutingPolicy() {
        MemoryRoutingPolicy.Builder builder = MemoryRoutingPolicy.builder();
        if (notEmpty(routing.getCorrectionKeywords())) {
            builder.correctionKeywords(routing.getCorrectionKeywords());
        }
        if (notEmpty(routing.getCorrectionKeywordsLatin())) {
            builder.correctionKeywordsLatin(routing.getCorrectionKeywordsLatin());
        }
        if (notEmpty(routing.getBusinessKeywords())) {
            builder.businessKeywords(routing.getBusinessKeywords());
        }
        if (notEmpty(routing.getBusinessKeywordsLatin())) {
            builder.businessKeywordsLatin(routing.getBusinessKeywordsLatin());
        }
        if (notEmpty(routing.getEpisodicKeywords())) {
            builder.episodicKeywords(routing.getEpisodicKeywords());
        }
        if (notEmpty(routing.getEpisodicKeywordsLatin())) {
            builder.episodicKeywordsLatin(routing.getEpisodicKeywordsLatin());
        }
        return builder.build();
    }

    public MemoryExtractionPolicy toExtractionPolicy() {
        MemoryExtractionPolicy.Builder builder = MemoryExtractionPolicy.builder();
        if (notBlank(extraction.getProfilePattern())) {
            builder.profilePattern(extraction.getProfilePattern());
        }
        if (notBlank(extraction.getPreferencePattern())) {
            builder.preferencePattern(extraction.getPreferencePattern());
        }
        if (notBlank(extraction.getNeedPattern())) {
            builder.needPattern(extraction.getNeedPattern());
        }
        if (extraction.getProfileConfidence() != null) {
            builder.profileConfidence(extraction.getProfileConfidence());
        }
        if (extraction.getPreferenceConfidence() != null) {
            builder.preferenceConfidence(extraction.getPreferenceConfidence());
        }
        if (extraction.getMinValueLength() != null) {
            builder.minValueLength(extraction.getMinValueLength());
        }
        return builder.build();
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 记忆路由词表；中文词表按原文匹配，拉丁词表按小写文本匹配。
     */
    public static class Routing {
        private List<String> correctionKeywords;
        private List<String> correctionKeywordsLatin;
        private List<String> businessKeywords;
        private List<String> businessKeywordsLatin;
        private List<String> episodicKeywords;
        private List<String> episodicKeywordsLatin;

        public List<String> getCorrectionKeywords() {
            return correctionKeywords;
        }

        public void setCorrectionKeywords(List<String> correctionKeywords) {
            this.correctionKeywords = correctionKeywords;
        }

        public List<String> getCorrectionKeywordsLatin() {
            return correctionKeywordsLatin;
        }

        public void setCorrectionKeywordsLatin(List<String> correctionKeywordsLatin) {
            this.correctionKeywordsLatin = correctionKeywordsLatin;
        }

        public List<String> getBusinessKeywords() {
            return businessKeywords;
        }

        public void setBusinessKeywords(List<String> businessKeywords) {
            this.businessKeywords = businessKeywords;
        }

        public List<String> getBusinessKeywordsLatin() {
            return businessKeywordsLatin;
        }

        public void setBusinessKeywordsLatin(List<String> businessKeywordsLatin) {
            this.businessKeywordsLatin = businessKeywordsLatin;
        }

        public List<String> getEpisodicKeywords() {
            return episodicKeywords;
        }

        public void setEpisodicKeywords(List<String> episodicKeywords) {
            this.episodicKeywords = episodicKeywords;
        }

        public List<String> getEpisodicKeywordsLatin() {
            return episodicKeywordsLatin;
        }

        public void setEpisodicKeywordsLatin(List<String> episodicKeywordsLatin) {
            this.episodicKeywordsLatin = episodicKeywordsLatin;
        }
    }

    /**
     * 规则式记忆抽取的正则与置信度。
     */
    public static class Extraction {
        private String profilePattern;
        private String preferencePattern;
        private String needPattern;
        private Double profileConfidence;
        private Double preferenceConfidence;
        private Integer minValueLength;

        public String getProfilePattern() {
            return profilePattern;
        }

        public void setProfilePattern(String profilePattern) {
            this.profilePattern = profilePattern;
        }

        public String getPreferencePattern() {
            return preferencePattern;
        }

        public void setPreferencePattern(String preferencePattern) {
            this.preferencePattern = preferencePattern;
        }

        public String getNeedPattern() {
            return needPattern;
        }

        public void setNeedPattern(String needPattern) {
            this.needPattern = needPattern;
        }

        public Double getProfileConfidence() {
            return profileConfidence;
        }

        public void setProfileConfidence(Double profileConfidence) {
            this.profileConfidence = profileConfidence;
        }

        public Double getPreferenceConfidence() {
            return preferenceConfidence;
        }

        public void setPreferenceConfidence(Double preferenceConfidence) {
            this.preferenceConfidence = preferenceConfidence;
        }

        public Integer getMinValueLength() {
            return minValueLength;
        }

        public void setMinValueLength(Integer minValueLength) {
            this.minValueLength = minValueLength;
        }
    }
}
