package com.miracle.ai.seahorse.agent.ports.outbound.keyword;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;

import java.util.List;

/**
 * 关键词/BM25 检索端口。
 * <p>
 * Elasticsearch、PostgreSQL FTS、OpenSearch 等实现都应适配到该端口，kernel 不依赖具体搜索引擎 SDK。
 */
public interface KeywordSearchPort {

    List<RetrievedChunk> search(KeywordSearchRequest request);

    static KeywordSearchPort noop() {
        return request -> List.of();
    }
}
