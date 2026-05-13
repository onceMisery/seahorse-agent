package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;

import java.util.Objects;

public record FieldEq(MetadataFieldDescriptor field, Object value) implements MetadataFilterExpr {

    public FieldEq {
        field = Objects.requireNonNull(field, "field must not be null");
    }
}
