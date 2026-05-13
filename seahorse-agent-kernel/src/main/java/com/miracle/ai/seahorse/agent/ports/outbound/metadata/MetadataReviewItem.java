package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Map;
import java.util.Objects;

public record MetadataReviewItem(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String resultId,
        String reasonCode,
        String reasonMessage,
        Map<String, Object> suggestedMetadata
) {

    public MetadataReviewItem {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        documentId = Objects.requireNonNullElse(documentId, "");
        resultId = Objects.requireNonNullElse(resultId, "");
        reasonCode = Objects.requireNonNullElse(reasonCode, "");
        reasonMessage = Objects.requireNonNullElse(reasonMessage, "");
        suggestedMetadata = Map.copyOf(Objects.requireNonNullElse(suggestedMetadata, Map.of()));
    }
}
