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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SkillSmartMatcherTests {

    private TestAgentSkillRepository repository;
    private SkillSmartMatcher matcher;

    @BeforeEach
    void setUp() {
        repository = new TestAgentSkillRepository();
        matcher = new SkillSmartMatcher(repository, 3);

        // 创建测试 Skill
        createSkill("deep-research", "Conduct thorough multi-source research with citations",
                List.of("research", "analysis", "investigation"));
        createSkill("data-analysis", "Analyze data trends and create visualizations",
                List.of("data", "statistics", "visualization"));
        createSkill("code-review", "Review code for bugs and improvements",
                List.of("code", "programming", "review"));
        createSkill("document-generator", "Generate technical documentation",
                List.of("document", "documentation", "writing"));
        createSkill("test-automation", "Automate testing workflows",
                List.of("test", "automation", "quality"));
    }

    private void createSkill(String name, String description, List<String> tags) {
        Instant now = Instant.now();
        AgentSkill skill = new AgentSkill(
                name,
                "default",
                AgentSkillCategory.PUBLIC,
                AgentSkillSource.BUILT_IN,
                AgentSkillStatus.ACTIVE,
                true,
                name + "-rev-1",
                description,
                tags,
                List.of(),
                "system",
                "system",
                now,
                now
        );
        repository.saveSkill(skill);

        AgentSkillRevision revision = new AgentSkillRevision(
                name + "-rev-1",
                name,
                "default",
                1,
                "hash-" + name,
                "# " + name,
                "{}",
                null,
                "{}",
                "system",
                now
        );
        repository.saveRevision(revision);
    }

    @Test
    void shouldMatchResearchSkillForResearchQuestion() {
        List<String> result = matcher.match("default", "帮我深度研究量子计算的最新发展");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("deep-research"));
    }

    @Test
    void shouldMatchDataAnalysisSkillForDataQuestion() {
        List<String> result = matcher.match("default", "分析这份数据的趋势并生成可视化图表");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("data-analysis"));
    }

    @Test
    void shouldMatchCodeReviewSkillForCodeQuestion() {
        List<String> result = matcher.match("default", "Review this code for potential bugs");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("code-review"));
    }

    @Test
    void shouldMatchDocumentSkillForDocumentationQuestion() {
        List<String> result = matcher.match("default", "生成这个项目的技术文档");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("document-generator"));
    }

    @Test
    void shouldMatchMultipleSkillsForComplexQuestion() {
        List<String> result = matcher.match("default", "我需要分析代码质量并生成测试报告");

        assertFalse(result.isEmpty());
        // 可能匹配 code-review 或 test-automation
        assertTrue(result.size() <= 3);
    }

    @Test
    void shouldReturnEmptyForEmptyQuestion() {
        List<String> result = matcher.match("default", "");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullQuestion() {
        List<String> result = matcher.match("default", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForQuestionWithOnlyStopWords() {
        List<String> result = matcher.match("default", "请帮我可以吗");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotMatchDisabledSkill() {
        // 禁用 deep-research
        AgentSkill skill = repository.findSkill("default", "deep-research").orElseThrow();
        repository.saveSkill(skill.withEnabled(false, Instant.now(), "system"));

        List<String> result = matcher.match("default", "帮我深度研究量子计算");

        assertFalse(result.contains("deep-research"));
    }

    @Test
    void shouldLimitResultsToMaxRecommendations() {
        // 使用一个能匹配多个 Skill 的问题
        List<String> result = matcher.match("default", "帮我研究数据分析代码文档测试");

        assertTrue(result.size() <= 3);
    }

    @Test
    void shouldMatchEnglishKeywords() {
        List<String> result = matcher.match("default", "I need to analyze the data trends");

        assertFalse(result.isEmpty());
        assertTrue(result.contains("data-analysis"));
    }

    @Test
    void shouldMatchMixedLanguageKeywords() {
        List<String> result = matcher.match("default", "请帮我 analyze code 并生成 documentation");

        assertFalse(result.isEmpty());
        // 应该匹配 code-review 或 document-generator
    }

    @Test
    void shouldCacheAvailableSkillsPerTenant() {
        matcher.match("default", "分析这份数据趋势");
        matcher.match("default", "生成数据可视化图表");

        assertEquals(1, repository.pageCalls);
    }

    @Test
    void shouldRevalidateCachedSkillStateBeforeReturningRecommendations() {
        assertTrue(matcher.match("default", "分析这份数据趋势").contains("data-analysis"));
        AgentSkill skill = repository.findSkill("default", "data-analysis").orElseThrow();
        repository.saveSkill(skill.withEnabled(false, Instant.now(), "system"));

        List<String> result = matcher.match("default", "分析这份数据趋势");

        assertFalse(result.contains("data-analysis"));
        assertEquals(1, repository.pageCalls);
    }

    @Test
    void shouldScanAllPagesWhenRepositoryCapsPageSize() {
        for (int i = 0; i < 105; i++) {
            createSkill("skill-%03d".formatted(i), "Generic helper %03d".formatted(i), List.of("generic"));
        }
        createSkill("zz-data-analysis", "Analyze data trends beyond the first page",
                List.of("data", "statistics", "visualization"));

        List<String> result = matcher.match("default", "分析这份数据趋势并生成图表");

        assertTrue(result.contains("zz-data-analysis"));
        assertTrue(repository.pageCalls > 1);
    }

    // 简单的内存实现，仅用于测试
    private static class TestAgentSkillRepository implements AgentSkillRepositoryPort {

        private final Map<String, AgentSkill> skills = new HashMap<>();
        private final Map<String, AgentSkillRevision> revisions = new HashMap<>();
        private final Map<String, List<AgentSkillBinding>> bindings = new HashMap<>();
        private int pageCalls;

        @Override
        public void saveSkill(AgentSkill skill) {
            skills.put(skillKey(skill.tenantId(), skill.name()), skill);
        }

        @Override
        public Optional<AgentSkill> findSkill(String tenantId, String name) {
            return Optional.ofNullable(skills.get(skillKey(tenantId, AgentSkill.normalizeName(name))))
                    .filter(skill -> skill.status() != AgentSkillStatus.DELETED);
        }

        @Override
        public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
            pageCalls++;
            String safeKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
            List<AgentSkill> records = skills.values().stream()
                    .filter(skill -> skill.tenantId().equals(tenantId))
                    .filter(skill -> skill.status() != AgentSkillStatus.DELETED)
                    .filter(skill -> safeKeyword.isEmpty()
                    || skill.name().contains(safeKeyword)
                    || skill.description().toLowerCase().contains(safeKeyword))
                    .sorted(Comparator.comparing(AgentSkill::name))
                    .toList();
            long safeSize = Math.min(size <= 0 ? 10 : size, 100);
            long safeCurrent = current <= 0 ? 1 : current;
            int fromIndex = (int) Math.min(records.size(), (safeCurrent - 1) * safeSize);
            int toIndex = (int) Math.min(records.size(), fromIndex + safeSize);
            return new AgentSkillPage(records.subList(fromIndex, toIndex), records.size(), safeSize, safeCurrent,
                    records.isEmpty() ? 0 : (records.size() + safeSize - 1) / safeSize);
        }

        @Override
        public void saveRevision(AgentSkillRevision revision) {
            revisions.put(revisionKey(revision.tenantId(), revision.revisionId()), revision);
        }

        @Override
        public long nextRevisionNo(String tenantId, String skillName) {
            return revisions.values().stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.skillName().equals(skillName))
                    .count() + 1;
        }

        @Override
        public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
            return Optional.ofNullable(revisions.get(revisionKey(tenantId, revisionId)));
        }

        @Override
        public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
            return revisions.values().stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.skillName().equals(skillName))
                    .sorted(Comparator.comparing(AgentSkillRevision::revisionNo).reversed())
                    .toList();
        }

        @Override
        public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
            return bindings.getOrDefault(bindingKey(tenantId, agentId), List.of());
        }

        @Override
        public void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> newBindings) {
            bindings.put(bindingKey(tenantId, agentId), new ArrayList<>(newBindings));
        }

        private String skillKey(String tenantId, String name) {
            return tenantId + ":" + name;
        }

        private String revisionKey(String tenantId, String revisionId) {
            return tenantId + ":" + revisionId;
        }

        private String bindingKey(String tenantId, String agentId) {
            return tenantId + ":" + agentId;
        }
    }
}
