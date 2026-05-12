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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;

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

    private static final String NAME = "VectorGlobalSearch";
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.6D;
    private static final double DEFAULT_SINGLE_INTENT_SUPPLEMENT_THRESHOLD = 0.85D;
    private static final int DEFAULT_TOP_K_MULTIPLIER = 2;
    private static final int ORDER = 10;

    private final KnowledgeBaseQueryPort knowledgeBaseQueryPort;
    private final VectorSearchPort vectorSearchPort;

    public VectorGlobalSearchFeature(KnowledgeBaseQueryPort knowledgeBaseQueryPort,
                                     VectorSearchPort vectorSearchPort) {
        this.knowledgeBaseQueryPort = Objects.requireNonNull(knowledgeBaseQueryPort,
                "knowledgeBaseQueryPort must not be null");
        this.vectorSearchPort = Objects.requireNonNull(vectorSearchPort, "vectorSearchPort must not be null");
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
        List<RetrievedChunk> chunks = retrieveAll(context);
        return SearchChannelResult.builder()
                .channelType(channelType())
                .channelName(name())
                .chunks(chunks)
                .latencyMs(System.currentTimeMillis() - start)
                .metadata(Map.of("collectionCount", collectionNames().size()))
                .build();
    }

    private List<RetrievedChunk> retrieveAll(SearchContext context) {
        List<String> collections = collectionNames();
        if (collections.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (String collectionName : collections) {
            chunks.addAll(searchCollection(context, collectionName));
        }
        return chunks;
    }

    private List<RetrievedChunk> searchCollection(SearchContext context, String collectionName) {
        VectorSearchRequest request = new VectorSearchRequest(
                collectionName,
                context == null ? "" : context.getMainQuestion(),
                List.of(),
                topK(context),
                Map.of());
        return Objects.requireNonNullElse(vectorSearchPort.search(request), List.of());
    }

    private List<String> collectionNames() {
        return knowledgeBaseQueryPort.listSearchableKnowledgeBases()
                .stream()
                .filter(Objects::nonNull)
                .map(KnowledgeBaseRef::collectionName)
                .filter(this::hasText)
                .distinct()
                .toList();
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
}
