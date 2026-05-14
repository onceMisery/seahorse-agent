package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record MetadataFieldDescriptor(
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
        BackendFieldMapping backendMapping
) {

    public MetadataFieldDescriptor {
        fieldKey = requireText(fieldKey, "fieldKey");
        displayName = Objects.requireNonNullElse(displayName, fieldKey);
        valueType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        allowedOperators = Set.copyOf(Objects.requireNonNullElse(allowedOperators, Set.of()));
        indexPolicy = Objects.requireNonNullElse(indexPolicy, MetadataIndexPolicy.NONE);
        minConfidence = minConfidence <= 0D ? 0.8D : minConfidence;
        trustedSources = Set.copyOf(Objects.requireNonNullElse(trustedSources, Set.of()));
        extractionHints = Map.copyOf(Objects.requireNonNullElse(extractionHints, Map.of()));
        backendMapping = normalizeBackendMapping(fieldKey, backendMapping);
    }

    private static BackendFieldMapping normalizeBackendMapping(String fieldKey, BackendFieldMapping backendMapping) {
        BackendFieldMapping safeMapping = Objects.requireNonNullElse(backendMapping,
                BackendFieldMapping.defaults(fieldKey));
        String canonicalName = textOrDefault(safeMapping.canonicalName(), fieldKey);
        String searchFieldName = textOrDefault(safeMapping.searchFieldName(), fieldKey);
        if (canonicalName.equals(safeMapping.canonicalName()) && searchFieldName.equals(safeMapping.searchFieldName())) {
            return safeMapping;
        }
        // 外部配置可只声明下推开关，逻辑字段名必须统一回退到 fieldKey，避免不同 adapter 各自兜底。
        return new BackendFieldMapping(
                canonicalName,
                safeMapping.milvusPath(),
                safeMapping.pgJsonPath(),
                searchFieldName,
                safeMapping.pushdownToVector(),
                safeMapping.pushdownToKeyword(),
                safeMapping.guardOnly(),
                safeMapping.attributes());
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
