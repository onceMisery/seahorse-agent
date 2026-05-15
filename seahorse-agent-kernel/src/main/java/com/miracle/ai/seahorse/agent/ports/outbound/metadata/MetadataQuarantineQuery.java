package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 隔离项查询条件。
 */
public record MetadataQuarantineQuery(
        String tenantId,
        String knowledgeBaseId,
        Boolean resolved,
        String stage,
        String reasonCode,
        String documentId,
        String jobId,
        long current,
        long size
) {

    public MetadataQuarantineQuery {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        stage = Objects.requireNonNullElse(stage, "").trim();
        reasonCode = Objects.requireNonNullElse(reasonCode, "").trim();
        documentId = Objects.requireNonNullElse(documentId, "").trim();
        jobId = Objects.requireNonNullElse(jobId, "").trim();
        current = Math.max(1L, current);
        size = Math.max(1L, Math.min(100L, size));
    }

    public MetadataQuarantineQuery(String tenantId,
                                   String knowledgeBaseId,
                                   Boolean resolved,
                                   long current,
                                   long size) {
        this(tenantId, knowledgeBaseId, resolved, null, null, null, null, current, size);
    }

    public long offset() {
        return (current - 1L) * size;
    }
}
