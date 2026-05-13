package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 元数据治理质量报表。
 */
public record MetadataQualityReport(
        String tenantId,
        String knowledgeBaseId,
        int totalDocuments,
        int extractedDocuments,
        double averageFieldCoverage,
        double lowConfidenceRatio,
        double reviewPassRate,
        int pendingReviewCount,
        int unresolvedQuarantineCount,
        List<MetadataFieldCoverage> fieldCoverages,
        List<MetadataQuarantineReasonCount> quarantineReasons,
        Instant generatedAt
) {

    public MetadataQualityReport {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        totalDocuments = Math.max(0, totalDocuments);
        extractedDocuments = Math.max(0, extractedDocuments);
        averageFieldCoverage = clamp(averageFieldCoverage);
        lowConfidenceRatio = clamp(lowConfidenceRatio);
        reviewPassRate = clamp(reviewPassRate);
        pendingReviewCount = Math.max(0, pendingReviewCount);
        unresolvedQuarantineCount = Math.max(0, unresolvedQuarantineCount);
        fieldCoverages = List.copyOf(Objects.requireNonNullElse(fieldCoverages, List.of()));
        quarantineReasons = List.copyOf(Objects.requireNonNullElse(quarantineReasons, List.of()));
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
    }

    public static MetadataQualityReport empty(String tenantId, String knowledgeBaseId) {
        return new MetadataQualityReport(tenantId, knowledgeBaseId, 0, 0, 0D, 0D, 0D, 0, 0,
                List.of(), List.of(), Instant.now());
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
