package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;

import java.util.List;
import java.util.Optional;

public interface AgentSkillRepositoryPort {

    void saveSkill(AgentSkill skill);

    Optional<AgentSkill> findSkill(String tenantId, String name);

    AgentSkillPage page(String tenantId, long current, long size, String keyword);

    default List<String> listTenants() {
        return List.of("default");
    }

    void saveRevision(AgentSkillRevision revision);

    long nextRevisionNo(String tenantId, String skillName);

    Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId);

    List<AgentSkillRevision> listRevisions(String tenantId, String skillName);

    List<AgentSkillBinding> listBindings(String tenantId, String agentId);

    void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings);
}
