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
import java.util.StringJoiner;

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
    private static final String UNKNOWN_CHANNEL = "UNKNOWN";

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
            RetrievalOptions options = context == null ? null : context.effectiveOptions();
            recordRrfEvent(context, "skipped", 0, 0, 0, rrfK(options),
                    fusionTopK(options), channelWeightsSummary(options));
            return List.of();
        }
        RetrievalOptions options = context.effectiveOptions();
        int rrfK = rrfK(options);
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        Map<String, Float> scores = new LinkedHashMap<>();
        Map<String, Map<String, Float>> channelContributions = new LinkedHashMap<>();
        Map<String, Map<String, Float>> channelWeights = new LinkedHashMap<>();
        for (SearchChannelResult result : results) {
            List<RetrievedChunk> channelChunks = safeChunks(result);
            String channelKey = channelKey(result);
            float weight = channelWeight(result, options);
            for (int index = 0; index < channelChunks.size(); index++) {
                RetrievedChunk source = channelChunks.get(index);
                String key = dedupeKey(source);
                RetrievedChunk target = merged.computeIfAbsent(key, ignored -> copyChunk(source));
                int rank = index + 1;
                float rrfScore = weight / (rrfK + rank);
                scores.merge(key, rrfScore, Float::sum);
                // 解释字段只进入 fusionExplanation，避免污染动态 metadata 过滤条件。
                channelContributions.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                        .merge(channelKey, rrfScore, Float::sum);
                channelWeights.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(channelKey, weight);
                target.getChannelRanks().put(channelKey, rank);
                target.getChannelScores().put(channelKey, Objects.requireNonNullElse(source.getScore(), 0F));
            }
        }
        List<RetrievedChunk> fused = merged.entrySet().stream()
                .peek(entry -> {
                    Float score = scores.get(entry.getKey());
                    entry.getValue().setFusionScore(score);
                    entry.getValue().setScore(score);
                    entry.getValue().setFusionExplanation(fusionExplanation(
                            entry.getValue(),
                            rrfK,
                            score,
                            channelContributions.get(entry.getKey()),
                            channelWeights.get(entry.getKey())));
                })
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(RetrievedChunk::getFusionScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(options.fusionTopK())
                .toList();
        recordRrfEvent(context, "success", results.size(), merged.size(), fused.size(), rrfK,
                fusionTopK(options), channelWeightsSummary(options));
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

    private String channelKey(SearchChannelResult result) {
        if (result == null) {
            return UNKNOWN_CHANNEL;
        }
        if (hasText(result.getChannelName())) {
            return result.getChannelName();
        }
        return result.getChannelType() == null ? UNKNOWN_CHANNEL : result.getChannelType().name();
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

    private int fusionTopK(RetrievalOptions options) {
        return options == null ? 0 : options.fusionTopK();
    }

    private String channelWeightsSummary(RetrievalOptions options) {
        if (options == null) {
            return "";
        }
        Object weights = options.channelSettings().get(SETTING_CHANNEL_WEIGHTS);
        if (!(weights instanceof Map<?, ?> weightMap) || weightMap.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        weightMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Objects::toString)))
                .forEach(entry -> {
                    Float parsed = weight(entry.getValue());
                    if (parsed != null) {
                        joiner.add(Objects.toString(entry.getKey(), "") + "=" + parsed);
                    }
                });
        return joiner.toString();
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
                                int rrfK,
                                int fusionTopK,
                                String channelWeights) {
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
                    "fusionTopK", String.valueOf(fusionTopK),
                    "rrfK", String.valueOf(rrfK),
                    "channelWeights", Objects.requireNonNullElse(channelWeights, ""))));
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
        copy.setFusionScore(source.getFusionScore());
        copy.setRerankScore(source.getRerankScore());
        copy.getMetadata().putAll(Objects.requireNonNullElse(source.getMetadata(), Map.of()));
        copy.getChannelScores().putAll(Objects.requireNonNullElse(source.getChannelScores(), Map.of()));
        copy.getChannelRanks().putAll(Objects.requireNonNullElse(source.getChannelRanks(), Map.of()));
        copy.getFusionExplanation().putAll(Objects.requireNonNullElse(source.getFusionExplanation(), Map.of()));
        return copy;
    }

    private Map<String, Object> fusionExplanation(RetrievedChunk chunk,
                                                  int rrfK,
                                                  Float fusionScore,
                                                  Map<String, Float> channelContributions,
                                                  Map<String, Float> channelWeights) {
        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("strategy", "RRF");
        explanation.put("rrfK", rrfK);
        explanation.put("fusionScore", fusionScore);
        explanation.put("channelRanks", new LinkedHashMap<>(
                Objects.requireNonNullElse(chunk.getChannelRanks(), Map.of())));
        explanation.put("channelScores", new LinkedHashMap<>(
                Objects.requireNonNullElse(chunk.getChannelScores(), Map.of())));
        explanation.put("channelContributions", new LinkedHashMap<>(
                Objects.requireNonNullElse(channelContributions, Map.of())));
        explanation.put("channelWeights", new LinkedHashMap<>(
                Objects.requireNonNullElse(channelWeights, Map.of())));
        return explanation;
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
