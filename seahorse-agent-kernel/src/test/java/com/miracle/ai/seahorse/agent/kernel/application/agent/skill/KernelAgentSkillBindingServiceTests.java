package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillSetSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentSkillBindingServiceTests {

    private static final String TENANT = AgentDefinition.DEFAULT_TENANT_ID;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-04T00:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReplaceBindingsAndCreateVersionedSnapshotJson() throws Exception {
        InMemoryAgentSkillRepository repository = new InMemoryAgentSkillRepository();
        KernelAgentSkillManagementService managementService = managementService(repository);
        AgentSkill skill = managementService.createCustom(null, markdown("custom-research", "Research helper"));
        KernelAgentSkillBindingService bindingService = bindingService(repository);

        List<AgentSkillBinding> saved = bindingService.replaceBindings(null, "agent-1", List.of(
                new AgentSkillBinding("ignored-agent", "ignored-tenant", skill.name(), skill.latestRevisionId(),
                        SkillInjectMode.METADATA_ONLY, null, CLOCK.instant())));
        String snapshotJson = bindingService.snapshotJson(null, "agent-1");
        SkillSetSnapshot snapshot = objectMapper.readValue(snapshotJson, SkillSetSnapshot.class);

        assertEquals(1, saved.size());
        assertEquals("agent-1", saved.get(0).agentId());
        assertEquals(TENANT, saved.get(0).tenantId());
        assertEquals("7", saved.get(0).createdBy());
        assertEquals(SkillSetSnapshot.CURRENT_VERSION, snapshot.version());
        assertEquals(SkillSetSnapshot.MODE_BOUND_REVISIONS, snapshot.mode());
        assertEquals(1, snapshot.skills().size());
        assertEquals("custom-research", snapshot.skills().get(0).name());
        assertEquals(skill.latestRevisionId(), snapshot.skills().get(0).revisionId());
        assertEquals(SkillInjectMode.METADATA_ONLY, snapshot.skills().get(0).injectMode());
    }

    @Test
    void shouldRejectDisabledSkillsAndMismatchedRevisions() {
        InMemoryAgentSkillRepository repository = new InMemoryAgentSkillRepository();
        KernelAgentSkillManagementService managementService = managementService(repository);
        AgentSkill first = managementService.createCustom(null, markdown("first-skill", "First skill"));
        AgentSkill second = managementService.createCustom(null, markdown("second-skill", "Second skill"));
        managementService.disable(null, "first-skill");
        KernelAgentSkillBindingService bindingService = bindingService(repository);

        assertThrows(IllegalArgumentException.class, () -> bindingService.replaceBindings(null, "agent-1", List.of(
                binding(first.name(), first.latestRevisionId()))));
        assertThrows(IllegalArgumentException.class, () -> bindingService.replaceBindings(null, "agent-1", List.of(
                binding(second.name(), first.latestRevisionId()))));
        assertTrue(bindingService.listBindings(null, "agent-1").isEmpty());
    }

    private KernelAgentSkillManagementService managementService(InMemoryAgentSkillRepository repository) {
        return new KernelAgentSkillManagementService(repository, currentUserPort(), CLOCK);
    }

    private KernelAgentSkillBindingService bindingService(InMemoryAgentSkillRepository repository) {
        return new KernelAgentSkillBindingService(repository, currentUserPort(), CLOCK);
    }

    private CurrentUserPort currentUserPort() {
        return () -> Optional.of(new CurrentUser(7L, "admin", "admin", null));
    }

    private AgentSkillBinding binding(String skillName, String revisionId) {
        return new AgentSkillBinding("agent-1", TENANT, skillName, revisionId,
                SkillInjectMode.METADATA_AND_BODY, null, CLOCK.instant());
    }

    private String markdown(String name, String description) {
        return """
                ---
                name: %s
                description: %s
                allowed_tools:
                  - web_search
                tags:
                  - research
                ---

                # %s

                Use this skill when research help is needed.
                """.formatted(name, description, name);
    }
}
