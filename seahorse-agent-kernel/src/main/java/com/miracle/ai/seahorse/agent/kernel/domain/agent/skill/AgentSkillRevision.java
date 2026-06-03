package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;

import java.time.Instant;
import java.util.Objects;

public record AgentSkillRevision(String revisionId,
                                 String skillName,
                                 String tenantId,
                                 long revisionNo,
                                 String contentHash,
                                 String content,
                                 String frontmatterJson,
                                 SkillScanDecision scanDecision,
                                 String scanResultJson,
                                 String createdBy,
                                 Instant createdAt) {

    public AgentSkillRevision {
        revisionId = requireText(revisionId, "revisionId must not be blank");
        skillName = AgentSkill.normalizeName(skillName);
        tenantId = trimToNull(tenantId) == null ? AgentDefinition.DEFAULT_TENANT_ID : tenantId.trim();
        if (revisionNo <= 0) {
            throw new IllegalArgumentException("revisionNo must be greater than 0");
        }
        contentHash = requireText(contentHash, "contentHash must not be blank");
        content = requireText(content, "content must not be blank");
        frontmatterJson = requireText(frontmatterJson, "frontmatterJson must not be blank");
        scanDecision = Objects.requireNonNullElse(scanDecision, SkillScanDecision.ALLOW);
        scanResultJson = requireText(scanResultJson, "scanResultJson must not be blank");
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
