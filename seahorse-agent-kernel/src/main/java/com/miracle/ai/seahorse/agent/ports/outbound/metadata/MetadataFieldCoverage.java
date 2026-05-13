package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 单个元数据字段在文档集合中的覆盖情况。
 */
public record MetadataFieldCoverage(
        String fieldKey,
        String displayName,
        boolean required,
        int coveredDocuments,
        int totalDocuments,
        double coverageRate
) {

    public MetadataFieldCoverage {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        displayName = Objects.requireNonNullElse(displayName, "");
        coveredDocuments = Math.max(0, coveredDocuments);
        totalDocuments = Math.max(0, totalDocuments);
        coverageRate = clamp(coverageRate);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
