package com.miracle.ai.seahorse.agent.kernel.domain.metadata;

import java.util.Map;
import java.util.Objects;

public record BackendFieldMapping(
        String canonicalName,
        String milvusPath,
        String pgJsonPath,
        String searchFieldName,
        boolean pushdownToVector,
        boolean pushdownToKeyword,
        boolean guardOnly,
        Map<String, Object> attributes
) {

    public BackendFieldMapping {
        canonicalName = Objects.requireNonNullElse(canonicalName, "");
        milvusPath = Objects.requireNonNullElse(milvusPath, "");
        pgJsonPath = Objects.requireNonNullElse(pgJsonPath, "");
        searchFieldName = Objects.requireNonNullElse(searchFieldName, "");
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
    }

    public static BackendFieldMapping defaults(String fieldKey) {
        String safeFieldKey = Objects.requireNonNullElse(fieldKey, "");
        return new BackendFieldMapping(safeFieldKey, "metadata[\"" + safeFieldKey + "\"]",
                "metadata->>'" + safeFieldKey + "'", safeFieldKey, false, false, false, Map.of());
    }
}
