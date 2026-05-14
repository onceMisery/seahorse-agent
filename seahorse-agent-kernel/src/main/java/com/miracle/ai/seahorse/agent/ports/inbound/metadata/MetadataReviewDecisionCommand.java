package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 复核提交命令，Web adapter 只传递审核人、备注、人工修正值和重抽取版本参数。
 */
public record MetadataReviewDecisionCommand(
        String reviewerId,
        String comment,
        Map<String, Object> correctedMetadata,
        List<String> ignoredFields,
        String extractorVersion,
        String pipelineId,
        String llmExtractorVersion,
        String llmPromptVersion
) {

    public MetadataReviewDecisionCommand {
        reviewerId = Objects.requireNonNullElse(reviewerId, "");
        comment = Objects.requireNonNullElse(comment, "");
        correctedMetadata = Map.copyOf(Objects.requireNonNullElse(correctedMetadata, Map.of()));
        ignoredFields = List.copyOf(Objects.requireNonNullElse(ignoredFields, List.of()));
        extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        pipelineId = Objects.requireNonNullElse(pipelineId, "");
        llmExtractorVersion = Objects.requireNonNullElse(llmExtractorVersion, "");
        llmPromptVersion = Objects.requireNonNullElse(llmPromptVersion, "");
    }

    public MetadataReviewDecisionCommand(String reviewerId,
                                         String comment,
                                         Map<String, Object> correctedMetadata) {
        this(reviewerId, comment, correctedMetadata, List.of(), "", "", "", "");
    }

    public MetadataReviewDecisionCommand(String reviewerId,
                                         String comment,
                                         Map<String, Object> correctedMetadata,
                                         List<String> ignoredFields) {
        this(reviewerId, comment, correctedMetadata, ignoredFields, "", "", "", "");
    }

    public MetadataReviewDecisionCommand(String reviewerId,
                                         String comment,
                                         Map<String, Object> correctedMetadata,
                                         List<String> ignoredFields,
                                         String extractorVersion,
                                         String pipelineId) {
        this(reviewerId, comment, correctedMetadata, ignoredFields, extractorVersion, pipelineId, "", "");
    }
}
