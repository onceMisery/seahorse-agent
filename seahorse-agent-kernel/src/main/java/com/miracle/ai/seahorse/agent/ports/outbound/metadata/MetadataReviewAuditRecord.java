package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据人工复核决策审计记录。
 *
 * <p>审计记录只用于追溯人工决策前后状态，不作为 canonical metadata 的写入口。
 */
public record MetadataReviewAuditRecord(
        String id,
        String reviewItemId,
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String resultId,
        String fromStatus,
        String toStatus,
        String reviewerId,
        String reviewComment,
        Map<String, Object> previousMetadata,
        Map<String, Object> updatedMetadata,
        Map<String, Object> decisionMetadata,
        Instant createTime
) {

    public MetadataReviewAuditRecord {
        id = Objects.requireNonNullElse(id, "");
        reviewItemId = Objects.requireNonNullElse(reviewItemId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        resultId = Objects.requireNonNullElse(resultId, "");
        fromStatus = Objects.requireNonNullElse(fromStatus, "");
        toStatus = Objects.requireNonNullElse(toStatus, "");
        reviewerId = Objects.requireNonNullElse(reviewerId, "");
        reviewComment = Objects.requireNonNullElse(reviewComment, "");
        previousMetadata = Map.copyOf(Objects.requireNonNullElse(previousMetadata, Map.of()));
        updatedMetadata = Map.copyOf(Objects.requireNonNullElse(updatedMetadata, Map.of()));
        decisionMetadata = Map.copyOf(Objects.requireNonNullElse(decisionMetadata, Map.of()));
        createTime = Objects.requireNonNullElseGet(createTime, Instant::now);
    }
}
