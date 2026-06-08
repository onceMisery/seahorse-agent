package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        return SearchChannelType.KEYWORD_BM25;
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
        List<String> expandedTerms = expandedTerms(context, options);
        KeywordSearchRequest request = new KeywordSearchRequest(
                keywordQuery(context.getMainQuestion(), expandedTerms),
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
                .metadata(metadata(options, expandedTerms))
                .build();
    }

    private String keywordQuery(String mainQuestion, List<String> expandedTerms) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addIfText(terms, mainQuestion);
        for (String expandedTerm : expandedTerms) {
            addIfText(terms, expandedTerm);
        }
        return String.join(" ", terms);
    }

    private List<String> expandedTerms(SearchContext context, RetrievalOptions options) {
        List<String> terms = new ArrayList<>();
        if (context != null) {
            appendTerms(terms, context.getMetadata() == null
                    ? null
                    : context.getMetadata().get(SearchContext.METADATA_QUERY_EXPANDED_TERMS));
        }
        if (options != null) {
            appendTerms(terms, options.channelSettings().get(SearchContext.METADATA_QUERY_EXPANDED_TERMS));
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String term : terms) {
            addIfText(distinct, term);
        }
        return List.copyOf(distinct);
    }

    private void appendTerms(List<String> terms, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addIfText(terms, item == null ? "" : item.toString());
            }
            return;
        }
        if (value instanceof String text) {
            addIfText(terms, text);
        }
    }

    private Map<String, Object> metadata(RetrievalOptions options, List<String> expandedTerms) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("topK", options.keywordTopK());
        if (!expandedTerms.isEmpty()) {
            metadata.put("expandedTermCount", expandedTerms.size());
        }
        return metadata;
    }

    private void addIfText(java.util.Collection<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }
}
