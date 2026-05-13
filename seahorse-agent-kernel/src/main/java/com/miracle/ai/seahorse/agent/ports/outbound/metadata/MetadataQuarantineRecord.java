package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据隔离项快照。
 */
public record MetadataQuarantineRecord(
        String id,
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String jobId,
        String stage,
        String reasonCode,
        String reasonMessage,
        Map<String, Object> sourceSnapshot,
        int retryCount,
        Instant nextRetryTime,
        boolean resolved,
        String resolvedBy,
        Instant resolvedTime,
        Instant createTime,
        Instant updateTime
) {

    public MetadataQuarantineRecord {
        id = Objects.requireNonNullElse(id, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        jobId = Objects.requireNonNullElse(jobId, "");
        stage = Objects.requireNonNullElse(stage, "");
        reasonCode = Objects.requireNonNullElse(reasonCode, "");
        reasonMessage = Objects.requireNonNullElse(reasonMessage, "");
        sourceSnapshot = Map.copyOf(Objects.requireNonNullElse(sourceSnapshot, Map.of()));
        retryCount = Math.max(0, retryCount);
        resolvedBy = Objects.requireNonNullElse(resolvedBy, "");
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }
}
