package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Map;
import java.util.Objects;

public record MetadataQuarantineItem(
        String tenantId,
        Long knowledgeBaseId,
        Long documentId,
        String taskId,
        String stage,
        String reasonCode,
        String reasonMessage,
        Map<String, Object> sourceSnapshot
) {

    public MetadataQuarantineItem {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, 0L);
        documentId = Objects.requireNonNullElse(documentId, 0L);
        taskId = Objects.requireNonNullElse(taskId, "");
        stage = Objects.requireNonNullElse(stage, "");
        reasonCode = Objects.requireNonNullElse(reasonCode, "");
        reasonMessage = Objects.requireNonNullElse(reasonMessage, "");
        sourceSnapshot = Map.copyOf(Objects.requireNonNullElse(sourceSnapshot, Map.of()));
    }
}
