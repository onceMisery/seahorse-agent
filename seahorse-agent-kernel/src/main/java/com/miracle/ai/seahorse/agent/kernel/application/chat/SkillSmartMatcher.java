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
import com.miracle.ai.seahorse.agent.kernel.policy.SkillMatchPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能匹配引擎：根据用户问题内容分析并推荐合适的 Skill。
 *
 * <p>匹配策略：
 * <ol>
 *   <li>关键词提取：从用户问题中提取有意义的词汇</li>
 *   <li>标签匹配：与 Skill 的 tags 进行匹配</li>
 *   <li>描述相似度：基于关键词在描述中的出现频率</li>
 *   <li>评分排序：综合得分，返回 Top N</li>
 * </ol>
 *
 * <p>线程安全：所有方法均为无状态，可在多线程环境使用。
 */
public class SkillSmartMatcher {

    private static final Logger LOG = LoggerFactory.getLogger(SkillSmartMatcher.class);

    private static final String DEFAULT_TENANT_ID = TenantConstants.DEFAULT_TENANT_ID;
    private static final int DEFAULT_MAX_RECOMMENDATIONS = 3;
    private static final int AVAILABLE_SKILLS_PAGE_SIZE = 100;
    private static final int MAX_AVAILABLE_SKILLS_SCAN = 10_000;
    private static final long AVAILABLE_SKILLS_CACHE_TTL_MILLIS = 30_000L;
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-z]{2,}");

    private final AgentSkillRepositoryPort repository;
    private final int maxRecommendations;
    private final SkillMatchPolicy policy;
    private final Map<String, CachedSkills> availableSkillsCache = new ConcurrentHashMap<>();

    public SkillSmartMatcher(AgentSkillRepositoryPort repository) {
        this(repository, DEFAULT_MAX_RECOMMENDATIONS);
    }

    public SkillSmartMatcher(AgentSkillRepositoryPort repository, int maxRecommendations) {
        this(repository, maxRecommendations, SkillMatchPolicy.defaults());
    }

    public SkillSmartMatcher(AgentSkillRepositoryPort repository, SkillMatchPolicy policy) {
        this(repository, DEFAULT_MAX_RECOMMENDATIONS, policy);
    }

    public SkillSmartMatcher(AgentSkillRepositoryPort repository,
                             int maxRecommendations,
                             SkillMatchPolicy policy) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.maxRecommendations = maxRecommendations <= 0 ? DEFAULT_MAX_RECOMMENDATIONS : maxRecommendations;
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * 根据用户问题智能匹配合适的 Skill。
     *
     * @param tenantId 租户 ID
     * @param question 用户问题
     * @return 推荐的 Skill 名称列表，按匹配度降序排列
     */
    public List<String> match(String tenantId, String question) {
        return matchWithScores(tenantId, question).stream()
                .map(SkillScore::skillName)
                .toList();
    }

    /**
     * 根据用户问题智能匹配合适的 Skill，并保留分数供组合匹配器复用。
     */
    List<SkillScore> matchWithScores(String tenantId, String question) {
        if (question == null || question.isBlank()) {
            LOG.debug("Empty question, no skill recommendations");
            return List.of();
        }

        // 1. 提取关键词
        List<String> keywords = extractKeywords(question);
        if (keywords.isEmpty()) {
            LOG.debug("No keywords extracted from question; questionLength={}", question.length());
            return List.of();
        }

        LOG.debug("Extracted keyword count: {}", keywords.size());

        // 2. 查询所有可用 Skill
        List<AgentSkill> availableSkills = fetchAvailableSkills(tenantId);
        if (availableSkills.isEmpty()) {
            LOG.debug("No available skills for tenant: {}", tenantId);
            return List.of();
        }

        // 3. 计算匹配得分
        List<SkillScore> scores = availableSkills.stream()
                .map(skill -> scoreSkill(skill, keywords))
                .filter(score -> score.score >= policy.minScoreThreshold())
                .sorted(Comparator.comparingDouble(SkillScore::score).reversed())
                .limit(maxRecommendations * 2L)
                .filter(score -> isCurrentAvailableSkill(tenantId, score.skillName()))
                .limit(maxRecommendations)
                .toList();

        if (scores.isEmpty()) {
            LOG.debug("No skills matched with sufficient score; questionLength={}, keywordCount={}",
                    question.length(), keywords.size());
            return List.of();
        }

        List<String> recommendations = scores.stream()
                .map(SkillScore::skillName)
                .toList();

        LOG.info("Skill recommendations: {} (questionLength={}, keywordCount={})",
                recommendations, question.length(), keywords.size());

        return scores;
    }

    /**
     * 从用户问题中提取关键词。
     */
    private List<String> extractKeywords(String question) {
        String normalized = question.toLowerCase()
                .replaceAll("[\\p{Punct}&&[^\\u4e00-\\u9fa5]]", " "); // 移除标点但保留中文

        LinkedHashSet<String> words = new LinkedHashSet<>();

        // 提取英文单词
        ENGLISH_WORD_PATTERN.matcher(normalized).results()
                .map(mr -> mr.group())
                .filter(w -> !policy.stopWords().contains(w))
                .forEach(words::add);

        // 领域词典优先扫描。中文没有空格，不能用贪婪正则分块，否则会吞掉“研究/数据/文档”等短领域词。
        for (List<String> domainTerms : policy.domainKeywords().values()) {
            for (String term : domainTerms) {
                String normalizedTerm = term.toLowerCase();
                if (normalizedTerm.length() >= 2
                        && !policy.stopWords().contains(normalizedTerm)
                        && normalized.contains(normalizedTerm)) {
                    words.add(normalizedTerm);
                }
            }
        }

        return new ArrayList<>(words);
    }

    /**
     * 获取租户下所有可用的 Skill。
     */
    private List<AgentSkill> fetchAvailableSkills(String tenantId) {
        String safeTenantId = defaultTenantId(tenantId);
        long now = System.currentTimeMillis();
        CachedSkills cached = availableSkillsCache.get(safeTenantId);
        if (cached != null && cached.expiresAtMillis > now) {
            return cached.skills;
        }
        try {
            List<AgentSkill> skills = new ArrayList<>();
            long current = 1L;
            while (skills.size() < MAX_AVAILABLE_SKILLS_SCAN) {
                var page = repository.page(safeTenantId, current, AVAILABLE_SKILLS_PAGE_SIZE, null);
                if (page.records().isEmpty()) {
                    break;
                }
                page.records().stream()
                        .filter(skill -> skill.enabled()
                                && skill.status() == AgentSkillStatus.ACTIVE
                                && skill.latestRevisionId() != null)
                        .forEach(skills::add);
                if (page.pages() <= 0 || current >= page.pages()) {
                    break;
                }
                current++;
            }
            availableSkillsCache.put(safeTenantId, new CachedSkills(List.copyOf(skills),
                    now + AVAILABLE_SKILLS_CACHE_TTL_MILLIS));
            return skills;
        } catch (Exception ex) {
            LOG.error("Failed to fetch available skills for tenant: {}", safeTenantId, ex);
            if (cached != null) {
                return cached.skills;
            }
            return List.of();
        }
    }

    /**
     * 计算 Skill 的匹配得分。
     */
    private SkillScore scoreSkill(AgentSkill skill, List<String> keywords) {
        double score = 0.0;

        // 1. 标签匹配
        double tagScore = calculateTagScore(skill.tags(), keywords);
        score += tagScore * policy.tagWeight();

        // 2. 描述匹配
        double descScore = calculateDescriptionScore(skill.description(), keywords);
        score += descScore * policy.descriptionWeight();

        // 3. 名称匹配
        double nameScore = calculateNameScore(skill.name(), keywords);
        score += nameScore * policy.nameWeight();

        return new SkillScore(skill.name(), score);
    }

    private boolean isCurrentAvailableSkill(String tenantId, String skillName) {
        String safeTenantId = defaultTenantId(tenantId);
        return repository.findSkill(safeTenantId, skillName)
                .filter(skill -> skill.enabled()
                        && skill.status() == AgentSkillStatus.ACTIVE
                        && skill.latestRevisionId() != null
                        && !skill.latestRevisionId().isBlank())
                .isPresent();
    }

    /**
     * 计算标签匹配得分。
     */
    private double calculateTagScore(List<String> tags, List<String> keywords) {
        if (tags == null || tags.isEmpty() || keywords.isEmpty()) {
            return 0.0;
        }

        Set<String> normalizedTags = tags.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        int matches = 0;
        for (String keyword : keywords) {
            // 直接匹配
            if (normalizedTags.contains(keyword)) {
                matches += 2; // 直接匹配权重更高
                continue;
            }

            // 领域关键词匹配
            for (Map.Entry<String, List<String>> entry : policy.domainKeywords().entrySet()) {
                if (normalizedTags.contains(entry.getKey()) && entry.getValue().contains(keyword)) {
                    matches += 1;
                    break;
                }
            }
        }

        return Math.min(1.0, (double) matches / keywords.size());
    }

    /**
     * 计算描述匹配得分。
     */
    private double calculateDescriptionScore(String description, List<String> keywords) {
        if (description == null || description.isBlank() || keywords.isEmpty()) {
            return 0.0;
        }

        String normalized = description.toLowerCase();
        int matches = 0;

        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                matches++;
            }
        }

        return Math.min(1.0, (double) matches / keywords.size());
    }

    /**
     * 计算名称匹配得分。
     */
    private double calculateNameScore(String name, List<String> keywords) {
        if (name == null || name.isBlank() || keywords.isEmpty()) {
            return 0.0;
        }

        String normalized = name.toLowerCase();
        int matches = 0;

        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                matches++;
            }
        }

        return Math.min(1.0, (double) matches / keywords.size());
    }

    private String defaultTenantId(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT_ID : tenantId.trim();
    }

    /**
     * Skill 匹配得分记录。
     */
    record SkillScore(String skillName, double score) {
    }

    private record CachedSkills(List<AgentSkill> skills, long expiresAtMillis) {
    }
}
