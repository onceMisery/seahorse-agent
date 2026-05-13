package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

/**
 * 元数据治理质量报表查询端口。
 */
public interface MetadataQualityReportRepositoryPort {

    MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN);

    static MetadataQualityReportRepositoryPort empty() {
        return (tenantId, knowledgeBaseId, quarantineTopN) ->
                MetadataQualityReport.empty(tenantId, knowledgeBaseId);
    }
}
