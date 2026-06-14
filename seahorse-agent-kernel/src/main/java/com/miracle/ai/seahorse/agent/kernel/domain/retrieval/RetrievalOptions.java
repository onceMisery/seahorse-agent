package com.miracle.ai.seahorse.agent.kernel.domain.retrieval;

import lombok.Builder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 检索策略参数。
 * <p>
 * 默认值保持向量检索可用，关键词、Rerank 等增强能力后续按配置逐步开启。
 */
@Builder
public record RetrievalOptions(
        int finalTopK,
        int vectorTopK,
        int keywordTopK,
        int fusionTopK,
        int rerankTopK,
        boolean enableVector,
        boolean enableIntentDirected,
        boolean enableKeyword,
        boolean enableRrf,
        boolean enableRerank,
        String embeddingModel,
        String rerankModel,
        Duration vectorTimeout,
        Duration keywordTimeout,
        Duration rerankTimeout,
        Map<String, Object> channelSettings
) {

    public RetrievalOptions {
        finalTopK = positive(finalTopK, 5);
        vectorTopK = positive(vectorTopK, finalTopK * 4);
        keywordTopK = positive(keywordTopK, finalTopK * 4);
        fusionTopK = positive(fusionTopK, finalTopK * 3);
        rerankTopK = positive(rerankTopK, finalTopK);
        embeddingModel = Objects.requireNonNullElse(embeddingModel, "");
        rerankModel = Objects.requireNonNullElse(rerankModel, "");
        channelSettings = Map.copyOf(Objects.requireNonNullElse(channelSettings, Map.of()));
    }

    public static RetrievalOptions defaults(int topK) {
        int finalTopK = positive(topK, 5);
        return RetrievalOptions.builder()
                .finalTopK(finalTopK)
                .vectorTopK(finalTopK * 4)
                .keywordTopK(finalTopK * 4)
                .fusionTopK(finalTopK * 3)
                .rerankTopK(finalTopK)
                .enableVector(true)
                .enableIntentDirected(true)
                .enableKeyword(false)
                .enableRrf(true)
                .enableRerank(false)
                .build();
    }

    public RetrievalOptions withEnableKeyword(boolean enabled) {
        return new RetrievalOptions(finalTopK, vectorTopK, keywordTopK, fusionTopK, rerankTopK,
                enableVector, enableIntentDirected, enabled, enableRrf, enableRerank,
                embeddingModel, rerankModel, vectorTimeout, keywordTimeout, rerankTimeout, channelSettings);
    }

    public RetrievalOptions withEmbeddingModel(String model) {
        return new RetrievalOptions(finalTopK, vectorTopK, keywordTopK, fusionTopK, rerankTopK,
                enableVector, enableIntentDirected, enableKeyword, enableRrf, enableRerank,
                model, rerankModel, vectorTimeout, keywordTimeout, rerankTimeout, channelSettings);
    }

    public RetrievalOptions withAdditionalChannelSettings(Map<String, Object> additionalSettings) {
        if (additionalSettings == null || additionalSettings.isEmpty()) {
            return this;
        }
        Map<String, Object> mergedSettings = new LinkedHashMap<>(channelSettings);
        mergedSettings.putAll(additionalSettings);
        return new RetrievalOptions(finalTopK, vectorTopK, keywordTopK, fusionTopK, rerankTopK,
                enableVector, enableIntentDirected, enableKeyword, enableRrf, enableRerank,
                embeddingModel, rerankModel, vectorTimeout, keywordTimeout, rerankTimeout, mergedSettings);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
