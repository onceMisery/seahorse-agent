package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Objects;

public record MetadataFieldQuality(
        String fieldKey,
        double confidence,
        String sourceType,
        String extractorName,
        boolean normalized,
        String message
) {

    public MetadataFieldQuality {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        sourceType = Objects.requireNonNullElse(sourceType, "");
        extractorName = Objects.requireNonNullElse(extractorName, "");
        message = Objects.requireNonNullElse(message, "");
    }
}
