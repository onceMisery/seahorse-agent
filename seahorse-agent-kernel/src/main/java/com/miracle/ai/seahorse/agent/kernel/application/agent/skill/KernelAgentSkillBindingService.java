package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.skill.AgentSkillBindingInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public class KernelAgentSkillBindingService implements AgentSkillBindingInboundPort {

    private static final String ADMIN_ROLE = "admin";

    private final AgentSkillRepositoryPort repository;
    private final CurrentUserPort currentUserPort;
    private final Clock clock;
    private final SkillSetJsonSupport jsonSupport;

    public KernelAgentSkillBindingService(AgentSkillRepositoryPort repository,
                                          CurrentUserPort currentUserPort,
                                          Clock clock) {
        this(repository, currentUserPort, clock, new SkillSetJsonSupport());
    }

    public KernelAgentSkillBindingService(AgentSkillRepositoryPort repository,
                                          CurrentUserPort currentUserPort,
                                          Clock clock,
                                          SkillSetJsonSupport jsonSupport) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
        currentUserPort.requireRole(ADMIN_ROLE);
        return repository.listBindings(defaultTenant(tenantId), requireText(agentId, "agentId"));
    }

    @Override
    public List<AgentSkillBinding> replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings) {
        CurrentUser user = currentUserPort.requireCurrentUser();
        currentUserPort.requireRole(ADMIN_ROLE);
        String safeTenant = defaultTenant(tenantId);
        String safeAgentId = requireText(agentId, "agentId");
        List<AgentSkillBinding> safeBindings = bindings == null ? List.of() : bindings.stream()
                .map(binding -> normalizeBinding(safeTenant, safeAgentId, binding, user))
                .toList();
        repository.replaceBindings(safeTenant, safeAgentId, safeBindings);
        return repository.listBindings(safeTenant, safeAgentId);
    }

    @Override
    public String snapshotJson(String tenantId, String agentId) {
        currentUserPort.requireRole(ADMIN_ROLE);
        String safeTenant = defaultTenant(tenantId);
        List<SkillRuntimeBlock> blocks = repository.listBindings(safeTenant, requireText(agentId, "agentId")).stream()
                .map(binding -> {
                    AgentSkill skill = repository.findSkill(safeTenant, binding.skillName())
                            .orElseThrow(() -> new IllegalArgumentException("skill not found: " + binding.skillName()));
                    AgentSkillRevision revision = repository.findRevision(safeTenant, binding.revisionId())
                            .orElseThrow(() -> new IllegalArgumentException("revision not found: " + binding.revisionId()));
                    return new SkillRuntimeBlock(skill.name(), revision.revisionId(), revision.contentHash(),
                            skill.description(), skill.category(), binding.injectMode(), skill.allowedTools(),
                            revision.content());
                })
                .toList();
        return jsonSupport.toJson(blocks);
    }

    private AgentSkillBinding normalizeBinding(String tenantId,
                                               String agentId,
                                               AgentSkillBinding binding,
                                               CurrentUser user) {
        if (binding == null) {
            throw new IllegalArgumentException("binding must not be null");
        }
        AgentSkill skill = repository.findSkill(tenantId, binding.skillName())
                .orElseThrow(() -> new IllegalArgumentException("skill not found: " + binding.skillName()));
        if (!skill.enabled()) {
            throw new IllegalArgumentException("skill is disabled: " + skill.name());
        }
        AgentSkillRevision revision = repository.findRevision(tenantId, binding.revisionId())
                .orElseThrow(() -> new IllegalArgumentException("revision not found: " + binding.revisionId()));
        if (!revision.skillName().equals(skill.name())) {
            throw new IllegalArgumentException("revision does not belong to skill");
        }
        return new AgentSkillBinding(agentId, tenantId, skill.name(), revision.revisionId(), binding.injectMode(),
                String.valueOf(user.userId()), clock.instant());
    }

    private String defaultTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? AgentDefinition.DEFAULT_TENANT_ID : tenantId.trim();
    }

    private String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
