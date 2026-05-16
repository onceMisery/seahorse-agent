package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityComparisonReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;

/**
 * 元数据治理质量报表入站端口。
 */
public interface MetadataQualityInboundPort {

    MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN);

    default MetadataQualityReport report(String tenantId,
                                         String knowledgeBaseId,
                                         int quarantineTopN,
                                         Integer schemaVersion,
                                         String extractorVersion) {
        return report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion, extractorVersion, "");
    }

    default MetadataQualityReport report(String tenantId,
                                         String knowledgeBaseId,
                                         int quarantineTopN,
                                         Integer schemaVersion,
                                         String extractorVersion,
                                         String llmPromptVersion) {
        // 兼容外部旧实现：未覆盖版本筛选方法时仍返回原质量报表。
        return report(tenantId, knowledgeBaseId, quarantineTopN);
    }

    default MetadataQualityComparisonReport compare(String tenantId,
                                                    String knowledgeBaseId,
                                                    int quarantineTopN,
                                                    Integer baselineSchemaVersion,
                                                    String baselineExtractorVersion,
                                                    String baselineLlmPromptVersion,
                                                    Integer candidateSchemaVersion,
                                                    String candidateExtractorVersion,
                                                    String candidateLlmPromptVersion) {
        MetadataQualityReport baseline = report(
                tenantId, knowledgeBaseId, quarantineTopN,
                baselineSchemaVersion, baselineExtractorVersion, baselineLlmPromptVersion);
        MetadataQualityReport candidate = report(
                tenantId, knowledgeBaseId, quarantineTopN,
                candidateSchemaVersion, candidateExtractorVersion, candidateLlmPromptVersion);
        return new MetadataQualityComparisonReport(
                tenantId,
                knowledgeBaseId,
                baseline,
                candidate,
                null,
                java.util.List.of());
    }
}
