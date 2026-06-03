package com.miracle.ai.seahorse.agent.ports.inbound.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;

import java.util.List;
import java.util.Optional;

public interface AgentSkillManagementInboundPort {

    AgentSkillPage page(String tenantId, long current, long size, String keyword);

    Optional<AgentSkill> find(String tenantId, String name);

    AgentSkill createCustom(String tenantId, String markdown);

    AgentSkill updateCustom(String tenantId, String name, String markdown);

    AgentSkill enable(String tenantId, String name);

    AgentSkill disable(String tenantId, String name);

    AgentSkill deleteCustom(String tenantId, String name);

    List<AgentSkillRevision> history(String tenantId, String name);

    AgentSkill rollbackCustom(String tenantId, String name, String revisionId);

    AgentSkill install(String tenantId, String markdown);
}
