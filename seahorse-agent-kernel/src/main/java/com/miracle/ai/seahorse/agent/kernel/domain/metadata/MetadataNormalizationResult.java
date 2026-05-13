package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MetadataNormalizationResult(
        Map<String, Object> normalizedMetadata,
        List<MetadataFieldQuality> fieldQualities,
        List<MetadataIssue> issues
) {

    public MetadataNormalizationResult {
        normalizedMetadata = Map.copyOf(Objects.requireNonNullElse(normalizedMetadata, Map.of()));
        fieldQualities = List.copyOf(Objects.requireNonNullElse(fieldQualities, List.of()));
        issues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
    }
}
