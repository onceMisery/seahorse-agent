package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import java.util.List;

public record SkillSetSnapshot(int version,
                               String mode,
                               List<SkillRuntimeBlock> skills) {

    public static final int CURRENT_VERSION = 1;
    public static final String MODE_BOUND_REVISIONS = "BOUND_REVISIONS";

    public SkillSetSnapshot {
        version = version <= 0 ? CURRENT_VERSION : version;
        mode = mode == null || mode.isBlank() ? MODE_BOUND_REVISIONS : mode.trim();
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
