package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

/**
 * 人工复核项状态，与 t_metadata_review_item.review_status 保持一致。
 */
public enum MetadataReviewStatus {
    PENDING,
    APPROVED,
    CORRECTED,
    RE_EXTRACTING,
    REJECTED,
    QUARANTINED
}
