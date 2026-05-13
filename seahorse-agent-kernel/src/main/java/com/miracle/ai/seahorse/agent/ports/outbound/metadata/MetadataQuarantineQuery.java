package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

/**
 * 隔离项查询条件。
 */
public record MetadataQuarantineQuery(
        String tenantId,
        String knowledgeBaseId,
        Boolean resolved,
        long current,
        long size
) {

    public MetadataQuarantineQuery {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        current = Math.max(1L, current);
        size = Math.max(1L, Math.min(100L, size));
    }

    public long offset() {
        return (current - 1L) * size;
    }
}
