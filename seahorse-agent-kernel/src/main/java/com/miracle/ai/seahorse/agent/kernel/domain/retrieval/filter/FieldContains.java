package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;

import java.util.Objects;

public record FieldContains(MetadataFieldDescriptor field, Object value) implements MetadataFilterExpr {

    public FieldContains {
        field = Objects.requireNonNull(field, "field must not be null");
    }
}
