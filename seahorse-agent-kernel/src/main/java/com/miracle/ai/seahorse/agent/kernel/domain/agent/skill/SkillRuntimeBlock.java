package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import java.util.List;

public record SkillRuntimeBlock(String name,
                                String revisionId,
                                String contentHash,
                                String description,
                                AgentSkillCategory category,
                                SkillInjectMode injectMode,
                                List<String> allowedTools,
                                String content) {

    public SkillRuntimeBlock {
        name = AgentSkill.normalizeName(name);
        if (revisionId == null || revisionId.isBlank()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
        contentHash = contentHash == null ? "" : contentHash.trim();
        description = description == null ? "" : description.trim();
        category = category == null ? AgentSkillCategory.CUSTOM : category;
        injectMode = injectMode == null ? SkillInjectMode.METADATA_AND_BODY : injectMode;
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        content = content == null ? "" : content.trim();
    }
}
