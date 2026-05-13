package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MetadataValidationResult(
        MetadataValidationDecision decision,
        List<MetadataIssue> issues,
        Map<String, Object> acceptedMetadata,
        Map<String, Object> rejectedMetadata
) {

    public MetadataValidationResult {
        decision = Objects.requireNonNullElse(decision, MetadataValidationDecision.ACCEPT);
        issues = List.copyOf(Objects.requireNonNullElse(issues, List.of()));
        acceptedMetadata = Map.copyOf(Objects.requireNonNullElse(acceptedMetadata, Map.of()));
        rejectedMetadata = Map.copyOf(Objects.requireNonNullElse(rejectedMetadata, Map.of()));
    }
}
