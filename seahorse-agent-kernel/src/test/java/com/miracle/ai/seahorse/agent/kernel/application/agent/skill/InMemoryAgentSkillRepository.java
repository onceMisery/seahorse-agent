package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryAgentSkillRepository implements AgentSkillRepositoryPort {

    private final Map<String, AgentSkill> skills = new HashMap<>();
    private final Map<String, AgentSkillRevision> revisions = new HashMap<>();
    private final Map<String, List<AgentSkillBinding>> bindings = new HashMap<>();

    @Override
    public void saveSkill(AgentSkill skill) {
        skills.put(skillKey(skill.tenantId(), skill.name()), skill);
    }

    @Override
    public Optional<AgentSkill> findSkill(String tenantId, String name) {
        return Optional.ofNullable(skills.get(skillKey(tenantId, AgentSkill.normalizeName(name))))
                .filter(skill -> !skill.status().name().equals("DELETED"));
    }

    @Override
    public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        List<AgentSkill> records = skills.values().stream()
                .filter(skill -> skill.tenantId().equals(tenantId))
                .filter(skill -> !skill.status().name().equals("DELETED"))
                .filter(skill -> safeKeyword.isEmpty()
                        || skill.name().contains(safeKeyword)
                        || skill.description().toLowerCase().contains(safeKeyword))
                .sorted(Comparator.comparing(AgentSkill::name))
                .toList();
        long safeSize = size <= 0 ? 10 : size;
        return new AgentSkillPage(records, records.size(), safeSize, current <= 0 ? 1 : current,
                records.isEmpty() ? 0 : (records.size() + safeSize - 1) / safeSize);
    }

    @Override
    public void saveRevision(AgentSkillRevision revision) {
        revisions.put(revision.revisionId(), revision);
    }

    @Override
    public long nextRevisionNo(String tenantId, String skillName) {
        return revisions.values().stream()
                .filter(revision -> revision.tenantId().equals(tenantId)
                        && revision.skillName().equals(AgentSkill.normalizeName(skillName)))
                .mapToLong(AgentSkillRevision::revisionNo)
                .max()
                .orElse(0) + 1;
    }

    @Override
    public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
        return Optional.ofNullable(revisions.get(revisionId))
                .filter(revision -> revision.tenantId().equals(tenantId));
    }

    @Override
    public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
        return revisions.values().stream()
                .filter(revision -> revision.tenantId().equals(tenantId)
                        && revision.skillName().equals(AgentSkill.normalizeName(skillName)))
                .sorted(Comparator.comparingLong(AgentSkillRevision::revisionNo).reversed())
                .toList();
    }

    @Override
    public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
        return List.copyOf(bindings.getOrDefault(bindingKey(tenantId, agentId), List.of()));
    }

    @Override
    public void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings) {
        this.bindings.put(bindingKey(tenantId, agentId), new ArrayList<>(bindings == null ? List.of() : bindings));
    }

    int skillCount() {
        return skills.size();
    }

    int revisionCount() {
        return revisions.size();
    }

    private String skillKey(String tenantId, String name) {
        return tenantId + ":" + name;
    }

    private String bindingKey(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }
}
