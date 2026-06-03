package com.miracle.ai.seahorse.agent.kernel.domain.agent.definition;

import java.time.Instant;
import java.util.Objects;

public record AgentVersion(String versionId,
                           String agentId,
                           long versionNo,
                           String instructions,
                           String toolSetJson,
                           String modelConfigJson,
                           String memoryConfigJson,
                           String guardrailConfigJson,
                           String skillSetJson,
                           String publishedBy,
                           Instant publishedAt,
                           String changeSummary) {

    public static final String EMPTY_JSON_OBJECT = "{}";
    public static final int MAX_CHANGE_SUMMARY_LENGTH = 500;

    public AgentVersion {
        versionId = requireText(versionId, "versionId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        if (versionNo <= 0) {
            throw new IllegalArgumentException("versionNo must be greater than 0");
        }
        instructions = requireText(instructions, "instructions must not be blank");
        toolSetJson = defaultJson(toolSetJson);
        modelConfigJson = defaultJson(modelConfigJson);
        memoryConfigJson = defaultJson(memoryConfigJson);
        guardrailConfigJson = defaultJson(guardrailConfigJson);
        skillSetJson = defaultJson(skillSetJson);
        publishedBy = requireText(publishedBy, "publishedBy must not be blank");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        changeSummary = requireChangeSummary(changeSummary);
    }

    public AgentVersion(String versionId,
                        String agentId,
                        long versionNo,
                        String instructions,
                        String toolSetJson,
                        String modelConfigJson,
                        String memoryConfigJson,
                        String guardrailConfigJson,
                        String publishedBy,
                        Instant publishedAt,
                        String changeSummary) {
        this(versionId, agentId, versionNo, instructions, toolSetJson, modelConfigJson, memoryConfigJson,
                guardrailConfigJson, EMPTY_JSON_OBJECT, publishedBy, publishedAt, changeSummary);
    }

    private static String defaultJson(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? EMPTY_JSON_OBJECT : trimmed;
    }

    private static String requireChangeSummary(String value) {
        String trimmed = requireText(value, "changeSummary must not be blank");
        if (trimmed.length() > MAX_CHANGE_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("changeSummary must not exceed 500 characters");
        }
        return trimmed;
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
