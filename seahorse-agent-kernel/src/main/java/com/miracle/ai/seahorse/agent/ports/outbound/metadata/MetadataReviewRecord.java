package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据人工复核项快照。
 */
public record MetadataReviewRecord(
        String id,
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String resultId,
        MetadataReviewStatus reviewStatus,
        int priority,
        String reasonCode,
        String reasonMessage,
        Map<String, Object> suggestedMetadata,
        Map<String, Object> correctedMetadata,
        String reviewerId,
        String reviewComment,
        Instant createTime,
        Instant updateTime
) {

    public MetadataReviewRecord {
        id = Objects.requireNonNullElse(id, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        resultId = Objects.requireNonNullElse(resultId, "");
        reviewStatus = Objects.requireNonNullElse(reviewStatus, MetadataReviewStatus.PENDING);
        priority = Math.max(0, priority);
        reasonCode = Objects.requireNonNullElse(reasonCode, "");
        reasonMessage = Objects.requireNonNullElse(reasonMessage, "");
        suggestedMetadata = Map.copyOf(Objects.requireNonNullElse(suggestedMetadata, Map.of()));
        correctedMetadata = Map.copyOf(Objects.requireNonNullElse(correctedMetadata, Map.of()));
        reviewerId = Objects.requireNonNullElse(reviewerId, "");
        reviewComment = Objects.requireNonNullElse(reviewComment, "");
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }
}
