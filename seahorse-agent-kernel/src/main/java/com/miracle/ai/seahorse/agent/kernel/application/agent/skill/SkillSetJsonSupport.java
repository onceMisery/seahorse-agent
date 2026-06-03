package com.miracle.ai.seahorse.agent.kernel.application.agent.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillSetSnapshot;

import java.util.List;

public class SkillSetJsonSupport {

    private final ObjectMapper objectMapper;

    public SkillSetJsonSupport() {
        this(new ObjectMapper());
    }

    public SkillSetJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public String toJson(List<SkillRuntimeBlock> skills) {
        try {
            return objectMapper.writeValueAsString(new SkillSetSnapshot(
                    SkillSetSnapshot.CURRENT_VERSION,
                    SkillSetSnapshot.MODE_BOUND_REVISIONS,
                    skills));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize skill set snapshot", ex);
        }
    }

    public SkillSetSnapshot fromJson(String json) {
        String safeJson = json == null || json.isBlank() ? AgentVersion.EMPTY_JSON_OBJECT : json;
        if (AgentVersion.EMPTY_JSON_OBJECT.equals(safeJson.trim())) {
            return new SkillSetSnapshot(SkillSetSnapshot.CURRENT_VERSION,
                    SkillSetSnapshot.MODE_BOUND_REVISIONS,
                    List.of());
        }
        try {
            return objectMapper.readValue(safeJson, SkillSetSnapshot.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid skillSetJson", ex);
        }
    }
}
