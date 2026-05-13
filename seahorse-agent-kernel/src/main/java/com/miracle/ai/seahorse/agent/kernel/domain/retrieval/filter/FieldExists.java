package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;

import java.util.Objects;

public record FieldExists(MetadataFieldDescriptor field) implements MetadataFilterExpr {

    public FieldExists {
        field = Objects.requireNonNull(field, "field must not be null");
    }
}
