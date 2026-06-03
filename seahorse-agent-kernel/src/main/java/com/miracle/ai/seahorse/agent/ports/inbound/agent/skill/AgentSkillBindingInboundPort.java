package com.miracle.ai.seahorse.agent.ports.inbound.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;

import java.util.List;

public interface AgentSkillBindingInboundPort {

    List<AgentSkillBinding> listBindings(String tenantId, String agentId);

    List<AgentSkillBinding> replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings);

    String snapshotJson(String tenantId, String agentId);
}
