package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Rerank 检索后处理器。
 * <p>
 * 这里只编排 Seahorse 内部 Chunk 契约和模型端口，不直接依赖任何外部 Rerank SDK。
 */
public class RerankPostProcessorFeature implements SearchResultPostProcessorFeature {

    private static final Logger LOG = LoggerFactory.getLogger(RerankPostProcessorFeature.class);
    private static final String NAME = "Rerank";
    private static final int ORDER = 200;
    private static final String EVENT_RERANK = "retrieval.rerank";
    private static final String SETTING_RERANK_INPUT_TOP_K = "rerankInputTopK";
    private static final String SETTING_RERANK_INPUT_TOP_K_DOTTED = "rerank.inputTopK";
    private static final String SETTING_RERANK_MAX_TEXT_CHARS = "rerankMaxTextChars";
    private static final String SETTING_RERANK_MAX_TEXT_CHARS_DOTTED = "rerank.maxTextChars";
    private static final int DEFAULT_RERANK_MAX_TEXT_CHARS = 4000;

    private final RerankModelPort rerankModelPort;
    private final ObservationPort observationPort;

    public RerankPostProcessorFeature(RerankModelPort rerankModelPort) {
        this(rerankModelPort, null);
    }

    public RerankPostProcessorFeature(RerankModelPort rerankModelPort, ObservationPort observationPort) {
        this.rerankModelPort = Objects.requireNonNullElse(rerankModelPort, RerankModelPort.noop());
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
                .filter(Objects::nonNull)
                // Rerank 是成本较高的精排阶段，先用 inputTopK 收窄候选，再由 rerankTopK 控制输出。
                .limit(rerankInputTopK(options))
                .toList();
        if (candidates.isEmpty()) {
            recordRerankEvent(context, "skipped", 0, 0, 0L, timeoutMs(options), "");
            return chunks;
        }
        List<RetrievedChunk> modelCandidates = candidates.stream()
                .map(candidate -> truncateForRerank(candidate, options))
                .toList();
        long started = System.nanoTime();
        List<RetrievedChunk> reranked;
        try {
            reranked = invokeRerank(options, context, modelCandidates);
        } catch (RerankTimeoutException ex) {
            LOG.debug("Rerank post processor timed out, fallback to original chunks", ex);
            recordRerankEvent(context, "timeout", candidates.size(), chunks.size(), elapsedMs(started),
                    timeoutMs(options), ex.getClass().getSimpleName());
            return chunks;
        } catch (RuntimeException ex) {
            LOG.debug("Rerank post processor failed, fallback to original chunks", ex);
            recordRerankEvent(context, "fallback", candidates.size(), chunks.size(), elapsedMs(started),
                    timeoutMs(options), ex.getClass().getSimpleName());
            return chunks;
        }
        if (reranked == null || reranked.isEmpty()) {
            recordRerankEvent(context, "empty", candidates.size(), chunks.size(), elapsedMs(started),
                    timeoutMs(options), "");
            return chunks;
        }
        List<RetrievedChunk> normalized = normalizeRerankedChunks(candidates, modelCandidates, reranked);
        if (normalized.isEmpty()) {
            recordRerankEvent(context, "unmatched", candidates.size(), chunks.size(), elapsedMs(started),
                    timeoutMs(options), "");
            return chunks;
        }
        List<RetrievedChunk> output = normalized.stream()
                .limit(options.rerankTopK())
                .toList();
        recordRerankEvent(context, "success", candidates.size(), output.size(), elapsedMs(started),
                timeoutMs(options), "");
        return output;
    }

    private List<RetrievedChunk> invokeRerank(RetrievalOptions options,
                                              SearchContext context,
                                              List<RetrievedChunk> candidates) {
        long timeoutMs = timeoutMs(options);
        if (timeoutMs <= 0) {
            return rerankModelPort.rerank(options.rerankModel(), context.getMainQuestion(), candidates);
        }
        CompletableFuture<List<RetrievedChunk>> future = CompletableFuture.supplyAsync(
                () -> rerankModelPort.rerank(options.rerankModel(), context.getMainQuestion(), candidates));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new RerankTimeoutException("rerank timed out after " + timeoutMs + "ms", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IllegalStateException("rerank interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("rerank failed", cause);
        }
    }

    private long rerankInputTopK(RetrievalOptions options) {
        if (options == null) {
            return 1L;
        }
        int configured = intSetting(options, SETTING_RERANK_INPUT_TOP_K,
                SETTING_RERANK_INPUT_TOP_K_DOTTED, options.fusionTopK());
        return Math.max(1L, configured);
    }

    private List<RetrievedChunk> normalizeRerankedChunks(List<RetrievedChunk> candidates,
                                                         List<RetrievedChunk> modelCandidates,
                                                         List<RetrievedChunk> reranked) {
        Map<String, RetrievedChunk> originalByKey = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            RetrievedChunk candidate = candidates.get(index);
            originalByKey.putIfAbsent(chunkKey(candidate), candidate);
            if (index < modelCandidates.size()) {
                // 文本被裁剪时，模型侧返回的 key 可能来自裁剪副本，仍需映射回原始 Chunk。
                originalByKey.putIfAbsent(chunkKey(modelCandidates.get(index)), candidate);
            }
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
        if (!hasText(original.getText()) && hasText(reranked.getText())) {
            merged.setText(reranked.getText());
        }
        Float rerankScore = firstNonNull(reranked.getRerankScore(), reranked.getScore(),
                original.getRerankScore(), original.getScore());
        merged.setRerankScore(rerankScore);
        merged.setScore(rerankScore);
        merged.getMetadata().putAll(Objects.requireNonNullElse(reranked.getMetadata(), Map.of()));
        merged.getChannelScores().putAll(Objects.requireNonNullElse(reranked.getChannelScores(), Map.of()));
        merged.getChannelRanks().putAll(Objects.requireNonNullElse(reranked.getChannelRanks(), Map.of()));
        mergeFusionExplanation(merged, reranked);
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
        copy.getFusionExplanation().putAll(Objects.requireNonNullElse(source.getFusionExplanation(), Map.of()));
        return copy;
    }

    private RetrievedChunk truncateForRerank(RetrievedChunk source, RetrievalOptions options) {
        RetrievedChunk copy = copyChunk(source);
        String text = copy.getText();
        int maxTextChars = rerankMaxTextChars(options);
        if (text != null && text.length() > maxTextChars) {
            copy.setText(text.substring(0, maxTextChars));
        }
        return copy;
    }

    private int rerankMaxTextChars(RetrievalOptions options) {
        return Math.max(1, intSetting(options, SETTING_RERANK_MAX_TEXT_CHARS,
                SETTING_RERANK_MAX_TEXT_CHARS_DOTTED, DEFAULT_RERANK_MAX_TEXT_CHARS));
    }

    private int intSetting(RetrievalOptions options, String key, String dottedKey, int defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        Map<String, Object> settings = options.channelSettings();
        Object value = settings.containsKey(key) ? settings.get(key) : settings.get(dottedKey);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private void mergeFusionExplanation(RetrievedChunk merged, RetrievedChunk reranked) {
        Map<String, Object> rerankedExplanation = Objects.requireNonNullElse(
                reranked.getFusionExplanation(), Map.of());
        if (rerankedExplanation.isEmpty() || rerankedExplanation.equals(merged.getFusionExplanation())) {
            return;
        }
        if (merged.getFusionExplanation().isEmpty()) {
            merged.getFusionExplanation().putAll(rerankedExplanation);
            return;
        }
        // 模型侧若返回额外解释，嵌入独立字段，避免覆盖 RRF 融合来源。
        merged.getFusionExplanation().put("rerankExplanation", new LinkedHashMap<>(rerankedExplanation));
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

    private long timeoutMs(RetrievalOptions options) {
        Duration timeout = options == null ? null : options.rerankTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return 0L;
        }
        return Math.max(1L, timeout.toMillis());
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private void recordRerankEvent(SearchContext context,
                                   String status,
                                   int inputCount,
                                   int outputCount,
                                   long durationMs,
                                   long timeoutMs,
                                   String exception) {
        if (observationPort == null) {
            return;
        }
        try {
            // 只记录低基数运维字段，避免把候选 chunk 明细写入指标标签。
            RetrievalOptions options = options(context);
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("tenant", tenantId(context));
            attributes.put("status", status);
            attributes.put("model", rerankModel(options));
            attributes.put("inputCount", String.valueOf(inputCount));
            attributes.put("outputCount", String.valueOf(outputCount));
            attributes.put("inputTopK", String.valueOf(rerankInputTopK(options)));
            attributes.put("outputTopK", String.valueOf(rerankOutputTopK(options)));
            attributes.put("durationMs", String.valueOf(durationMs));
            attributes.put("timeoutMs", String.valueOf(timeoutMs));
            attributes.put("fallback", String.valueOf(isFallbackStatus(status)));
            attributes.put("exception", Objects.requireNonNullElse(exception, ""));
            observationPort.recordEvent(new ObservationEvent(EVENT_RERANK, null, attributes));
        } catch (RuntimeException ex) {
            // 观测失败不能影响检索结果。
        }
    }

    private RetrievalOptions options(SearchContext context) {
        return context == null ? null : context.effectiveOptions();
    }

    private String rerankModel(RetrievalOptions options) {
        return options == null ? "" : options.rerankModel();
    }

    private int rerankOutputTopK(RetrievalOptions options) {
        return options == null ? 0 : options.rerankTopK();
    }

    private boolean isFallbackStatus(String status) {
        return !"success".equals(status) && !"skipped".equals(status);
    }

    private String tenantId(SearchContext context) {
        if (context == null || context.getFilter() == null || context.getFilter().system() == null) {
            return "";
        }
        return context.getFilter().system().tenantId();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class RerankTimeoutException extends RuntimeException {

        private RerankTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
