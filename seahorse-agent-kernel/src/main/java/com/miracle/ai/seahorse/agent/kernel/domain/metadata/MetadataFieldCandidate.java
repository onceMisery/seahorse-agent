package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Objects;

public record MetadataFieldCandidate(
        String fieldKey,
        Object rawValue,
        String sourceType,
        String extractorName,
        double confidence,
        String evidence,
        int schemaVersion,
        String extractorVersion
) {

    public MetadataFieldCandidate {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        sourceType = Objects.requireNonNullElse(sourceType, "");
        extractorName = Objects.requireNonNullElse(extractorName, "");
        evidence = Objects.requireNonNullElse(evidence, "");
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
    }
}
