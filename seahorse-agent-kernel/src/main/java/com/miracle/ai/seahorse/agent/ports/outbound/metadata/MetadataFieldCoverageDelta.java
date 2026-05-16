package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 字段级元数据质量差值。
 */
public record MetadataFieldCoverageDelta(
        String fieldKey,
        String displayName,
        int coveredDocumentsDelta,
        int lowConfidenceDocumentsDelta,
        int reviewedDocumentsDelta,
        int correctedDocumentsDelta,
        double coverageRateDelta,
        double lowConfidenceRateDelta,
        double correctionRateDelta
) {

    public MetadataFieldCoverageDelta {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        displayName = Objects.requireNonNullElse(displayName, "");
    }
}
