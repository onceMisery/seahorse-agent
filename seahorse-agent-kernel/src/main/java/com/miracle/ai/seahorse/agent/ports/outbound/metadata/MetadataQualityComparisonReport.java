package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Objects;

/**
 * 元数据质量版本对比报表。
 */
public record MetadataQualityComparisonReport(
        String tenantId,
        String knowledgeBaseId,
        MetadataQualityReport baseline,
        MetadataQualityReport candidate,
        MetadataQualityComparisonDelta delta,
        List<MetadataFieldCoverageDelta> fieldDeltas
) {

    public MetadataQualityComparisonReport {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKnowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        tenantId = safeTenantId;
        knowledgeBaseId = safeKnowledgeBaseId;
        baseline = baseline == null
                ? MetadataQualityReport.empty(safeTenantId, safeKnowledgeBaseId)
                : baseline;
        candidate = candidate == null
                ? MetadataQualityReport.empty(safeTenantId, safeKnowledgeBaseId)
                : candidate;
        delta = delta == null
                ? new MetadataQualityComparisonDelta(0, 0, 0D, 0D, 0D, 0D, 0, 0, 0)
                : delta;
        fieldDeltas = List.copyOf(Objects.requireNonNullElse(fieldDeltas, List.of()));
    }
}
