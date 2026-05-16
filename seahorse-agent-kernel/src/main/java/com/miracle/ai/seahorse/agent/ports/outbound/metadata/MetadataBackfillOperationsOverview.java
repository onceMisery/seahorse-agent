package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 回填运维总览视图。
 */
public record MetadataBackfillOperationsOverview(
        String tenantId,
        String knowledgeBaseId,
        long totalJobs,
        long processedDocuments,
        long succeededDocuments,
        long failedDocuments,
        long skippedDocuments,
        long reviewDocuments,
        long quarantineDocuments,
        long pendingReviewItems,
        long reExtractingReviewItems,
        long pendingQuarantineItems,
        long resolvedQuarantineItems,
        long pendingSchemaCompensationJobs,
        long pendingSchemaCompensationDocuments,
        List<MetadataBackfillCountItem> statusCounts,
        List<MetadataBackfillCountItem> failureReasons,
        List<MetadataBackfillCountItem> pauseReasons,
        MetadataBackfillJobRecord latestReExtractJob,
        MetadataBackfillJobRecord latestSchemaCompensationJob,
        Instant generatedAt
) {

    public MetadataBackfillOperationsOverview {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        totalJobs = Math.max(0L, totalJobs);
        processedDocuments = Math.max(0L, processedDocuments);
        succeededDocuments = Math.max(0L, succeededDocuments);
        failedDocuments = Math.max(0L, failedDocuments);
        skippedDocuments = Math.max(0L, skippedDocuments);
        reviewDocuments = Math.max(0L, reviewDocuments);
        quarantineDocuments = Math.max(0L, quarantineDocuments);
        pendingReviewItems = Math.max(0L, pendingReviewItems);
        reExtractingReviewItems = Math.max(0L, reExtractingReviewItems);
        pendingQuarantineItems = Math.max(0L, pendingQuarantineItems);
        resolvedQuarantineItems = Math.max(0L, resolvedQuarantineItems);
        pendingSchemaCompensationJobs = Math.max(0L, pendingSchemaCompensationJobs);
        pendingSchemaCompensationDocuments = Math.max(0L, pendingSchemaCompensationDocuments);
        statusCounts = List.copyOf(Objects.requireNonNullElseGet(statusCounts,
                MetadataBackfillOperationsOverview::defaultStatusCounts));
        failureReasons = List.copyOf(Objects.requireNonNullElse(failureReasons, List.of()));
        pauseReasons = List.copyOf(Objects.requireNonNullElse(pauseReasons, List.of()));
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
    }

    public static MetadataBackfillOperationsOverview empty(String tenantId, String knowledgeBaseId) {
        return new MetadataBackfillOperationsOverview(
                tenantId,
                knowledgeBaseId,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                defaultStatusCounts(),
                List.of(),
                List.of(),
                null,
                null,
                Instant.now());
    }

    private static List<MetadataBackfillCountItem> defaultStatusCounts() {
        return Arrays.stream(MetadataBackfillJobStatus.values())
                .map(status -> new MetadataBackfillCountItem(status.name(), 0L))
                .toList();
    }
}
