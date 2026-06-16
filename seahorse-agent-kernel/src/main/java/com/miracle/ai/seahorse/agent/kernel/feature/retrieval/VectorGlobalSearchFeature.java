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

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 原生全局向量检索通道。
 *
 * <p>该 Feature 通过知识库查询端口枚举 collection，再通过向量检索端口执行检索，替代旧
 * VectorGlobalSearchChannel 对 MyBatis Mapper 和 RetrieverService 的直接依赖。
 */
public class VectorGlobalSearchFeature implements SearchChannelFeature {

    private static final Logger LOG = LoggerFactory.getLogger(VectorGlobalSearchFeature.class);

    private static final String NAME = "VectorGlobalSearch";
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.6D;
    private static final double DEFAULT_SINGLE_INTENT_SUPPLEMENT_THRESHOLD = 0.85D;
    private static final int DEFAULT_TOP_K_MULTIPLIER = 2;
    private static final int DEFAULT_MAX_COLLECTIONS = 5;
    private static final int ORDER = 10;

    private final KnowledgeBaseQueryPort knowledgeBaseQueryPort;
    private final VectorSearchPort vectorSearchPort;
    private final EmbeddingModelPort embeddingModelPort;

    public VectorGlobalSearchFeature(KnowledgeBaseQueryPort knowledgeBaseQueryPort,
                                     VectorSearchPort vectorSearchPort) {
        this(knowledgeBaseQueryPort, vectorSearchPort, EmbeddingModelPort.noop());
    }

    public VectorGlobalSearchFeature(KnowledgeBaseQueryPort knowledgeBaseQueryPort,
                                     VectorSearchPort vectorSearchPort,
                                     EmbeddingModelPort embeddingModelPort) {
        this.knowledgeBaseQueryPort = Objects.requireNonNull(knowledgeBaseQueryPort,
                "knowledgeBaseQueryPort must not be null");
        this.vectorSearchPort = Objects.requireNonNull(vectorSearchPort, "vectorSearchPort must not be null");
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
        return SearchChannelType.VECTOR_GLOBAL;
    }

    @Override
    public boolean enabled(SearchContext context) {
        List<IntentScore> scores = allScores(context);
        if (scores.isEmpty()) {
            return true;
        }
        double maxScore = scores.stream()
                .mapToDouble(IntentScore::getScore)
                .max()
                .orElse(0.0D);
        if (maxScore < DEFAULT_CONFIDENCE_THRESHOLD) {
            return true;
        }
        return scores.size() == 1 && maxScore < DEFAULT_SINGLE_INTENT_SUPPLEMENT_THRESHOLD;
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long start = System.currentTimeMillis();
        String embeddingModel = embeddingModel(context);
        List<String> searchableCollections = collectionNames(embeddingModel, context);
        List<String> collections = searchableCollections.stream()
                .limit(DEFAULT_MAX_COLLECTIONS)
                .toList();
        List<Float> queryVector = collections.isEmpty() ? List.of() : queryVector(context);
        RetrievalResult retrieval = retrieveAll(context, collections, queryVector);
        return SearchChannelResult.builder()
                .channelType(channelType())
                .channelName(name())
                .chunks(retrieval.chunks())
                .latencyMs(System.currentTimeMillis() - start)
                .metadata(Map.of(
                        "embeddingModel", embeddingModel,
                        "searchableCollectionCount", searchableCollections.size(),
                        "collectionCount", collections.size(),
                        "failedCollectionCount", retrieval.failedCollectionCount()))
                .build();
    }

    private RetrievalResult retrieveAll(SearchContext context, List<String> collections, List<Float> queryVector) {
        if (collections.isEmpty()) {
            return new RetrievalResult(List.of(), 0);
        }
        List<RetrievedChunk> chunks = new ArrayList<>();
        int failedCollectionCount = 0;
        for (String collectionName : collections) {
            try {
                chunks.addAll(searchCollection(context, collectionName, queryVector));
            } catch (RuntimeException ex) {
                failedCollectionCount++;
                LOG.warn("Vector global search skipped collection after failure: collection={}, error={}",
                        collectionName, ex.toString());
            }
        }
        return new RetrievalResult(chunks, failedCollectionCount);
    }

    private List<RetrievedChunk> searchCollection(SearchContext context, String collectionName, List<Float> queryVector) {
        VectorSearchRequest request = new VectorSearchRequest(
                collectionName,
                context == null ? "" : context.getMainQuestion(),
                queryVector,
                topK(context),
                Map.of(),
                context == null ? null : context.getCompiledFilter());
        return Objects.requireNonNullElse(vectorSearchPort.search(request), List.of());
    }

    private List<Float> queryVector(SearchContext context) {
        if (context == null) {
            return List.of();
        }
        String question = context.getMainQuestion();
        if (!hasText(question)) {
            return List.of();
        }
        return Objects.requireNonNullElse(
                embeddingModelPort.embed(context.effectiveOptions().embeddingModel(), question), List.of());
    }

    private List<String> collectionNames(String embeddingModel, SearchContext context) {
        SystemRetrievalFilter systemFilter = systemFilter(context);
        return knowledgeBaseQueryPort.listSearchableKnowledgeBases(embeddingModel)
                .stream()
                .filter(Objects::nonNull)
                .filter(ref -> matchesKnowledgeBaseScope(ref, systemFilter))
                .map(KnowledgeBaseRef::collectionName)
                .filter(this::hasText)
                .filter(collectionName -> matchesCollectionScope(collectionName, systemFilter))
                .distinct()
                .toList();
    }

    private SystemRetrievalFilter systemFilter(SearchContext context) {
        RetrievalFilter filter = context == null ? null : context.getFilter();
        return filter == null ? null : filter.system();
    }

    private boolean matchesKnowledgeBaseScope(KnowledgeBaseRef ref, SystemRetrievalFilter filter) {
        if (filter == null || filter.knowledgeBaseIds().isEmpty()) {
            return true;
        }
        return filter.knowledgeBaseIds().contains(String.valueOf(ref.id()));
    }

    private boolean matchesCollectionScope(String collectionName, SystemRetrievalFilter filter) {
        if (filter == null || filter.collectionNames().isEmpty()) {
            return true;
        }
        return filter.collectionNames().contains(collectionName);
    }

    private String embeddingModel(SearchContext context) {
        if (context == null) {
            return "";
        }
        return Objects.requireNonNullElse(context.effectiveOptions().embeddingModel(), "");
    }

    private int topK(SearchContext context) {
        int baseTopK = context == null || context.getTopK() <= 0 ? 5 : context.getTopK();
        return baseTopK * DEFAULT_TOP_K_MULTIPLIER;
    }

    private List<IntentScore> allScores(SearchContext context) {
        if (context == null || context.getIntents() == null) {
            return List.of();
        }
        return context.getIntents()
                .stream()
                .filter(Objects::nonNull)
                .map(SubQuestionIntent::intentScores)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RetrievalResult(List<RetrievedChunk> chunks, int failedCollectionCount) {
    }
}
