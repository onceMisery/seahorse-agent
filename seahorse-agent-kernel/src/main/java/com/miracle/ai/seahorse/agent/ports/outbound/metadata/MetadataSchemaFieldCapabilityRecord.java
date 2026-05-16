package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;

import java.time.Instant;
import java.util.Objects;

public record MetadataSchemaFieldCapabilityRecord(
        String fieldId,
        String tenantId,
        String knowledgeBaseId,
        String fieldKey,
        String displayName,
        MetadataValueType valueType,
        boolean filterable,
        boolean sortable,
        boolean facetable,
        boolean indexed,
        MetadataIndexPolicy indexPolicy,
        boolean pushdownToKeyword,
        boolean pushdownToVector,
        boolean guardOnly,
        int schemaVersion,
        String lastSyncBackend,
        String lastSyncAction,
        String lastSyncOutcome,
        String lastSyncErrorType,
        String lastSyncErrorMessage,
        Instant lastSyncTime,
        Instant updateTime
) {

    public MetadataSchemaFieldCapabilityRecord {
        fieldId = Objects.requireNonNullElse(fieldId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        fieldKey = Objects.requireNonNullElse(fieldKey, "");
        displayName = Objects.requireNonNullElse(displayName, fieldKey);
        valueType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        indexPolicy = Objects.requireNonNullElse(indexPolicy, MetadataIndexPolicy.NONE);
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        lastSyncBackend = Objects.requireNonNullElse(lastSyncBackend, "");
        lastSyncAction = Objects.requireNonNullElse(lastSyncAction, "");
        lastSyncOutcome = Objects.requireNonNullElse(lastSyncOutcome, "");
        lastSyncErrorType = Objects.requireNonNullElse(lastSyncErrorType, "");
        lastSyncErrorMessage = Objects.requireNonNullElse(lastSyncErrorMessage, "");
        updateTime = Objects.requireNonNullElseGet(updateTime, Instant::now);
    }
}
