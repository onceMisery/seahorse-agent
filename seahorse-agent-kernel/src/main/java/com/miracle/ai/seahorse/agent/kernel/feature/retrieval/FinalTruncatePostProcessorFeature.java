package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.util.List;
import java.util.Map;

/**
 * 最终截断后处理器。
 * <p>
 * 它位于 RRF/Rerank 之后，只负责把候选集裁剪到 finalTopK。
 */
public class FinalTruncatePostProcessorFeature implements SearchResultPostProcessorFeature {

    private static final String NAME = "FinalTruncate";
    private static final int ORDER = 1000;
    private static final String EVENT_FINAL = "retrieval.final";

    private final ObservationPort observationPort;

    public FinalTruncatePostProcessorFeature() {
        this(null);
    }

    public FinalTruncatePostProcessorFeature(ObservationPort observationPort) {
        this.observationPort = observationPort;
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
    public boolean enabled(SearchContext context) {
        return context != null && context.getOptions() != null;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks == null || chunks.isEmpty()) {
            recordFinalEvent(context, 0, 0, 0);
            return List.of();
        }
        int finalTopK = context.effectiveOptions().finalTopK();
        List<RetrievedChunk> truncated = chunks.stream()
                .limit(finalTopK)
                .toList();
        recordFinalEvent(context, chunks.size(), truncated.size(), finalTopK);
        return truncated;
    }

    private void recordFinalEvent(SearchContext context, int inputCount, int outputCount, int finalTopK) {
        if (observationPort == null) {
            return;
        }
        try {
            // 最终截断只记录规模类低基数字段，避免 chunkId/docId 进入指标标签。
            observationPort.recordEvent(new ObservationEvent(EVENT_FINAL, null, Map.of(
                    "tenantId", tenantId(context),
                    "knowledgeBaseId", knowledgeBaseId(context),
                    "inputCount", String.valueOf(inputCount),
                    "outputCount", String.valueOf(outputCount),
                    "finalTopK", String.valueOf(finalTopK),
                    "truncated", String.valueOf(inputCount > outputCount))));
        } catch (RuntimeException ex) {
            // 观测失败不能影响最终候选返回。
        }
    }

    private String tenantId(SearchContext context) {
        if (context == null || context.getFilter() == null || context.getFilter().system() == null) {
            return "";
        }
        String tenantId = context.getFilter().system().tenantId();
        return tenantId == null ? "" : tenantId;
    }

    private String knowledgeBaseId(SearchContext context) {
        if (context == null || context.getFilter() == null || context.getFilter().system() == null) {
            return "";
        }
        List<String> knowledgeBaseIds = context.getFilter().system().knowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return "";
        }
        return knowledgeBaseIds.get(0) == null ? "" : knowledgeBaseIds.get(0);
    }
}
