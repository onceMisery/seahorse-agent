package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;

import java.util.Objects;

/**
 * 元数据治理质量报表服务。
 */
public class KernelMetadataQualityService implements MetadataQualityInboundPort {

    private final MetadataQualityReportRepositoryPort reportRepositoryPort;

    public KernelMetadataQualityService(MetadataQualityReportRepositoryPort reportRepositoryPort) {
        this.reportRepositoryPort = Objects.requireNonNullElse(reportRepositoryPort,
                MetadataQualityReportRepositoryPort.empty());
    }

    @Override
    public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
        int safeTopN = quarantineTopN <= 0 ? 5 : Math.min(quarantineTopN, 50);
        return reportRepositoryPort.report(tenantId, knowledgeBaseId, safeTopN);
    }
}
