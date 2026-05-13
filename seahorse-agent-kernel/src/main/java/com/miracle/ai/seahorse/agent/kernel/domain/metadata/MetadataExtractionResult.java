package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.List;
import java.util.Objects;

public record MetadataExtractionResult(
        List<MetadataFieldCandidate> candidates,
        List<MetadataIssue> issues,
        String extractorVersion
) {

    public MetadataExtractionResult {
        candidates = List.copyOf(Objects.requireNonNullElse(candidates, List.of()));
        issues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
    }
}
