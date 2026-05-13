package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;

import java.util.List;
import java.util.Objects;

public record FieldIn(MetadataFieldDescriptor field, List<?> values) implements MetadataFilterExpr {

    public FieldIn {
        field = Objects.requireNonNull(field, "field must not be null");
        values = List.copyOf(Objects.requireNonNullElse(values, List.of()));
    }
}
