package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;

import java.util.Objects;

/**
 * Schema 使用情况报表服务。
 */
public class KernelMetadataSchemaUsageService implements MetadataSchemaUsageInboundPort {

    private final MetadataSchemaUsageReportRepositoryPort repositoryPort;

    public KernelMetadataSchemaUsageService(MetadataSchemaUsageReportRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNullElseGet(repositoryPort,
                MetadataSchemaUsageReportRepositoryPort::empty);
    }

    @Override
    public MetadataSchemaUsageReport report(String tenantId,
                                            String knowledgeBaseId,
                                            Integer schemaVersion) {
        Integer safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        return repositoryPort.report(tenantId, knowledgeBaseId, safeSchemaVersion);
    }
}
