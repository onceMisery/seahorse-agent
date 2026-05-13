package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MetadataSchema(
        String tenantId,
        String knowledgeBaseId,
        int schemaVersion,
        List<MetadataFieldDescriptor> fields
) {

    public MetadataSchema {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
    }

    public Optional<MetadataFieldDescriptor> find(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return Optional.empty();
        }
        return fields.stream()
                .filter(field -> field.fieldKey().equals(fieldKey))
                .findFirst();
    }

    public boolean empty() {
        return fields.isEmpty();
    }

    public static MetadataSchema empty(String tenantId, String knowledgeBaseId) {
        return new MetadataSchema(tenantId, knowledgeBaseId, 1, List.of());
    }
}
