package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;

import java.util.Objects;

/**
 * 元数据抽取结果只读查询服务。
 *
 * <p>该服务只暴露结果追溯视图，不触发复核决策、canonical metadata 写回或索引补偿。
 */
public class KernelMetadataExtractionResultService implements MetadataExtractionResultInboundPort {

    private final MetadataExtractionResultManagementRepositoryPort repositoryPort;

    public KernelMetadataExtractionResultService(MetadataExtractionResultManagementRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                MetadataExtractionResultManagementRepositoryPort.empty());
    }

    @Override
    public MetadataExtractionResultPage page(String tenantId,
                                             String knowledgeBaseId,
                                             String documentId,
                                             String jobId,
                                             String status,
                                             long current,
                                             long size) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        return repositoryPort.pageExtractionResults(new MetadataExtractionResultQuery(
                safeTenantId, knowledgeBaseId, documentId, jobId, status, current, size));
    }

    @Override
    public MetadataExtractionResultRecord queryById(String resultId) {
        String safeResultId = requireText(resultId, "resultId must not be blank");
        return repositoryPort.findExtractionResult(safeResultId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Extraction Result 不存在: " + safeResultId));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
