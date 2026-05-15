package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 元数据抽取结果查询条件。
 */
public record MetadataExtractionResultQuery(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String jobId,
        String status,
        long current,
        long size
) {

    public MetadataExtractionResultQuery {
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        documentId = Objects.requireNonNullElse(documentId, "").trim();
        jobId = Objects.requireNonNullElse(jobId, "").trim();
        status = Objects.requireNonNullElse(status, "").trim();
        current = Math.max(1L, current);
        size = Math.max(1L, Math.min(100L, size));
    }

    public long offset() {
        return (current - 1L) * size;
    }
}
