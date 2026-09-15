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

import com.miracle.ai.seahorse.agent.kernel.policy.SkillMatchPolicy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skill 匹配策略配置（前缀 {@code seahorse-agent.skill-match}）。
 *
 * <p>此前这些阈值与权重是匹配器里的 {@code private static final} 常量，调整必须改代码发版。
 * 现在按属性绑定，未配置的项保持内核默认值——默认值只在此处之外的一处定义
 * （{@link SkillMatchPolicy#defaults()}），因此本类的字段一律允许为 {@code null}。
 */
@ConfigurationProperties(prefix = "seahorse-agent.skill-match")
public class SkillMatchProperties {

    /** 关键词匹配：最低入选分数。 */
    private Double minScoreThreshold;
    /** 关键词匹配：标签命中权重。 */
    private Double tagWeight;
    /** 关键词匹配：描述命中权重。 */
    private Double descriptionWeight;
    /** 关键词匹配：名称命中权重。 */
    private Double nameWeight;

    /** 语义匹配：向量相似度下限，低于该值不进入混合评分。 */
    private Float minSimilarityThreshold;
    /** 语义匹配：混合评分最低入选分数。 */
    private Float minHybridScoreThreshold;
    /** 语义匹配：向量相似度在混合评分中的权重。 */
    private Float vectorWeight;
    /** 语义匹配：规则增强在混合评分中的权重。 */
    private Float ruleWeight;
    /** 语义匹配：规则命中达到该分数时视为强命中。 */
    private Float strongRuleMatchThreshold;
    /** 语义匹配：强命中的加成。 */
    private Float strongRuleMatchBoost;

    /** 关键词匹配停用词，覆盖内核默认值。 */
    private Set<String> stopWords;
    /** 语义匹配停用词，覆盖内核默认值。 */
    private Set<String> semanticStopWords;
    /** 领域关键词映射（领域 -> 同义词表），覆盖内核默认值。 */
    private Map<String, List<String>> domainKeywords;

    public Double getMinScoreThreshold() {
        return minScoreThreshold;
    }

    public void setMinScoreThreshold(Double minScoreThreshold) {
        this.minScoreThreshold = minScoreThreshold;
    }

    public Double getTagWeight() {
        return tagWeight;
    }

    public void setTagWeight(Double tagWeight) {
        this.tagWeight = tagWeight;
    }

    public Double getDescriptionWeight() {
        return descriptionWeight;
    }

    public void setDescriptionWeight(Double descriptionWeight) {
        this.descriptionWeight = descriptionWeight;
    }

    public Double getNameWeight() {
        return nameWeight;
    }

    public void setNameWeight(Double nameWeight) {
        this.nameWeight = nameWeight;
    }

    public Float getMinSimilarityThreshold() {
        return minSimilarityThreshold;
    }

    public void setMinSimilarityThreshold(Float minSimilarityThreshold) {
        this.minSimilarityThreshold = minSimilarityThreshold;
    }

    public Float getMinHybridScoreThreshold() {
        return minHybridScoreThreshold;
    }

    public void setMinHybridScoreThreshold(Float minHybridScoreThreshold) {
        this.minHybridScoreThreshold = minHybridScoreThreshold;
    }

    public Float getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(Float vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public Float getRuleWeight() {
        return ruleWeight;
    }

    public void setRuleWeight(Float ruleWeight) {
        this.ruleWeight = ruleWeight;
    }

    public Float getStrongRuleMatchThreshold() {
        return strongRuleMatchThreshold;
    }

    public void setStrongRuleMatchThreshold(Float strongRuleMatchThreshold) {
        this.strongRuleMatchThreshold = strongRuleMatchThreshold;
    }

    public Float getStrongRuleMatchBoost() {
        return strongRuleMatchBoost;
    }

    public void setStrongRuleMatchBoost(Float strongRuleMatchBoost) {
        this.strongRuleMatchBoost = strongRuleMatchBoost;
    }

    public Set<String> getStopWords() {
        return stopWords;
    }

    public void setStopWords(Set<String> stopWords) {
        this.stopWords = stopWords;
    }

    public Set<String> getSemanticStopWords() {
        return semanticStopWords;
    }

    public void setSemanticStopWords(Set<String> semanticStopWords) {
        this.semanticStopWords = semanticStopWords;
    }

    public Map<String, List<String>> getDomainKeywords() {
        return domainKeywords;
    }

    public void setDomainKeywords(Map<String, List<String>> domainKeywords) {
        this.domainKeywords = domainKeywords;
    }

    /**
     * 转换为内核策略载体；未显式配置的项沿用 {@link SkillMatchPolicy#defaults()}。
     */
    public SkillMatchPolicy toPolicy() {
        SkillMatchPolicy.Builder builder = SkillMatchPolicy.builder();
        if (minScoreThreshold != null) {
            builder.minScoreThreshold(minScoreThreshold);
        }
        if (tagWeight != null) {
            builder.tagWeight(tagWeight);
        }
        if (descriptionWeight != null) {
            builder.descriptionWeight(descriptionWeight);
        }
        if (nameWeight != null) {
            builder.nameWeight(nameWeight);
        }
        if (minSimilarityThreshold != null) {
            builder.minSimilarityThreshold(minSimilarityThreshold);
        }
        if (minHybridScoreThreshold != null) {
            builder.minHybridScoreThreshold(minHybridScoreThreshold);
        }
        if (vectorWeight != null) {
            builder.vectorWeight(vectorWeight);
        }
        if (ruleWeight != null) {
            builder.ruleWeight(ruleWeight);
        }
        if (strongRuleMatchThreshold != null) {
            builder.strongRuleMatchThreshold(strongRuleMatchThreshold);
        }
        if (strongRuleMatchBoost != null) {
            builder.strongRuleMatchBoost(strongRuleMatchBoost);
        }
        if (stopWords != null && !stopWords.isEmpty()) {
            builder.stopWords(stopWords);
        }
        if (semanticStopWords != null && !semanticStopWords.isEmpty()) {
            builder.semanticStopWords(semanticStopWords);
        }
        if (domainKeywords != null && !domainKeywords.isEmpty()) {
            builder.domainKeywords(domainKeywords);
        }
        return builder.build();
    }
}
