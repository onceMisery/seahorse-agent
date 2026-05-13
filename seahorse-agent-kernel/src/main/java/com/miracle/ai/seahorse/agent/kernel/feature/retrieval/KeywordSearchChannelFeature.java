package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 关键词/BM25 检索通道。
 * <p>
 * 该 Feature 只负责把 SearchContext 转成端口请求；Elasticsearch 或 PostgreSQL FTS 查询语义由 adapter 实现。
 */
public class KeywordSearchChannelFeature implements SearchChannelFeature {

    private static final String NAME = "KeywordSearch";
    private static final int ORDER = 20;

    private final KeywordSearchPort keywordSearchPort;

    public KeywordSearchChannelFeature(KeywordSearchPort keywordSearchPort) {
        this.keywordSearchPort = Objects.requireNonNullElseGet(keywordSearchPort, KeywordSearchPort::noop);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public SearchChannelType channelType() {
        return SearchChannelType.KEYWORD_ES;
    }

    @Override
    public boolean enabled(SearchContext context) {
        if (context == null) {
            return false;
        }
        return context.effectiveOptions().enableKeyword();
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long start = System.currentTimeMillis();
        RetrievalOptions options = context.effectiveOptions();
        KeywordSearchRequest request = new KeywordSearchRequest(
                context.getMainQuestion(),
                options.keywordTopK(),
                context.getFilter(),
                options,
                context.getCompiledFilter());
        List<RetrievedChunk> chunks = Objects.requireNonNullElse(keywordSearchPort.search(request), List.of());
        return SearchChannelResult.builder()
                .channelType(channelType())
                .channelName(name())
                .chunks(chunks)
                .latencyMs(System.currentTimeMillis() - start)
                .metadata(Map.of("topK", options.keywordTopK()))
                .build();
    }
}
