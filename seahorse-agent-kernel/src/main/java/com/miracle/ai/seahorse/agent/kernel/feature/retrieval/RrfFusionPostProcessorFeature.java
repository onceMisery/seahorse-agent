package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RRF 多通道融合后处理器。
 * <p>
 * RRF 按通道内排名融合候选，避免直接比较向量分、关键词分、意图分等不可比的原始分数。
 */
public class RrfFusionPostProcessorFeature implements SearchResultPostProcessorFeature {

    private static final String NAME = "RrfFusion";
    private static final int ORDER = 100;
    private static final int DEFAULT_RRF_K = 60;
    private static final String EVENT_RRF = "retrieval.rrf";
    private static final String SETTING_RRF_K = "rrfK";
    private static final String SETTING_RRF_K_DOTTED = "rrf.k";
    private static final String SETTING_CHANNEL_WEIGHTS = "channelWeights";

    private final ObservationPort observationPort;

    public RrfFusionPostProcessorFeature() {
        this(null);
    }

    public RrfFusionPostProcessorFeature(ObservationPort observationPort) {
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
        return context != null && context.getOptions() != null && context.effectiveOptions().enableRrf();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (results == null || results.isEmpty()) {
            recordRrfEvent(context, "skipped", 0, 0, 0, DEFAULT_RRF_K);
            return List.of();
        }
        RetrievalOptions options = context.effectiveOptions();
        int rrfK = rrfK(options);
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        Map<String, Float> scores = new LinkedHashMap<>();
        for (SearchChannelResult result : results) {
            List<RetrievedChunk> channelChunks = safeChunks(result);
            for (int index = 0; index < channelChunks.size(); index++) {
                RetrievedChunk source = channelChunks.get(index);
                String key = dedupeKey(source);
                RetrievedChunk target = merged.computeIfAbsent(key, ignored -> copyChunk(source));
                int rank = index + 1;
                float rrfScore = channelWeight(result, options) / (rrfK + rank);
                scores.merge(key, rrfScore, Float::sum);
                target.getChannelRanks().put(result.getChannelName(), rank);
                target.getChannelScores().put(result.getChannelName(), Objects.requireNonNullElse(source.getScore(), 0F));
            }
        }
        List<RetrievedChunk> fused = merged.entrySet().stream()
                .peek(entry -> {
                    Float score = scores.get(entry.getKey());
                    entry.getValue().setFusionScore(score);
                    entry.getValue().setScore(score);
                })
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(RetrievedChunk::getFusionScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(options.fusionTopK())
                .toList();
        recordRrfEvent(context, "success", results.size(), merged.size(), fused.size(), rrfK);
        return fused;
    }

    private List<RetrievedChunk> safeChunks(SearchChannelResult result) {
        if (result == null || result.getChunks() == null) {
            return List.of();
        }
        return result.getChunks();
    }

    private float channelWeight(SearchChannelResult result, RetrievalOptions options) {
        Float configured = configuredChannelWeight(result, options);
        if (configured != null && configured > 0F) {
            return configured;
        }
        if (result == null || result.getChannelName() == null) {
            return 1.0F;
        }
        if (IntentDirectedSearchFeature.NAME.equals(result.getChannelName())) {
            return 1.2F;
        }
        return 1.0F;
    }

    private Float configuredChannelWeight(SearchChannelResult result, RetrievalOptions options) {
        if (result == null || options == null) {
            return null;
        }
        Object weights = options.channelSettings().get(SETTING_CHANNEL_WEIGHTS);
        if (!(weights instanceof Map<?, ?> weightMap)) {
            return null;
        }
        Float byName = weight(weightMap.get(result.getChannelName()));
        if (byName != null) {
            return byName;
        }
        return result.getChannelType() == null ? null : weight(weightMap.get(result.getChannelType().name()));
    }

    private int rrfK(RetrievalOptions options) {
        if (options == null) {
            return DEFAULT_RRF_K;
        }
        Object value = options.channelSettings().containsKey(SETTING_RRF_K)
                ? options.channelSettings().get(SETTING_RRF_K)
                : options.channelSettings().get(SETTING_RRF_K_DOTTED);
        int configured = intValue(value, DEFAULT_RRF_K);
        return configured <= 0 ? DEFAULT_RRF_K : configured;
    }

    private Float weight(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Float.parseFloat(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private void recordRrfEvent(SearchContext context,
                                String status,
                                int channelCount,
                                int candidateCount,
                                int outputCount,
                                int rrfK) {
        if (observationPort == null) {
            return;
        }
        try {
            // 只记录低基数字段，避免把 chunkId/docId 写入指标标签。
            observationPort.recordEvent(new ObservationEvent(EVENT_RRF, null, Map.of(
                    "tenant", tenantId(context),
                    "status", status,
                    "channelCount", String.valueOf(channelCount),
                    "candidateCount", String.valueOf(candidateCount),
                    "outputCount", String.valueOf(outputCount),
                    "rrfK", String.valueOf(rrfK))));
        } catch (RuntimeException ex) {
            // 观测失败不能影响检索结果。
        }
    }

    private String tenantId(SearchContext context) {
        if (context == null || context.getFilter() == null || context.getFilter().system() == null) {
            return "";
        }
        return context.getFilter().system().tenantId();
    }

    private String dedupeKey(RetrievedChunk chunk) {
        if (chunk == null) {
            return "";
        }
        if (hasText(chunk.getId())) {
            return "id:" + chunk.getId();
        }
        if (hasText(chunk.getDocId()) && chunk.getChunkIndex() != null) {
            return "chunk:" + chunk.getDocId() + ":" + chunk.getChunkIndex();
        }
        return "text:" + sha256(Objects.requireNonNullElse(chunk.getText(), ""));
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
        copy.getMetadata().putAll(Objects.requireNonNullElse(source.getMetadata(), Map.of()));
        return copy;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
