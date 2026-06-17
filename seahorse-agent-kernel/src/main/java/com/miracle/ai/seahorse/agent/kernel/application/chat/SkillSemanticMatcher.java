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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort.SkillSearchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于向量语义的 Skill 智能匹配引擎（生产级实现）。
 *
 * <p>核心特性：
 * <ol>
 *   <li>使用 Embedding 模型将问题和 Skill 转换为语义向量</li>
 *   <li>基于余弦相似度进行语义匹配</li>
 *   <li>支持中英文混合、同义词、语义理解</li>
 *   <li>混合评分：向量相似度 + 规则增强</li>
 *   <li>降级策略：向量服务不可用时回退到规则匹配</li>
 * </ol>
 *
 * <p>线程安全：所有方法均为无状态，可在多线程环境使用。
 */
public class SkillSemanticMatcher {

    private static final Logger LOG = LoggerFactory.getLogger(SkillSemanticMatcher.class);

    private static final String DEFAULT_TENANT_ID = "default";
    private static final int DEFAULT_MAX_RECOMMENDATIONS = 3;
    private static final float MIN_SIMILARITY_THRESHOLD = 0.6f;
    private static final float MIN_HYBRID_SCORE_THRESHOLD = 0.1f;
    private static final float VECTOR_WEIGHT = 0.7f;
    private static final float RULE_WEIGHT = 0.3f;
    private static final float STRONG_RULE_MATCH_THRESHOLD = 0.3f;
    private static final float STRONG_RULE_MATCH_BOOST = 0.35f;

    private final EmbeddingPort embeddingPort;
    private final SkillVectorIndexRepositoryPort vectorRepository;
    private final AgentSkillRepositoryPort skillRepository;
    private final SkillSmartMatcher fallbackMatcher;
    private final int maxRecommendations;

    public SkillSemanticMatcher(EmbeddingPort embeddingPort,
                                 SkillVectorIndexRepositoryPort vectorRepository,
                                 AgentSkillRepositoryPort skillRepository) {
        this(embeddingPort, vectorRepository, skillRepository, DEFAULT_MAX_RECOMMENDATIONS);
    }

    public SkillSemanticMatcher(EmbeddingPort embeddingPort,
                                 SkillVectorIndexRepositoryPort vectorRepository,
                                 AgentSkillRepositoryPort skillRepository,
                                 int maxRecommendations) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.vectorRepository = Objects.requireNonNull(vectorRepository, "vectorRepository must not be null");
        this.skillRepository = Objects.requireNonNull(skillRepository, "skillRepository must not be null");
        this.fallbackMatcher = new SkillSmartMatcher(skillRepository, maxRecommendations);
        this.maxRecommendations = maxRecommendations <= 0 ? DEFAULT_MAX_RECOMMENDATIONS : maxRecommendations;
    }

    /**
     * 根据用户问题进行语义匹配。
     *
     * @param tenantId 租户 ID
     * @param question 用户问题
     * @return 推荐的 Skill 名称列表，按匹配度降序排列
     */
    public List<String> match(String tenantId, String question) {
        if (question == null || question.isBlank()) {
            LOG.debug("Empty question, no skill recommendations");
            return List.of();
        }
        String safeTenantId = defaultTenantId(tenantId);

        try {
            // 1. 将问题转换为向量
            float[] queryVector = embeddingPort.embed(question);
            if (queryVector == null || queryVector.length == 0) {
                LOG.warn("Embedding service returned empty vector, falling back to rule-based matching");
                return fallbackMatcher.match(safeTenantId, question);
            }

            // 2. 向量相似度搜索
            List<SkillSearchResult> vectorResults = vectorRepository.searchSimilar(
                    safeTenantId, queryVector, maxRecommendations * 2);

            List<SkillSearchResult> validVectorResults = vectorResults.stream()
                    .filter(result -> isCurrentAvailableSkill(safeTenantId, result))
                    .toList();

            if (validVectorResults.isEmpty()) {
                LOG.debug("No vector search results, falling back to rule-based matching");
                return fallbackMatcher.match(safeTenantId, question);
            }

            // 3. 混合评分：向量相似度 + 规则增强
            List<SkillScore> hybridScores = hybridScoring(safeTenantId, question, validVectorResults);

            // 4. 过滤、排序、截取
            List<String> recommendations = hybridScores.stream()
                    .filter(score -> score.score >= MIN_HYBRID_SCORE_THRESHOLD)
                    .sorted(Comparator.comparingDouble(SkillScore::score).reversed())
                    .limit(maxRecommendations)
                    .map(SkillScore::skillName)
                    .toList();

            if (recommendations.isEmpty()) {
                LOG.debug("No skills matched with sufficient score, falling back to rule-based matching");
                return fallbackMatcher.match(safeTenantId, question);
            }

            LOG.info("Semantic skill matching for question '{}': {} (embedding model: {})",
                    truncate(question, 50), recommendations, embeddingPort.modelName());

            return recommendations;

        } catch (Exception ex) {
            LOG.error("Semantic matching failed, falling back to rule-based matching: {}", ex.getMessage(), ex);
            return fallbackMatcher.match(safeTenantId, question);
        }
    }

    /**
     * 混合评分：向量相似度 + 规则增强。
     *
     * <p>规则增强包括：
     * <ul>
     *   <li>关键词在名称中出现：+0.1</li>
     *   <li>Skill 最近更新：+0.05</li>
     *   <li>Skill 使用频率高：+0.05（未来扩展）</li>
     * </ul>
     */
    private List<SkillScore> hybridScoring(String tenantId, String question, List<SkillSearchResult> vectorResults) {
        Map<String, Float> scores = new LinkedHashMap<>();

        // 提取关键词用于规则增强
        Set<String> keywords = extractKeywords(question);

        for (SkillSearchResult result : vectorResults) {
            // 向量相似度得分
            float vectorScore = result.score();

            // 规则增强得分
            float ruleScore = calculateRuleScore(result.skillName(), keywords);

            // 混合得分
            float hybridScore = vectorScore * VECTOR_WEIGHT + ruleScore * RULE_WEIGHT;

            scores.merge(result.skillName(), hybridScore, Math::max);
        }

        for (SkillSmartMatcher.SkillScore ruleScore : fallbackMatcher.matchWithScores(tenantId, question)) {
            float weightedRuleScore = (float) ruleScore.score();
            if (weightedRuleScore >= STRONG_RULE_MATCH_THRESHOLD) {
                weightedRuleScore = Math.min(1.0f, weightedRuleScore + STRONG_RULE_MATCH_BOOST);
            }
            scores.merge(ruleScore.skillName(), weightedRuleScore, Math::max);
        }

        return scores.entrySet().stream()
                .map(entry -> new SkillScore(entry.getKey(), entry.getValue()))
                .toList();
    }

    private boolean isCurrentAvailableSkill(String tenantId, SkillSearchResult result) {
        if (result == null || result.skillName() == null || result.skillName().isBlank()) {
            return false;
        }
        return skillRepository.findSkill(tenantId, result.skillName())
                .filter(skill -> skill.enabled()
                        && skill.status() == AgentSkillStatus.ACTIVE
                        && skill.latestRevisionId() != null
                        && !skill.latestRevisionId().isBlank()
                        && skill.latestRevisionId().equals(result.revisionId()))
                .isPresent();
    }

    /**
     * 计算规则增强得分。
     */
    private float calculateRuleScore(String skillName, Set<String> keywords) {
        float score = 0.0f;

        // 名称匹配增强
        String normalizedName = skillName.toLowerCase();
        for (String keyword : keywords) {
            if (normalizedName.contains(keyword.toLowerCase())) {
                score += 0.1f;
            }
        }

        // 限制最大增强分数
        return Math.min(1.0f, score);
    }

    /**
     * 简单的关键词提取（用于规则增强）。
     */
    private Set<String> extractKeywords(String text) {
        return Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String defaultTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId.trim();
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "我", "你", "他", "她", "它", "的", "了", "是", "在", "有", "这", "那", "个",
            "请", "帮", "帮我", "能", "可以", "需要", "想", "要"
    );

    /**
     * Skill 评分记录。
     */
    private record SkillScore(String skillName, float score) {
    }
}
