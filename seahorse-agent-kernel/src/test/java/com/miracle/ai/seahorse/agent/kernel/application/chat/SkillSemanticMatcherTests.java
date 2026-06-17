package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillVectorIndex;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSemanticMatcherTests {

    private TestAgentSkillRepository skillRepository;
    private TestVectorRepository vectorRepository;
    private SkillSemanticMatcher matcher;

    @BeforeEach
    void setUp() {
        skillRepository = new TestAgentSkillRepository();
        vectorRepository = new TestVectorRepository();
        matcher = new SkillSemanticMatcher(new FixedEmbeddingPort(), vectorRepository, skillRepository, 3);

        createSkill("deep-research", "Conduct thorough multi-source research with citations",
                List.of("research", "analysis"), true, "rev-deep-1");
        createSkill("data-analysis", "Analyze data trends and create visualizations",
                List.of("data", "statistics", "visualization"), true, "rev-data-1");
        createSkill("disabled-helper", "Disabled skill that should never be recommended",
                List.of("data"), false, "rev-disabled-1");
    }

    @Test
    void shouldFilterDisabledVectorResultsBeforeReturningRecommendations() {
        vectorRepository.results = List.of(
                new SkillVectorIndexRepositoryPort.SkillSearchResult(
                        "disabled-helper", "rev-disabled-1", 0.95f, "disabled"),
                new SkillVectorIndexRepositoryPort.SkillSearchResult(
                        "data-analysis", "rev-data-1", 0.90f, "data"));

        List<String> recommendations = matcher.match("default", "分析这份数据的趋势");

        assertFalse(recommendations.contains("disabled-helper"));
        assertTrue(recommendations.contains("data-analysis"));
    }

    @Test
    void shouldMergeRuleRecommendationsWithSemanticRecommendations() {
        vectorRepository.results = List.of(new SkillVectorIndexRepositoryPort.SkillSearchResult(
                "deep-research", "rev-deep-1", 0.92f, "research"));

        List<String> recommendations = matcher.match("default", "分析这份数据的趋势并生成可视化图表");

        assertEquals("data-analysis", recommendations.get(0));
        assertTrue(recommendations.contains("deep-research"));
    }

    @Test
    void shouldIgnoreStaleVectorRevision() {
        vectorRepository.results = List.of(new SkillVectorIndexRepositoryPort.SkillSearchResult(
                "data-analysis", "old-revision", 0.95f, "old data"));

        List<String> recommendations = matcher.match("default", "分析这份数据的趋势");

        assertTrue(recommendations.contains("data-analysis"));
    }

    @Test
    void shouldRejectLowSimilarityVectorResultsAndUseRuleFallback() {
        vectorRepository.results = List.of(new SkillVectorIndexRepositoryPort.SkillSearchResult(
                "deep-research", "rev-deep-1", 0.20f, "research"));

        List<String> recommendations = matcher.match("default", "分析这份数据的趋势并生成可视化图表");

        assertTrue(recommendations.contains("data-analysis"));
        assertFalse(recommendations.contains("deep-research"));
    }

    private void createSkill(String name, String description, List<String> tags, boolean enabled, String revisionId) {
        Instant now = Instant.now();
        skillRepository.saveSkill(new AgentSkill(
                name,
                "default",
                AgentSkillCategory.PUBLIC,
                AgentSkillSource.BUILT_IN,
                AgentSkillStatus.ACTIVE,
                enabled,
                revisionId,
                description,
                tags,
                List.of(),
                "system",
                "system",
                now,
                now));
        skillRepository.saveRevision(new AgentSkillRevision(
                revisionId,
                name,
                "default",
                1,
                "hash-" + name,
                "# " + name,
                "{}",
                null,
                "{}",
                "system",
                now));
    }

    private static final class FixedEmbeddingPort implements EmbeddingPort {
        @Override
        public float[] embed(String text) {
            return new float[] {1.0f, 0.0f};
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimension() {
            return 2;
        }

        @Override
        public String modelName() {
            return "test-embedding";
        }
    }

    private static final class TestVectorRepository implements SkillVectorIndexRepositoryPort {
        private List<SkillSearchResult> results = List.of();

        @Override
        public void save(SkillVectorIndex index) {
        }

        @Override
        public void saveBatch(List<SkillVectorIndex> indices) {
        }

        @Override
        public Optional<SkillVectorIndex> findBySkillName(String tenantId, String skillName) {
            return Optional.empty();
        }

        @Override
        public List<SkillSearchResult> searchSimilar(String tenantId, float[] queryVector, int topK) {
            return results.stream().limit(topK).toList();
        }

        @Override
        public void delete(String tenantId, String skillName) {
        }

        @Override
        public void deleteByTenant(String tenantId) {
        }

        @Override
        public boolean collectionExists() {
            return true;
        }

        @Override
        public void createCollection(int dimension) {
        }
    }

    private static final class TestAgentSkillRepository implements AgentSkillRepositoryPort {
        private final Map<String, AgentSkill> skills = new HashMap<>();
        private final Map<String, AgentSkillRevision> revisions = new HashMap<>();

        @Override
        public void saveSkill(AgentSkill skill) {
            skills.put(skill.tenantId() + ":" + skill.name(), skill);
        }

        @Override
        public Optional<AgentSkill> findSkill(String tenantId, String name) {
            return Optional.ofNullable(skills.get(tenantId + ":" + name))
                    .filter(skill -> skill.status() != AgentSkillStatus.DELETED);
        }

        @Override
        public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
            List<AgentSkill> records = new ArrayList<>(skills.values()).stream()
                    .filter(skill -> tenantId.equals(skill.tenantId()))
                    .sorted(Comparator.comparing(AgentSkill::name))
                    .toList();
            return new AgentSkillPage(records, records.size(), size, current, records.isEmpty() ? 0 : 1);
        }

        @Override
        public void saveRevision(AgentSkillRevision revision) {
            revisions.put(revision.tenantId() + ":" + revision.revisionId(), revision);
        }

        @Override
        public long nextRevisionNo(String tenantId, String skillName) {
            return 1;
        }

        @Override
        public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
            return Optional.ofNullable(revisions.get(tenantId + ":" + revisionId));
        }

        @Override
        public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
            return List.of();
        }

        @Override
        public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
            return List.of();
        }

        @Override
        public void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings) {
        }
    }
}
