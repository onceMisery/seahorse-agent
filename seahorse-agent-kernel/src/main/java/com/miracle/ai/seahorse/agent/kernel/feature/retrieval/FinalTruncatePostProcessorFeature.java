package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;

import java.util.List;

/**
 * 最终截断后处理器。
 * <p>
 * 它位于 RRF/Rerank 之后，只负责把候选集裁剪到 finalTopK。
 */
public class FinalTruncatePostProcessorFeature implements SearchResultPostProcessorFeature {

    private static final String NAME = "FinalTruncate";
    private static final int ORDER = 1000;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean enabled(SearchContext context) {
        return context != null && context.getOptions() != null;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .limit(context.effectiveOptions().finalTopK())
                .toList();
    }
}
