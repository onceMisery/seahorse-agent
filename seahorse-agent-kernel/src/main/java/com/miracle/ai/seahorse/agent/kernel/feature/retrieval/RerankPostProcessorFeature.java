package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rerank 检索后处理器。
 * <p>
 * 这里只编排 Seahorse 内部 Chunk 契约和模型端口，不直接依赖任何外部 Rerank SDK。
 */
public class RerankPostProcessorFeature implements SearchResultPostProcessorFeature {

    private static final Logger LOG = LoggerFactory.getLogger(RerankPostProcessorFeature.class);
    private static final String NAME = "Rerank";
    private static final int ORDER = 200;

    private final RerankModelPort rerankModelPort;

    public RerankPostProcessorFeature(RerankModelPort rerankModelPort) {
        this.rerankModelPort = Objects.requireNonNullElse(rerankModelPort, RerankModelPort.noop());
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
        if (context == null || context.getOptions() == null) {
            return false;
        }
        RetrievalOptions options = context.effectiveOptions();
        return options.enableRerank() && hasText(options.rerankModel());
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks == null || chunks.isEmpty() || !enabled(context)) {
            return chunks == null ? List.of() : chunks;
        }
        RetrievalOptions options = context.effectiveOptions();
        List<RetrievedChunk> candidates = chunks.stream()
                // Rerank 是成本较高的精排阶段，必须先用 fusion/rerank 配置收窄候选集。
                .limit(candidateLimit(options))
                .toList();
        if (candidates.isEmpty()) {
            return chunks;
        }
        List<RetrievedChunk> reranked;
        try {
            reranked = rerankModelPort.rerank(options.rerankModel(), context.getMainQuestion(), candidates);
        } catch (RuntimeException ex) {
            LOG.debug("Rerank post processor failed, fallback to original chunks", ex);
            return chunks;
        }
        if (reranked == null || reranked.isEmpty()) {
            return chunks;
        }
        List<RetrievedChunk> normalized = normalizeRerankedChunks(candidates, reranked);
        return normalized.isEmpty() ? chunks : normalized;
    }

    private long candidateLimit(RetrievalOptions options) {
        return Math.min(options.fusionTopK(), options.rerankTopK());
    }

    private List<RetrievedChunk> normalizeRerankedChunks(List<RetrievedChunk> candidates, List<RetrievedChunk> reranked) {
        Map<String, RetrievedChunk> originalByKey = new LinkedHashMap<>();
        for (RetrievedChunk candidate : candidates) {
            originalByKey.putIfAbsent(chunkKey(candidate), candidate);
        }
        return reranked.stream()
                // 防止模型端口返回候选集外的内容，后续上下文只允许来自已检索 Chunk。
                .map(chunk -> mergeRerankResult(originalByKey.get(chunkKey(chunk)), chunk))
                .filter(Objects::nonNull)
                .toList();
    }

    private RetrievedChunk mergeRerankResult(RetrievedChunk original, RetrievedChunk reranked) {
        if (original == null) {
            return null;
        }
        RetrievedChunk merged = copyChunk(original);
        if (hasText(reranked.getText())) {
            merged.setText(reranked.getText());
        }
        Float rerankScore = firstNonNull(reranked.getRerankScore(), reranked.getScore(),
                original.getRerankScore(), original.getScore());
        merged.setRerankScore(rerankScore);
        merged.setScore(rerankScore);
        merged.getMetadata().putAll(Objects.requireNonNullElse(reranked.getMetadata(), Map.of()));
        merged.getChannelScores().putAll(Objects.requireNonNullElse(reranked.getChannelScores(), Map.of()));
        merged.getChannelRanks().putAll(Objects.requireNonNullElse(reranked.getChannelRanks(), Map.of()));
        return merged;
    }

    private RetrievedChunk copyChunk(RetrievedChunk source) {
        RetrievedChunk copy = new RetrievedChunk();
        copy.setId(source.getId());
        copy.setText(source.getText());
        copy.setScore(source.getScore());
        copy.setTenantId(source.getTenantId());
        copy.setKbId(source.getKbId());
        copy.setDocId(source.getDocId());
        copy.setCollectionName(source.getCollectionName());
        copy.setChunkIndex(source.getChunkIndex());
        copy.setFusionScore(source.getFusionScore());
        copy.setRerankScore(source.getRerankScore());
        copy.getMetadata().putAll(Objects.requireNonNullElse(source.getMetadata(), Map.of()));
        copy.getChannelScores().putAll(Objects.requireNonNullElse(source.getChannelScores(), Map.of()));
        copy.getChannelRanks().putAll(Objects.requireNonNullElse(source.getChannelRanks(), Map.of()));
        return copy;
    }

    private String chunkKey(RetrievedChunk chunk) {
        if (chunk == null) {
            return "";
        }
        if (hasText(chunk.getId())) {
            return "id:" + chunk.getId();
        }
        if (hasText(chunk.getDocId()) && chunk.getChunkIndex() != null) {
            return "chunk:" + chunk.getDocId() + ":" + chunk.getChunkIndex();
        }
        return "text:" + Objects.requireNonNullElse(chunk.getText(), "");
    }

    private Float firstNonNull(Float first, Float second, Float third, Float fourth) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third != null ? third : fourth;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
