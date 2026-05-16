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
        Integer schemaVersion,
        String extractorVersion,
        String llmPromptVersion,
        int totalDocuments,
        int extractedDocuments,
        double averageFieldCoverage,
        double lowConfidenceRatio,
        double reviewPassRate,
        double reviewCorrectionRate,
        int pendingReviewCount,
        int unresolvedQuarantineCount,
        int indexSyncFailureCount,
        List<MetadataFieldCoverage> fieldCoverages,
        List<MetadataReviewFeedbackSummary> reviewFeedbackSummaries,
        List<MetadataQuarantineReasonCount> quarantineReasons,
        Instant generatedAt
) {

    public MetadataQualityReport {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        schemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        llmPromptVersion = Objects.requireNonNullElse(llmPromptVersion, "");
        totalDocuments = Math.max(0, totalDocuments);
        extractedDocuments = Math.max(0, extractedDocuments);
        averageFieldCoverage = clamp(averageFieldCoverage);
        lowConfidenceRatio = clamp(lowConfidenceRatio);
        reviewPassRate = clamp(reviewPassRate);
        reviewCorrectionRate = clamp(reviewCorrectionRate);
        pendingReviewCount = Math.max(0, pendingReviewCount);
        unresolvedQuarantineCount = Math.max(0, unresolvedQuarantineCount);
        indexSyncFailureCount = Math.max(0, indexSyncFailureCount);
        fieldCoverages = List.copyOf(Objects.requireNonNullElse(fieldCoverages, List.of()));
        reviewFeedbackSummaries = List.copyOf(Objects.requireNonNullElse(reviewFeedbackSummaries, List.of()));
        quarantineReasons = List.copyOf(Objects.requireNonNullElse(quarantineReasons, List.of()));
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
    }

    public static MetadataQualityReport empty(String tenantId, String knowledgeBaseId) {
        return new MetadataQualityReport(tenantId, knowledgeBaseId, null, "", "", 0, 0,
                0D, 0D, 0D, 0D, 0, 0, 0, List.of(), List.of(), List.of(), Instant.now());
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
