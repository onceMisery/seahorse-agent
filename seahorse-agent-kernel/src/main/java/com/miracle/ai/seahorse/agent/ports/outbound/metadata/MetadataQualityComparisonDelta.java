package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

/**
 * 元数据质量对比的聚合差值。
 */
public record MetadataQualityComparisonDelta(
        int totalDocumentsDelta,
        int extractedDocumentsDelta,
        double averageFieldCoverageDelta,
        double lowConfidenceRatioDelta,
        double reviewPassRateDelta,
        double reviewCorrectionRateDelta,
        int pendingReviewCountDelta,
        int unresolvedQuarantineCountDelta,
        int indexSyncFailureCountDelta
) {
}
