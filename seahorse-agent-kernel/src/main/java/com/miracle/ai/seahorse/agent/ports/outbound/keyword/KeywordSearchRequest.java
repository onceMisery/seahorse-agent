package com.miracle.ai.seahorse.agent.ports.outbound.keyword;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;

import java.util.Objects;

/**
 * 关键词检索请求。
 * <p>
 * query 是检索文本；compiledFilter 是已通过 Schema 编译的过滤表达式，适配器不能消费用户原始 Map。
 */
public record KeywordSearchRequest(
        String query,
        int topK,
        RetrievalFilter filter,
        RetrievalOptions options,
        CompiledMetadataFilter compiledFilter
) {

    public KeywordSearchRequest {
        query = Objects.requireNonNullElse(query, "");
        topK = topK <= 0 ? 5 : topK;
        if (options == null) {
            options = RetrievalOptions.defaults(topK);
        }
        compiledFilter = Objects.requireNonNullElseGet(compiledFilter, CompiledMetadataFilter::empty);
    }
}
