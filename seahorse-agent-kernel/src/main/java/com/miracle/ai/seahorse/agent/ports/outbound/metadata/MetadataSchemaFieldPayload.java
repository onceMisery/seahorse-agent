package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata Schema 字段写入载荷。
 */
public record MetadataSchemaFieldPayload(
        String tenantId,
        String knowledgeBaseId,
        String fieldKey,
        String displayName,
        MetadataValueType valueType,
        Set<MetadataOperator> allowedOperators,
        boolean required,
        boolean filterable,
        boolean sortable,
        boolean facetable,
        boolean indexed,
        MetadataIndexPolicy indexPolicy,
        double minConfidence,
        Set<String> trustedSources,
        Map<String, Object> extractionHints,
        BackendFieldMapping backendMapping,
        int schemaVersion
) {

    public MetadataSchemaFieldPayload {
        tenantId = Objects.requireNonNullElse(tenantId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        fieldKey = Objects.requireNonNullElse(fieldKey, "").trim();
        displayName = Objects.requireNonNullElse(displayName, fieldKey);
        valueType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        allowedOperators = Set.copyOf(Objects.requireNonNullElse(allowedOperators,
                Set.of(MetadataOperator.EQ, MetadataOperator.IN)));
        indexPolicy = Objects.requireNonNullElse(indexPolicy, MetadataIndexPolicy.NONE);
        minConfidence = minConfidence <= 0D ? 0.8D : minConfidence;
        trustedSources = Set.copyOf(Objects.requireNonNullElse(trustedSources, Set.of()));
        extractionHints = Map.copyOf(Objects.requireNonNullElse(extractionHints, Map.of()));
        backendMapping = Objects.requireNonNullElse(backendMapping, BackendFieldMapping.defaults(fieldKey));
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
    }

    public MetadataSchemaFieldPayload withKnowledgeBaseId(String targetKnowledgeBaseId) {
        return new MetadataSchemaFieldPayload(tenantId, targetKnowledgeBaseId, fieldKey, displayName, valueType,
                allowedOperators, required, filterable, sortable, facetable, indexed, indexPolicy, minConfidence,
                trustedSources, extractionHints, backendMapping, schemaVersion);
    }
}
