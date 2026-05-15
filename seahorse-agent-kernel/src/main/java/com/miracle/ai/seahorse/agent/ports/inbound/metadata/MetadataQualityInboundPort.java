package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

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
        // 兼容外部旧实现：未覆盖版本筛选方法时仍返回原质量报表。
        return report(tenantId, knowledgeBaseId, quarantineTopN);
    }
}
