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
        Integer schemaVersion,
        String extractorVersion,
        long current,
        long size
) {

    public MetadataExtractionResultQuery {
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "").trim();
        documentId = Objects.requireNonNullElse(documentId, "").trim();
        jobId = Objects.requireNonNullElse(jobId, "").trim();
        status = Objects.requireNonNullElse(status, "").trim();
        schemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "").trim();
        current = Math.max(1L, current);
        size = Math.max(1L, Math.min(100L, size));
    }

    public MetadataExtractionResultQuery(String tenantId,
                                         String knowledgeBaseId,
                                         String documentId,
                                         String jobId,
                                         String status,
                                         long current,
                                         long size) {
        // 兼容旧调用方：未传版本条件时保持原有查询语义。
        this(tenantId, knowledgeBaseId, documentId, jobId, status, null, "", current, size);
    }

    public long offset() {
        return (current - 1L) * size;
    }
}
