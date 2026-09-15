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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Skill 匹配策略：关键词匹配与语义匹配用到的阈值、权重与词表。
 *
 * <p>此前这些数值以 {@code private static final} 形式散落在
 * {@code SkillSmartMatcher} 与 {@code SkillSemanticMatcher} 中，调整任一权重都必须改代码重新发版。
 * 抽成载体后由装配层（Spring Boot autoconfigure）绑定外部配置，内核本身不依赖任何配置框架。
 */
public final class SkillMatchPolicy {

    private static final double DEFAULT_MIN_SCORE_THRESHOLD = 0.1;
    private static final double DEFAULT_TAG_WEIGHT = 0.5;
    private static final double DEFAULT_DESCRIPTION_WEIGHT = 0.3;
    private static final double DEFAULT_NAME_WEIGHT = 0.2;

    private static final float DEFAULT_MIN_SIMILARITY_THRESHOLD = 0.6f;
    private static final float DEFAULT_MIN_HYBRID_SCORE_THRESHOLD = 0.1f;
    private static final float DEFAULT_VECTOR_WEIGHT = 0.7f;
    private static final float DEFAULT_RULE_WEIGHT = 0.3f;
    private static final float DEFAULT_STRONG_RULE_MATCH_THRESHOLD = 0.3f;
    private static final float DEFAULT_STRONG_RULE_MATCH_BOOST = 0.35f;

    private static final Set<String> DEFAULT_STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "我", "你", "他", "她", "它", "我们", "你们", "他们",
            "的", "了", "是", "在", "有", "这", "那", "个", "上", "中", "下",
            "请", "帮", "帮我", "能", "可以", "需要", "想", "要"
    );

    /** 语义匹配器用于规则增强的停用词；与关键词匹配器的停用词来源不同，故分别保留。 */
    private static final Set<String> DEFAULT_SEMANTIC_STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "我", "你", "他", "她", "它", "的", "了", "是", "在", "有", "这", "那", "个",
            "请", "帮", "帮我", "能", "可以", "需要", "想", "要"
    );

    private static final Map<String, List<String>> DEFAULT_DOMAIN_KEYWORDS = Map.of(
            "research", List.of("研究", "调研", "调查", "探索", "study", "investigate", "analyze", "explore"),
            "data", List.of("数据", "统计", "可视化", "图表", "表格", "data", "statistics", "visualization", "chart"),
            "code", List.of("代码", "编程", "开发", "重构", "review", "code", "programming", "refactor", "debug"),
            "document", List.of("文档", "说明", "手册", "注释", "document", "documentation", "manual", "comment"),
            "test", List.of("测试", "验证", "质量", "QA", "test", "testing", "quality", "verify"),
            "design", List.of("设计", "架构", "UX", "UI", "原型", "design", "architecture", "prototype"),
            "translate", List.of("翻译", "转换", "translate", "conversion", "transform"),
            "summary", List.of("总结", "摘要", "概括", "汇总", "summary", "abstract", "summarize")
    );

    private final double minScoreThreshold;
    private final double tagWeight;
    private final double descriptionWeight;
    private final double nameWeight;

    private final float minSimilarityThreshold;
    private final float minHybridScoreThreshold;
    private final float vectorWeight;
    private final float ruleWeight;
    private final float strongRuleMatchThreshold;
    private final float strongRuleMatchBoost;

    private final Set<String> stopWords;
    private final Set<String> semanticStopWords;
    private final Map<String, List<String>> domainKeywords;

    private SkillMatchPolicy(Builder b) {
        this.minScoreThreshold = requireAtLeastZero(b.minScoreThreshold, "minScoreThreshold");
        this.tagWeight = requireUnitWeight(b.tagWeight, "tagWeight");
        this.descriptionWeight = requireUnitWeight(b.descriptionWeight, "descriptionWeight");
        this.nameWeight = requireUnitWeight(b.nameWeight, "nameWeight");

        this.minSimilarityThreshold = requireAtLeastZero(b.minSimilarityThreshold, "minSimilarityThreshold");
        this.minHybridScoreThreshold = requireAtLeastZero(b.minHybridScoreThreshold, "minHybridScoreThreshold");
        this.vectorWeight = requireUnitWeight(b.vectorWeight, "vectorWeight");
        this.ruleWeight = requireUnitWeight(b.ruleWeight, "ruleWeight");
        this.strongRuleMatchThreshold = requireAtLeastZero(b.strongRuleMatchThreshold, "strongRuleMatchThreshold");
        this.strongRuleMatchBoost = requireAtLeastZero(b.strongRuleMatchBoost, "strongRuleMatchBoost");

        this.stopWords = Set.copyOf(Objects.requireNonNull(b.stopWords, "stopWords must not be null"));
        this.semanticStopWords = Set.copyOf(
                Objects.requireNonNull(b.semanticStopWords, "semanticStopWords must not be null"));
        Map<String, List<String>> copied = new LinkedHashMap<>();
        b.domainKeywords.forEach((domain, terms) ->
                copied.put(domain, List.copyOf(Objects.requireNonNull(terms, "domainKeywords terms"))));
        this.domainKeywords = Map.copyOf(copied);
    }

    public double minScoreThreshold() {
        return minScoreThreshold;
    }

    public double tagWeight() {
        return tagWeight;
    }

    public double descriptionWeight() {
        return descriptionWeight;
    }

    public double nameWeight() {
        return nameWeight;
    }

    public float minSimilarityThreshold() {
        return minSimilarityThreshold;
    }

    public float minHybridScoreThreshold() {
        return minHybridScoreThreshold;
    }

    public float vectorWeight() {
        return vectorWeight;
    }

    public float ruleWeight() {
        return ruleWeight;
    }

    public float strongRuleMatchThreshold() {
        return strongRuleMatchThreshold;
    }

    public float strongRuleMatchBoost() {
        return strongRuleMatchBoost;
    }

    public Set<String> stopWords() {
        return stopWords;
    }

    public Set<String> semanticStopWords() {
        return semanticStopWords;
    }

    public Map<String, List<String>> domainKeywords() {
        return domainKeywords;
    }

    public static SkillMatchPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static double requireAtLeastZero(double value, String name) {
        if (Double.isNaN(value) || value < 0) {
            throw new IllegalArgumentException(name + " 必须 >= 0");
        }
        return value;
    }

    private static float requireAtLeastZero(float value, String name) {
        if (Float.isNaN(value) || value < 0) {
            throw new IllegalArgumentException(name + " 必须 >= 0");
        }
        return value;
    }

    private static float requireUnitWeight(float value, String name) {
        if (Float.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " 必须在 [0, 1] 之间");
        }
        return value;
    }

    private static double requireUnitWeight(double value, String name) {
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " 必须在 [0, 1] 之间");
        }
        return value;
    }

    public static final class Builder {
        private double minScoreThreshold = DEFAULT_MIN_SCORE_THRESHOLD;
        private double tagWeight = DEFAULT_TAG_WEIGHT;
        private double descriptionWeight = DEFAULT_DESCRIPTION_WEIGHT;
        private double nameWeight = DEFAULT_NAME_WEIGHT;

        private float minSimilarityThreshold = DEFAULT_MIN_SIMILARITY_THRESHOLD;
        private float minHybridScoreThreshold = DEFAULT_MIN_HYBRID_SCORE_THRESHOLD;
        private float vectorWeight = DEFAULT_VECTOR_WEIGHT;
        private float ruleWeight = DEFAULT_RULE_WEIGHT;
        private float strongRuleMatchThreshold = DEFAULT_STRONG_RULE_MATCH_THRESHOLD;
        private float strongRuleMatchBoost = DEFAULT_STRONG_RULE_MATCH_BOOST;

        private Set<String> stopWords = DEFAULT_STOP_WORDS;
        private Set<String> semanticStopWords = DEFAULT_SEMANTIC_STOP_WORDS;
        private Map<String, List<String>> domainKeywords = DEFAULT_DOMAIN_KEYWORDS;

        public Builder minScoreThreshold(double minScoreThreshold) {
            this.minScoreThreshold = minScoreThreshold;
            return this;
        }

        public Builder tagWeight(double tagWeight) {
            this.tagWeight = tagWeight;
            return this;
        }

        public Builder descriptionWeight(double descriptionWeight) {
            this.descriptionWeight = descriptionWeight;
            return this;
        }

        public Builder nameWeight(double nameWeight) {
            this.nameWeight = nameWeight;
            return this;
        }

        public Builder minSimilarityThreshold(float minSimilarityThreshold) {
            this.minSimilarityThreshold = minSimilarityThreshold;
            return this;
        }

        public Builder minHybridScoreThreshold(float minHybridScoreThreshold) {
            this.minHybridScoreThreshold = minHybridScoreThreshold;
            return this;
        }

        public Builder vectorWeight(float vectorWeight) {
            this.vectorWeight = vectorWeight;
            return this;
        }

        public Builder ruleWeight(float ruleWeight) {
            this.ruleWeight = ruleWeight;
            return this;
        }

        public Builder strongRuleMatchThreshold(float strongRuleMatchThreshold) {
            this.strongRuleMatchThreshold = strongRuleMatchThreshold;
            return this;
        }

        public Builder strongRuleMatchBoost(float strongRuleMatchBoost) {
            this.strongRuleMatchBoost = strongRuleMatchBoost;
            return this;
        }

        public Builder stopWords(Set<String> stopWords) {
            this.stopWords = stopWords;
            return this;
        }

        public Builder semanticStopWords(Set<String> semanticStopWords) {
            this.semanticStopWords = semanticStopWords;
            return this;
        }

        public Builder domainKeywords(Map<String, List<String>> domainKeywords) {
            this.domainKeywords = domainKeywords;
            return this;
        }

        public SkillMatchPolicy build() {
            return new SkillMatchPolicy(this);
        }
    }
}
