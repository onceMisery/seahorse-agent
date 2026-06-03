package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;

import java.time.Instant;
import java.util.Objects;

public record AgentSkillBinding(String agentId,
                                String tenantId,
                                String skillName,
                                String revisionId,
                                SkillInjectMode injectMode,
                                String createdBy,
                                Instant createdAt) {

    public AgentSkillBinding {
        agentId = requireText(agentId, "agentId must not be blank");
        tenantId = trimToNull(tenantId) == null ? AgentDefinition.DEFAULT_TENANT_ID : tenantId.trim();
        skillName = AgentSkill.normalizeName(skillName);
        revisionId = requireText(revisionId, "revisionId must not be blank");
        injectMode = Objects.requireNonNullElse(injectMode, SkillInjectMode.METADATA_AND_BODY);
        createdBy = trimToNull(createdBy);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
