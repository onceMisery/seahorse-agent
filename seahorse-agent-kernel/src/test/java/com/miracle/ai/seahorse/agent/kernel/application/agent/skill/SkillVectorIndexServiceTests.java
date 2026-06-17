package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillScanDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillVectorIndex;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.embedding.EmbeddingPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillVectorIndexServiceTests {

    @Test
    void shouldRebuildAllKnownTenantsWhenCollectionIsCreatedOrRecreated() throws Exception {
        TestSkillRepository skillRepository = new TestSkillRepository();
        skillRepository.addSkill("default", "default-skill");
        skillRepository.addSkill("tenant-b", "tenant-b-skill");
        RecordingVectorRepository vectorRepository = new RecordingVectorRepository();
        SkillVectorIndexService service = new SkillVectorIndexService(
                new FixedEmbeddingPort(), vectorRepository, skillRepository);

        try {
            service.initializeCollection();

            waitForSavedSkillCount(vectorRepository, 2);

            assertTrue(vectorRepository.savedSkillKeys.contains("default:default-skill"));
            assertTrue(vectorRepository.savedSkillKeys.contains("tenant-b:tenant-b-skill"));
        } finally {
            service.shutdown();
        }
    }

    private static void waitForSavedSkillCount(RecordingVectorRepository repository, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline) {
            if (repository.savedSkillKeys.size() >= expected) {
                return;
            }
            Thread.sleep(20L);
        }
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

    private static final class RecordingVectorRepository implements SkillVectorIndexRepositoryPort {
        private final List<String> savedSkillKeys = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void save(SkillVectorIndex index) {
            savedSkillKeys.add(index.tenantId() + ":" + index.skillName());
        }

        @Override
        public void saveBatch(List<SkillVectorIndex> indices) {
            for (SkillVectorIndex index : indices) {
                save(index);
            }
        }

        @Override
        public Optional<SkillVectorIndex> findBySkillName(String tenantId, String skillName) {
            return Optional.empty();
        }

        @Override
        public List<SkillSearchResult> searchSimilar(String tenantId, float[] queryVector, int topK) {
            return List.of();
        }

        @Override
        public void delete(String tenantId, String skillName) {
        }

        @Override
        public void deleteByTenant(String tenantId) {
        }

        @Override
        public boolean collectionExists() {
            return false;
        }

        @Override
        public boolean ensureCollection(int dimension) {
            return true;
        }

        @Override
        public void createCollection(int dimension) {
        }
    }

    private static final class TestSkillRepository implements AgentSkillRepositoryPort {
        private final Map<String, AgentSkill> skills = new LinkedHashMap<>();
        private final Map<String, AgentSkillRevision> revisions = new LinkedHashMap<>();

        void addSkill(String tenantId, String skillName) {
            Instant now = Instant.parse("2026-06-17T00:00:00Z");
            String revisionId = "rev-" + tenantId + "-" + skillName;
            skills.put(tenantId + ":" + skillName, new AgentSkill(
                    skillName,
                    tenantId,
                    AgentSkillCategory.PUBLIC,
                    AgentSkillSource.BUILT_IN,
                    AgentSkillStatus.ACTIVE,
                    true,
                    revisionId,
                    "Skill for " + tenantId,
                    List.of("test"),
                    List.of(),
                    "system",
                    "system",
                    now,
                    now));
            revisions.put(tenantId + ":" + revisionId, new AgentSkillRevision(
                    revisionId,
                    skillName,
                    tenantId,
                    1,
                    "hash-" + revisionId,
                    "# " + skillName,
                    "{}",
                    SkillScanDecision.ALLOW,
                    "{}",
                    "system",
                    now));
        }

        @Override
        public List<String> listTenants() {
            return skills.values().stream()
                    .map(AgentSkill::tenantId)
                    .distinct()
                    .sorted()
                    .toList();
        }

        @Override
        public void saveSkill(AgentSkill skill) {
            skills.put(skill.tenantId() + ":" + skill.name(), skill);
        }

        @Override
        public Optional<AgentSkill> findSkill(String tenantId, String name) {
            return Optional.ofNullable(skills.get(tenantId + ":" + name));
        }

        @Override
        public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
            List<AgentSkill> records = skills.values().stream()
                    .filter(skill -> skill.tenantId().equals(tenantId))
                    .filter(skill -> skill.status() != AgentSkillStatus.DELETED)
                    .sorted(Comparator.comparing(AgentSkill::name))
                    .toList();
            return new AgentSkillPage(records, records.size(), size <= 0 ? 10 : size,
                    current <= 0 ? 1 : current, records.isEmpty() ? 0 : 1);
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
