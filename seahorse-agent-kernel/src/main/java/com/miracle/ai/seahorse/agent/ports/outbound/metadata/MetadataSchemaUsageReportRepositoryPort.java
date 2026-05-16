package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;

/**
 * Schema 使用情况报表查询与统计写入端口。
 */
public interface MetadataSchemaUsageReportRepositoryPort {

    default void recordCompiled(String tenantId,
                                String knowledgeBaseId,
                                Integer schemaVersion,
                                List<String> fieldKeys,
                                List<String> guardOnlyFieldKeys) {
        // 兼容未实现该能力的旧适配器：默认忽略统计写入。
    }

    default void recordRejected(String tenantId,
                                String knowledgeBaseId,
                                Integer schemaVersion,
                                List<String> fieldKeys,
                                String rejectReason) {
        // 兼容未实现该能力的旧适配器：默认忽略统计写入。
    }

    default MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId) {
        return report(tenantId, knowledgeBaseId, null);
    }

    default MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        return MetadataSchemaUsageReport.empty(tenantId, knowledgeBaseId, schemaVersion);
    }

    static MetadataSchemaUsageReportRepositoryPort empty() {
        return new MetadataSchemaUsageReportRepositoryPort() {
        };
    }
}
