package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import java.util.List;

public record SkillScanResult(SkillScanDecision decision,
                              List<String> reasons,
                              List<String> warnings) {

    public SkillScanResult {
        decision = decision == null ? SkillScanDecision.ALLOW : decision;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean blocked() {
        return decision == SkillScanDecision.BLOCK;
    }
}
