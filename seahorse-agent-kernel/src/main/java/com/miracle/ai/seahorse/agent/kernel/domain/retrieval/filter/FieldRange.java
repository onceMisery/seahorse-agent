package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;

import java.util.Objects;

public record FieldRange(MetadataFieldDescriptor field, Object from, Object to) implements MetadataFilterExpr {

    public FieldRange {
        field = Objects.requireNonNull(field, "field must not be null");
    }
}
