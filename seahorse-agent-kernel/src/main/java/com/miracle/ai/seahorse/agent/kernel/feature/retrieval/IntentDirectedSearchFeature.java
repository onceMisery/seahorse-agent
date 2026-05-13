/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.feature.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 原生意图定向检索通道。
 *
 * <p>该 Feature 基于内核自有意图模型筛选 KB 意图，并通过统一向量检索端口访问 Milvus、pgvector 等适配器。
 * 它保留旧 IntentDirectedSearchChannel 的核心语义：KB 意图优先、节点 topK 优先、按倍率扩大召回。
 */
public class IntentDirectedSearchFeature implements SearchChannelFeature {

    public static final String NAME = "IntentDirectedSearch";
    private static final Logger LOG = LoggerFactory.getLogger(IntentDirectedSearchFeature.class);
    private static final String LOG_MSG_INTENT_FAILED = "意图定向检索失败，意图ID={}，集合={}";
    private static final double DEFAULT_MIN_INTENT_SCORE = 0.4D;
    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_TOP_K_MULTIPLIER = 2;
    private static final int ORDER = 1;

    private final VectorSearchPort vectorSearchPort;
    private final Executor retrievalExecutor;
    private final SearchSettings settings;
    private final EmbeddingModelPort embeddingModelPort;

    public IntentDirectedSearchFeature(VectorSearchPort vectorSearchPort, Executor retrievalExecutor) {
        this(vectorSearchPort, retrievalExecutor,
                new SearchSettings(DEFAULT_MIN_INTENT_SCORE, DEFAULT_TOP_K_MULTIPLIER),
                EmbeddingModelPort.noop());
    }

    public IntentDirectedSearchFeature(VectorSearchPort vectorSearchPort,
                                       Executor retrievalExecutor,
                                       SearchSettings settings) {
        this(vectorSearchPort, retrievalExecutor, settings, EmbeddingModelPort.noop());
    }

    public IntentDirectedSearchFeature(VectorSearchPort vectorSearchPort,
                                       Executor retrievalExecutor,
                                       SearchSettings settings,
                                       EmbeddingModelPort embeddingModelPort) {
        this.vectorSearchPort = Objects.requireNonNull(vectorSearchPort, "vectorSearchPort must not be null");
        this.retrievalExecutor = Objects.requireNonNull(retrievalExecutor, "retrievalExecutor must not be null");
        this.settings = Objects.requireNonNullElse(settings,
                new SearchSettings(DEFAULT_MIN_INTENT_SCORE, DEFAULT_TOP_K_MULTIPLIER));
        this.embeddingModelPort = Objects.requireNonNullElseGet(embeddingModelPort, EmbeddingModelPort::noop);
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
        return SearchChannelType.INTENT_DIRECTED;
    }

    @Override
    public boolean enabled(SearchContext context) {
        return !extractKbIntents(context).isEmpty();
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long start = System.currentTimeMillis();
        List<IntentScore> kbIntents = extractKbIntents(context);
        if (kbIntents.isEmpty()) {
            return result(List.of(), start, 0);
        }
        List<RetrievedChunk> chunks = retrieveByIntents(context, kbIntents);
        return result(chunks, start, kbIntents.size());
    }

    private SearchChannelResult result(List<RetrievedChunk> chunks, long start, int intentCount) {
        return SearchChannelResult.builder()
                .channelType(channelType())
                .channelName(name())
                .chunks(chunks)
                .latencyMs(System.currentTimeMillis() - start)
                .metadata(Map.of("intentCount", intentCount))
                .build();
    }

    private List<RetrievedChunk> retrieveByIntents(SearchContext context, List<IntentScore> kbIntents) {
        List<CompletableFuture<List<RetrievedChunk>>> futures = kbIntents.stream()
                .map(intent -> CompletableFuture.supplyAsync(() -> searchIntent(context, intent), retrievalExecutor))
                .toList();
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (CompletableFuture<List<RetrievedChunk>> future : futures) {
            chunks.addAll(Objects.requireNonNullElse(future.join(), List.of()));
        }
        return chunks;
    }

    private List<RetrievedChunk> searchIntent(SearchContext context, IntentScore intentScore) {
        IntentNode node = intentScore.getNode();
        try {
            VectorSearchRequest request = new VectorSearchRequest(
                    node.getCollectionName(),
                    resolveQuestion(context),
                    queryVector(context),
                    resolveTopK(context, node),
                    Map.of("intentId", Objects.requireNonNullElse(node.getId(), "")),
                    context == null ? null : context.getCompiledFilter());
            return Objects.requireNonNullElse(vectorSearchPort.search(request), List.of());
        } catch (Exception ex) {
            LOG.error(LOG_MSG_INTENT_FAILED, node.getId(), node.getCollectionName(), ex);
            return List.of();
        }
    }

    private List<IntentScore> extractKbIntents(SearchContext context) {
        if (context == null || context.getIntents() == null) {
            return List.of();
        }
        return context.getIntents()
                .stream()
                .filter(Objects::nonNull)
                .map(SubQuestionIntent::intentScores)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(this::isSearchableKbIntent)
                .sorted(Comparator.comparingDouble(IntentScore::getScore).reversed())
                .toList();
    }

    private boolean isSearchableKbIntent(IntentScore intentScore) {
        if (intentScore == null || intentScore.getScore() < settings.minIntentScore()) {
            return false;
        }
        IntentNode node = intentScore.getNode();
        if (node == null || !node.isKb()) {
            return false;
        }
        return hasText(node.getCollectionName());
    }

    private String resolveQuestion(SearchContext context) {
        if (context == null) {
            return "";
        }
        String mainQuestion = context.getMainQuestion();
        if (hasText(mainQuestion)) {
            return mainQuestion;
        }
        List<SubQuestionIntent> intents = context.getIntents();
        if (intents == null || intents.isEmpty() || intents.get(0) == null) {
            return "";
        }
        return Objects.requireNonNullElse(intents.get(0).subQuestion(), "");
    }

    private List<Float> queryVector(SearchContext context) {
        String question = resolveQuestion(context);
        if (!hasText(question)) {
            return List.of();
        }
        String modelId = context == null ? "" : context.effectiveOptions().embeddingModel();
        return Objects.requireNonNullElse(embeddingModelPort.embed(modelId, question), List.of());
    }

    private int resolveTopK(SearchContext context, IntentNode node) {
        int baseTopK = DEFAULT_TOP_K;
        if (context != null && context.getTopK() > 0) {
            baseTopK = context.getTopK();
        }
        if (node != null && node.getTopK() != null && node.getTopK() > 0) {
            baseTopK = node.getTopK();
        }
        if (settings.topKMultiplier() <= 0) {
            return baseTopK;
        }
        return baseTopK * settings.topKMultiplier();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 意图定向检索配置快照。
     *
     * @param minIntentScore 最低意图分数
     * @param topKMultiplier 召回倍率
     */
    public record SearchSettings(double minIntentScore, int topKMultiplier) {
    }
}
