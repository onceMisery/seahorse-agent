package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Map;
import java.util.Objects;

/**
 * 人工复核提交结果。
 */
public record MetadataReviewDecision(
        String itemId,
        MetadataReviewStatus reviewStatus,
        String reviewerId,
        String reviewComment,
        Map<String, Object> correctedMetadata
) {

    public MetadataReviewDecision {
        itemId = Objects.requireNonNullElse(itemId, "");
        reviewStatus = Objects.requireNonNullElse(reviewStatus, MetadataReviewStatus.PENDING);
        reviewerId = Objects.requireNonNullElse(reviewerId, "");
        reviewComment = Objects.requireNonNullElse(reviewComment, "");
        correctedMetadata = Map.copyOf(Objects.requireNonNullElse(correctedMetadata, Map.of()));
    }
}
