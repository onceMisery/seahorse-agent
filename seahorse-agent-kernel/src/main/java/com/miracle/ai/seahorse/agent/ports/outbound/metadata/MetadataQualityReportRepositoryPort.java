package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

/**
 * 元数据治理质量报表查询端口。
 */
public interface MetadataQualityReportRepositoryPort {

    MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN);

    default MetadataQualityReport report(String tenantId,
                                         String knowledgeBaseId,
                                         int quarantineTopN,
                                         Integer schemaVersion,
                                         String extractorVersion) {
        // 兼容外部旧仓储实现：未支持版本筛选时保持原统计口径。
        return report(tenantId, knowledgeBaseId, quarantineTopN);
    }

    static MetadataQualityReportRepositoryPort empty() {
        return (tenantId, knowledgeBaseId, quarantineTopN) ->
                MetadataQualityReport.empty(tenantId, knowledgeBaseId);
    }
}
