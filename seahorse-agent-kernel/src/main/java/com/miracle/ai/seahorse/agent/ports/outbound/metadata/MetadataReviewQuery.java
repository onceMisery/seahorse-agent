package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 复核队列查询条件。
 */
public record MetadataReviewQuery(
        String tenantId,
        String knowledgeBaseId,
        MetadataReviewStatus reviewStatus,
        long current,
        long size
) {

    public MetadataReviewQuery {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        current = Math.max(1L, current);
        size = Math.max(1L, Math.min(100L, size));
    }

    public long offset() {
        return (current - 1L) * size;
    }
}
