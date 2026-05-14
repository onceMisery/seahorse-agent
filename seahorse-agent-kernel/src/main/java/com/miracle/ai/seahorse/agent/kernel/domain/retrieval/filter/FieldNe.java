package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;

import java.util.Objects;

public record FieldNe(MetadataFieldDescriptor field, Object value) implements MetadataFilterExpr {

    public FieldNe {
        field = Objects.requireNonNull(field, "field must not be null");
    }
}
