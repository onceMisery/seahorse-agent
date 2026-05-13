package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import java.util.List;
import java.util.Objects;

public record FilterAnd(List<MetadataFilterExpr> children) implements MetadataFilterExpr {

    public FilterAnd {
        children = List.copyOf(Objects.requireNonNullElse(children, List.of()));
    }
}
