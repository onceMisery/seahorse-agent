package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * Schema 使用情况字段级统计记录。
 */
public record MetadataSchemaUsageFieldRecord(
        String fieldKey,
        String displayName,
        long usageCount,
        long guardOnlyCount,
        long rejectedCount,
        double guardOnlyRate,
        double rejectedRate
) {

    public MetadataSchemaUsageFieldRecord {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        displayName = Objects.requireNonNullElse(displayName, fieldKey);
        usageCount = Math.max(0L, usageCount);
        guardOnlyCount = Math.max(0L, guardOnlyCount);
        rejectedCount = Math.max(0L, rejectedCount);
        guardOnlyRate = clamp(guardOnlyRate);
        rejectedRate = clamp(rejectedRate);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
