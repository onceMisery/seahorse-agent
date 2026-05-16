package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;

/**
 * Schema 使用情况报表入站端口。
 */
public interface MetadataSchemaUsageInboundPort {

    default MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId) {
        return report(tenantId, knowledgeBaseId, null);
    }

    default MetadataSchemaUsageReport report(String tenantId,
                                             String knowledgeBaseId,
                                             Integer schemaVersion) {
        return MetadataSchemaUsageReport.empty(tenantId, knowledgeBaseId, schemaVersion);
    }
}
