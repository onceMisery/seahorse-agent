package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Objects;

/**
 * 元数据复核反馈聚合摘要。
 *
 * <p>用于把 review 明细压缩成字段、原因码和决策动作维度的 TopN 结果，
 * 便于从质量报表直接定位对应的 review、result、audit 与 backfill 任务。
 */
public record MetadataReviewFeedbackSummary(
        String fieldKey,
        String reasonCode,
        String decisionAction,
        int reviewCount,
        int documentCount,
        List<String> sampleReviewItemIds,
        List<String> sampleResultIds,
        List<String> sampleAuditIds,
        List<String> sampleJobIds
) {

    public MetadataReviewFeedbackSummary {
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        reasonCode = Objects.requireNonNullElse(reasonCode, "");
        decisionAction = Objects.requireNonNullElse(decisionAction, "");
        reviewCount = Math.max(0, reviewCount);
        documentCount = Math.max(0, documentCount);
        sampleReviewItemIds = List.copyOf(Objects.requireNonNullElse(sampleReviewItemIds, List.of()));
        sampleResultIds = List.copyOf(Objects.requireNonNullElse(sampleResultIds, List.of()));
        sampleAuditIds = List.copyOf(Objects.requireNonNullElse(sampleAuditIds, List.of()));
        sampleJobIds = List.copyOf(Objects.requireNonNullElse(sampleJobIds, List.of()));
    }
}
