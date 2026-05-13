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
        backendMapping = Objects.requireNonNullElse(backendMapping, BackendFieldMapping.defaults(fieldKey));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
