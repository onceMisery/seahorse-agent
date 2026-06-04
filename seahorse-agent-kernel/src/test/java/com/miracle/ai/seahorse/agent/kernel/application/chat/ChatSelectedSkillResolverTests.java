package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatSelectedSkillResolverTests {

    private StubSkillRepository repo;
    private ChatSelectedSkillResolver resolver;

    @BeforeEach
    void setUp() {
        repo = new StubSkillRepository();
        resolver = new ChatSelectedSkillResolver(repo);
    }

    // ── Happy path ───────────────────────────────────────────

    @Test
    void resolveEnabledActiveSkillReturnsBlock() {
        AgentSkill skill = activeSkill("deep-research", "rev-1");
        repo.addSkill(skill);
        repo.addRevision(revision("rev-1", "deep-research", "# Deep Research instructions"));

        List<SkillRuntimeBlock> blocks = resolver.resolve("default", List.of("deep-research"));

        assertEquals(1, blocks.size());
        assertEquals("deep-research", blocks.get(0).name());
        assertEquals("rev-1", blocks.get(0).revisionId());
        assertEquals(SkillInjectMode.METADATA_AND_BODY, blocks.get(0).injectMode());
        assertTrue(blocks.get(0).content().contains("Deep Research"));
    }

    @Test
    void resolveMultipleSkillsReturnsAll() {
        repo.addSkill(activeSkill("skill-a", "rev-a"));
        repo.addSkill(activeSkill("skill-b", "rev-b"));
        repo.addRevision(revision("rev-a", "skill-a", "Content A"));
        repo.addRevision(revision("rev-b", "skill-b", "Content B"));

        List<SkillRuntimeBlock> blocks = resolver.resolve("default", List.of("skill-a", "skill-b"));

        assertEquals(2, blocks.size());
        assertEquals("skill-a", blocks.get(0).name());
        assertEquals("skill-b", blocks.get(1).name());
    }

    @Test
    void resolveEmptyListReturnsEmpty() {
        List<SkillRuntimeBlock> blocks = resolver.resolve("default", List.of());
        assertTrue(blocks.isEmpty());
    }

    @Test
    void resolveNullListReturnsEmpty() {
        List<SkillRuntimeBlock> blocks = resolver.resolve("default", null);
        assertTrue(blocks.isEmpty());
    }

    // ── Fail-fast on invalid skills ──────────────────────────

    @Test
    void nonexistentSkillThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("default", List.of("ghost-skill")));
        assertTrue(ex.getMessage().contains("not found"));
        assertTrue(ex.getMessage().contains("ghost-skill"));
    }

    @Test
    void disabledSkillThrows() {
        AgentSkill disabled = new AgentSkill("disabled-skill", "default",
                AgentSkillCategory.CUSTOM, AgentSkillSource.MANUAL, AgentSkillStatus.ACTIVE,
                false, "rev-1", "A disabled skill", List.of(), List.of(),
                "admin", "admin", Instant.now(), Instant.now());
        repo.addSkill(disabled);
        repo.addRevision(revision("rev-1", "disabled-skill", "content"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("default", List.of("disabled-skill")));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    void nonActiveSkillThrows() {
        AgentSkill deleted = new AgentSkill("old-skill", "default",
                AgentSkillCategory.CUSTOM, AgentSkillSource.MANUAL, AgentSkillStatus.DELETED,
                true, "rev-1", "A deleted skill", List.of(), List.of(),
                "admin", "admin", Instant.now(), Instant.now());
        repo.addSkill(deleted);
        repo.addRevision(revision("rev-1", "old-skill", "content"));

        // The stub repo filters DELETED in findSkill, so it returns empty → "not found"
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("default", List.of("old-skill")));
    }

    @Test
    void skillWithNoRevisionThrows() {
        repo.addSkill(activeSkill("no-rev-skill", null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("default", List.of("no-rev-skill")));
        assertTrue(ex.getMessage().contains("no revision"));
    }

    @Test
    void missingRevisionRecordThrows() {
        repo.addSkill(activeSkill("orphan-rev", "rev-missing"));
        // No revision record added

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("default", List.of("orphan-rev")));
        assertTrue(ex.getMessage().contains("revision not found"));
    }

    // ── Over-limit validation ────────────────────────────────

    @Test
    void moreThanFiveSkillsThrows() {
        List<String> sixSkills = List.of("a", "b", "c", "d", "e", "f");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("default", sixSkills));
        assertTrue(ex.getMessage().contains("Too many"));
        assertTrue(ex.getMessage().contains("maximum 5"));
    }

    @Test
    void exactlyFiveSkillsAccepted() {
        for (String name : List.of("s-one", "s-two", "s-three", "s-four", "s-five")) {
            repo.addSkill(activeSkill(name, "rev-" + name));
            repo.addRevision(revision("rev-" + name, name, "Content for " + name));
        }

        List<SkillRuntimeBlock> blocks = resolver.resolve("default",
                List.of("s-one", "s-two", "s-three", "s-four", "s-five"));
        assertEquals(5, blocks.size());
    }

    // ── Injection strategy ───────────────────────────────────

    @Test
    void moreThanThreeSkillsSwitchesToMetadataOnly() {
        for (String name : List.of("s-one", "s-two", "s-three", "s-four")) {
            repo.addSkill(activeSkill(name, "rev-" + name));
            repo.addRevision(revision("rev-" + name, name, "Short content"));
        }

        List<SkillRuntimeBlock> blocks = resolver.resolve("default",
                List.of("s-one", "s-two", "s-three", "s-four"));
        assertEquals(4, blocks.size());
        assertTrue(blocks.stream().allMatch(b -> b.injectMode() == SkillInjectMode.METADATA_ONLY));
    }

    @Test
    void longContentSwitchesToMetadataOnly() {
        String longContent = "x".repeat(13000);
        repo.addSkill(activeSkill("long-skill", "rev-long"));
        repo.addRevision(revision("rev-long", "long-skill", longContent));

        List<SkillRuntimeBlock> blocks = resolver.resolve("default", List.of("long-skill"));

        assertEquals(1, blocks.size());
        assertEquals(SkillInjectMode.METADATA_ONLY, blocks.get(0).injectMode());
    }

    @Test
    void shortContentStaysMetadataAndBody() {
        repo.addSkill(activeSkill("short-skill", "rev-short"));
        repo.addRevision(revision("rev-short", "short-skill", "Short instructions"));

        List<SkillRuntimeBlock> blocks = resolver.resolve("default", List.of("short-skill"));

        assertEquals(1, blocks.size());
        assertEquals(SkillInjectMode.METADATA_AND_BODY, blocks.get(0).injectMode());
    }

    // ── Tenant isolation ─────────────────────────────────────

    @Test
    void differentTenantSkillNotFound() {
        repo.addSkill(activeSkill("tenant-a-skill", "rev-a"));
        repo.addRevision(revision("rev-a", "tenant-a-skill", "content"));

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("other-tenant", List.of("tenant-a-skill")));
    }

    // ── Helpers ──────────────────────────────────────────────

    private static AgentSkill activeSkill(String name, String revisionId) {
        return new AgentSkill(name, "default",
                AgentSkillCategory.PUBLIC, AgentSkillSource.MANUAL, AgentSkillStatus.ACTIVE,
                true, revisionId, "Skill: " + name, List.of(), List.of(),
                "admin", "admin", Instant.now(), Instant.now());
    }

    private static AgentSkillRevision revision(String revisionId, String skillName, String content) {
        return new AgentSkillRevision(revisionId, skillName, "default", 1,
                "hash-" + revisionId, content, "{}", null, "{}", "admin", Instant.now());
    }

    /**
     * Minimal stub implementation of AgentSkillRepositoryPort for testing.
     */
    static class StubSkillRepository implements AgentSkillRepositoryPort {
        private final Map<String, AgentSkill> skills = new HashMap<>();
        private final Map<String, AgentSkillRevision> revisions = new HashMap<>();

        void addSkill(AgentSkill skill) {
            skills.put(skill.tenantId() + ":" + skill.name(), skill);
        }

        void addRevision(AgentSkillRevision revision) {
            revisions.put(revision.tenantId() + ":" + revision.revisionId(), revision);
        }

        @Override
        public void saveSkill(AgentSkill skill) { addSkill(skill); }

        @Override
        public Optional<AgentSkill> findSkill(String tenantId, String name) {
            return Optional.ofNullable(skills.get(tenantId + ":" + name))
                    .filter(s -> s.status() != AgentSkillStatus.DELETED);
        }

        @Override
        public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
            return new AgentSkillPage(List.of(), 0, size, current, 0);
        }

        @Override
        public void saveRevision(AgentSkillRevision revision) { addRevision(revision); }

        @Override
        public long nextRevisionNo(String tenantId, String skillName) { return 1; }

        @Override
        public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
            return Optional.ofNullable(revisions.get(tenantId + ":" + revisionId));
        }

        @Override
        public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
            return List.of();
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding> listBindings(
                String tenantId, String agentId) {
            return List.of();
        }

        @Override
        public void replaceBindings(String tenantId, String agentId,
                                     List<com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding> bindings) {
        }
    }
}
