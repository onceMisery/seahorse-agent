package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import java.util.Map;
import java.util.Objects;

/**
 * 复核提交命令，Web adapter 只传递审核人、备注和人工修正值。
 */
public record MetadataReviewDecisionCommand(
        String reviewerId,
        String comment,
        Map<String, Object> correctedMetadata
) {

    public MetadataReviewDecisionCommand {
        reviewerId = Objects.requireNonNullElse(reviewerId, "");
        comment = Objects.requireNonNullElse(comment, "");
        correctedMetadata = Map.copyOf(Objects.requireNonNullElse(correctedMetadata, Map.of()));
    }
}
