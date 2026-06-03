package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AgentSkill(String name,
                         String tenantId,
                         AgentSkillCategory category,
                         AgentSkillSource source,
                         AgentSkillStatus status,
                         boolean enabled,
                         String latestRevisionId,
                         String description,
                         List<String> tags,
                         List<String> allowedTools,
                         String createdBy,
                         String updatedBy,
                         Instant createdAt,
                         Instant updatedAt) {

    public AgentSkill {
        name = normalizeName(name);
        tenantId = defaultTenant(tenantId);
        category = Objects.requireNonNullElse(category, AgentSkillCategory.CUSTOM);
        source = Objects.requireNonNullElse(source, AgentSkillSource.MANUAL);
        status = Objects.requireNonNullElse(status, AgentSkillStatus.ACTIVE);
        latestRevisionId = trimToNull(latestRevisionId);
        description = requireText(description, "description must not be blank");
        tags = normalizeList(tags);
        allowedTools = normalizeList(allowedTools);
        createdBy = trimToNull(createdBy);
        updatedBy = trimToNull(updatedBy);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public AgentSkill withRevision(String revisionId, Instant now, String operator) {
        return new AgentSkill(name, tenantId, category, source, status, enabled, revisionId, description,
                tags, allowedTools, createdBy, operator, createdAt, now);
    }

    public AgentSkill withEnabled(boolean nextEnabled, Instant now, String operator) {
        return new AgentSkill(name, tenantId, category, source, status, nextEnabled, latestRevisionId, description,
                tags, allowedTools, createdBy, operator, createdAt, now);
    }

    public AgentSkill deleted(Instant now, String operator) {
        return new AgentSkill(name, tenantId, category, source, AgentSkillStatus.DELETED, false, latestRevisionId,
                description, tags, allowedTools, createdBy, operator, createdAt, now);
    }

    public static String normalizeName(String value) {
        String trimmed = requireText(value, "name must not be blank").toLowerCase().replace('_', '-');
        if (!trimmed.matches("[a-z0-9][a-z0-9-]{1,126}[a-z0-9]")) {
            throw new IllegalArgumentException("skill name must be kebab-case and 3-128 characters");
        }
        return trimmed;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(AgentSkill::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String defaultTenant(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? AgentDefinition.DEFAULT_TENANT_ID : trimmed;
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
