package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;

/**
 * 元数据治理质量报表入站端口。
 */
public interface MetadataQualityInboundPort {

    MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN);
}
