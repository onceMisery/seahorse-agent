package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Objects;

public record MetadataIssue(
        String fieldKey,
        String stage,
        String code,
        String message,
        MetadataIssueSeverity severity
) {

    public MetadataIssue {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        stage = Objects.requireNonNullElse(stage, "");
        code = Objects.requireNonNullElse(code, "");
        message = Objects.requireNonNullElse(message, "");
        severity = Objects.requireNonNullElse(severity, MetadataIssueSeverity.WARN);
    }

    public static MetadataIssue warn(String fieldKey, String stage, String code, String message) {
        return new MetadataIssue(fieldKey, stage, code, message, MetadataIssueSeverity.WARN);
    }

    public static MetadataIssue error(String fieldKey, String stage, String code, String message) {
        return new MetadataIssue(fieldKey, stage, code, message, MetadataIssueSeverity.ERROR);
    }
}
