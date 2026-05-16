package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Schema 使用情况报表。
 */
public record MetadataSchemaUsageReport(
        String tenantId,
        String knowledgeBaseId,
        Integer schemaVersion,
        long totalCompiledRequests,
        long totalRejectedRequests,
        long guardOnlyRequestCount,
        double guardOnlyRate,
        double rejectedRate,
        List<MetadataSchemaUsageFieldRecord> fields,
        Instant generatedAt
) {

    public MetadataSchemaUsageReport {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        schemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        totalCompiledRequests = Math.max(0L, totalCompiledRequests);
        totalRejectedRequests = Math.max(0L, totalRejectedRequests);
        guardOnlyRequestCount = Math.max(0L, guardOnlyRequestCount);
        guardOnlyRate = clamp(guardOnlyRate);
        rejectedRate = clamp(rejectedRate);
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
    }

    public static MetadataSchemaUsageReport empty(String tenantId,
                                                  String knowledgeBaseId,
                                                  Integer schemaVersion) {
        return new MetadataSchemaUsageReport(
                tenantId,
                knowledgeBaseId,
                schemaVersion,
                0L,
                0L,
                0L,
                0D,
                0D,
                List.of(),
                Instant.now());
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
