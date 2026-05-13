package com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.MetadataCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;

import java.util.List;
import java.util.Objects;

/**
 * Schema 校验后的过滤表达式。
 * <p>
 * expression 可下推给向量或关键词后端；guardOnlyConditions 只能由后处理器兜底过滤。
 */
public record CompiledMetadataFilter(
        RetrievalFilter sourceFilter,
        MetadataFilterExpr expression,
        List<MetadataCondition> guardOnlyConditions,
        List<String> warnings
) {

    public CompiledMetadataFilter {
        sourceFilter = Objects.requireNonNullElseGet(sourceFilter, RetrievalFilter::empty);
        guardOnlyConditions = List.copyOf(Objects.requireNonNullElse(guardOnlyConditions, List.of()));
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public static CompiledMetadataFilter empty() {
        return new CompiledMetadataFilter(RetrievalFilter.empty(), new FilterAnd(List.of()), List.of(), List.of());
    }

    public boolean hasExpression() {
        return expression != null
                && (!(expression instanceof FilterAnd filterAnd) || !filterAnd.children().isEmpty());
    }
}
