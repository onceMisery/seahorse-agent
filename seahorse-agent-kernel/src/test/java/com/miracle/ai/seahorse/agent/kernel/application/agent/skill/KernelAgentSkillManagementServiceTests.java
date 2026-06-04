package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentSkillManagementServiceTests {

    private static final String TENANT = AgentDefinition.DEFAULT_TENANT_ID;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldManageCompleteCustomSkillLifecycle() {
        InMemoryAgentSkillRepository repository = new InMemoryAgentSkillRepository();
        KernelAgentSkillManagementService service = service(repository);

        AgentSkill created = service.createCustom(null, markdown("custom-research", "Research helper v1"));
        AgentSkill updated = service.updateCustom(null, "custom-research",
                markdown("custom-research", "Research helper v2"));
        AgentSkill disabled = service.disable(null, "custom-research");
        AgentSkill enabled = service.enable(null, "custom-research");
        AgentSkill rolledBack = service.rollbackCustom(null, "custom-research", "skillrev_custom_research_1");
        AgentSkill deleted = service.deleteCustom(null, "custom-research");

        assertEquals(AgentSkillCategory.CUSTOM, created.category());
        assertEquals(AgentSkillSource.MANUAL, created.source());
        assertEquals("skillrev_custom_research_1", created.latestRevisionId());
        assertEquals("skillrev_custom_research_2", updated.latestRevisionId());
        assertFalse(disabled.enabled());
        assertTrue(enabled.enabled());
        assertEquals("skillrev_custom_research_3", rolledBack.latestRevisionId());
        assertEquals(AgentSkillStatus.DELETED, deleted.status());
        assertFalse(service.find(null, "custom-research").isPresent());
        assertEquals(3, service.history(null, "custom-research").size());
        assertEquals(3, repository.revisionCount());
    }

    @Test
    void shouldInstallUploadedSkillAsEditableCustomSkill() {
        KernelAgentSkillManagementService service = service(new InMemoryAgentSkillRepository());

        AgentSkill installed = service.install(null, markdown("installed-helper", "Installed helper"));

        assertEquals(AgentSkillCategory.CUSTOM, installed.category());
        assertEquals(AgentSkillSource.MANUAL, installed.source());
        assertTrue(installed.enabled());
    }

    @Test
    void shouldKeepPublicSkillsReadOnly() {
        KernelAgentSkillManagementService service = service(new InMemoryAgentSkillRepository());
        service.importPublic(TENANT, markdown("public-helper", "Public helper"), "system");

        assertThrows(IllegalArgumentException.class,
                () -> service.updateCustom(null, "public-helper", markdown("public-helper", "Updated")));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCustom(null, "public-helper"));
    }

    private KernelAgentSkillManagementService service(InMemoryAgentSkillRepository repository) {
        CurrentUserPort currentUserPort = () -> Optional.of(new CurrentUser(7L, "admin", "admin", null));
        return new KernelAgentSkillManagementService(repository, currentUserPort, CLOCK);
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
