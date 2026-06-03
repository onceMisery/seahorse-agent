package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;

import java.util.List;

public record AgentSkillPage(List<AgentSkill> records,
                             long total,
                             long size,
                             long current,
                             long pages) {

    public AgentSkillPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
