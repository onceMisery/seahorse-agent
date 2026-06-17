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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final String DEFAULT_TENANT_ID = "default";
    private static final int DEFAULT_MAX_RECOMMENDATIONS = 3;
    private static final int AVAILABLE_SKILLS_PAGE_SIZE = 100;
    private static final int MAX_AVAILABLE_SKILLS_SCAN = 10_000;
    private static final long AVAILABLE_SKILLS_CACHE_TTL_MILLIS = 30_000L;
    private static final double MIN_SCORE_THRESHOLD = 0.1;
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-z]{2,}");

    // 停用词列表（中英文）
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "我", "你", "他", "她", "它", "我们", "你们", "他们",
            "的", "了", "是", "在", "有", "这", "那", "个", "上", "中", "下",
            "请", "帮", "帮我", "能", "可以", "需要", "想", "要"
    );

    // 领域关键词映射
    private static final Map<String, List<String>> DOMAIN_KEYWORDS = Map.of(
            "research", List.of("研究", "调研", "调查", "探索", "study", "investigate", "analyze", "explore"),
            "data", List.of("数据", "统计", "可视化", "图表", "表格", "data", "statistics", "visualization", "chart"),
            "code", List.of("代码", "编程", "开发", "重构", "review", "code", "programming", "refactor", "debug"),
            "document", List.of("文档", "说明", "手册", "注释", "document", "documentation", "manual", "comment"),
            "test", List.of("测试", "验证", "质量", "QA", "test", "testing", "quality", "verify"),
            "design", List.of("设计", "架构", "UX", "UI", "原型", "design", "architecture", "prototype"),
            "translate", List.of("翻译", "转换", "translate", "conversion", "transform"),
            "summary", List.of("总结", "摘要", "概括", "汇总", "summary", "abstract", "summarize")
    );

    private final AgentSkillRepositoryPort repository;
    private final int maxRecommendations;
    private final Map<String, CachedSkills> availableSkillsCache = new ConcurrentHashMap<>();

    public SkillSmartMatcher(AgentSkillRepositoryPort repository) {
        this(repository, DEFAULT_MAX_RECOMMENDATIONS);
    }

    public SkillSmartMatcher(AgentSkillRepositoryPort repository, int maxRecommendations) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.maxRecommendations = maxRecommendations <= 0 ? DEFAULT_MAX_RECOMMENDATIONS : maxRecommendations;
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
            LOG.debug("No keywords extracted from question: {}", question);
            return List.of();
        }

        LOG.debug("Extracted keywords: {}", keywords);

        // 2. 查询所有可用 Skill
        List<AgentSkill> availableSkills = fetchAvailableSkills(tenantId);
        if (availableSkills.isEmpty()) {
            LOG.debug("No available skills for tenant: {}", tenantId);
            return List.of();
        }

        // 3. 计算匹配得分
        List<SkillScore> scores = availableSkills.stream()
                .map(skill -> scoreSkill(skill, keywords))
                .filter(score -> score.score >= MIN_SCORE_THRESHOLD)
                .sorted(Comparator.comparingDouble(SkillScore::score).reversed())
                .limit(maxRecommendations * 2L)
                .filter(score -> isCurrentAvailableSkill(tenantId, score.skillName()))
                .limit(maxRecommendations)
                .toList();

        if (scores.isEmpty()) {
            LOG.debug("No skills matched with sufficient score for question: {}", question);
            return List.of();
        }

        List<String> recommendations = scores.stream()
                .map(SkillScore::skillName)
                .toList();

        LOG.info("Skill recommendations for question '{}': {} (keywords: {})",
                truncate(question, 50), recommendations, keywords);

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
                .filter(w -> !STOP_WORDS.contains(w))
                .forEach(words::add);

        // 领域词典优先扫描。中文没有空格，不能用贪婪正则分块，否则会吞掉“研究/数据/文档”等短领域词。
        for (List<String> domainTerms : DOMAIN_KEYWORDS.values()) {
            for (String term : domainTerms) {
                String normalizedTerm = term.toLowerCase();
                if (normalizedTerm.length() >= 2
                        && !STOP_WORDS.contains(normalizedTerm)
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

        // 1. 标签匹配（权重 0.5）
        double tagScore = calculateTagScore(skill.tags(), keywords);
        score += tagScore * 0.5;

        // 2. 描述匹配（权重 0.3）
        double descScore = calculateDescriptionScore(skill.description(), keywords);
        score += descScore * 0.3;

        // 3. 名称匹配（权重 0.2）
        double nameScore = calculateNameScore(skill.name(), keywords);
        score += nameScore * 0.2;

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
            for (Map.Entry<String, List<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
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
