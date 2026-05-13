package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

import lombok.Builder;

import java.util.List;
import java.util.Objects;

/**
 * 检索过滤请求的领域对象。
 * <p>
 * 查询侧必须先把用户输入转换成该对象，再由 MetadataFilterCompiler 做 Schema 校验和编译。
 */
@Builder
public record RetrievalFilter(
        SystemRetrievalFilter system,
        List<MetadataCondition> metadataConditions
) {

    public RetrievalFilter {
        system = Objects.requireNonNullElseGet(system, SystemRetrievalFilter::defaults);
        metadataConditions = List.copyOf(Objects.requireNonNullElse(metadataConditions, List.of()));
    }

    public static RetrievalFilter empty() {
        return new RetrievalFilter(SystemRetrievalFilter.defaults(), List.of());
    }

    public boolean emptyMetadataConditions() {
        return metadataConditions.isEmpty();
    }
}
