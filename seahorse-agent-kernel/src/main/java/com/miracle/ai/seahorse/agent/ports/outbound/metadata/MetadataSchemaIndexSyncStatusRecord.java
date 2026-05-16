package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.time.Instant;
import java.util.Objects;

public record MetadataSchemaIndexSyncStatusRecord(
        String fieldId,
        String tenantId,
        String knowledgeBaseId,
        String fieldKey,
        int schemaVersion,
        String backend,
        String action,
        String outcome,
        String errorType,
        String errorMessage,
        Instant syncTime
) {

    public MetadataSchemaIndexSyncStatusRecord {
        fieldId = Objects.requireNonNullElse(fieldId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        backend = Objects.requireNonNullElse(backend, "");
        action = Objects.requireNonNullElse(action, "");
        outcome = Objects.requireNonNullElse(outcome, "");
        errorType = Objects.requireNonNullElse(errorType, "");
        errorMessage = Objects.requireNonNullElse(errorMessage, "");
        syncTime = Objects.requireNonNullElseGet(syncTime, Instant::now);
    }
}
