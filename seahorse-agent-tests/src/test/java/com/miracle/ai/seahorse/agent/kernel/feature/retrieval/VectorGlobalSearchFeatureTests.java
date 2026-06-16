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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorGlobalSearchFeatureTests {

    @Test
    void shouldEnableWhenIntentConfidenceIsLow() {
        VectorGlobalSearchFeature feature = featureWithCollections(List.of());

        boolean enabled = feature.enabled(SearchContext.builder()
                .intents(List.of(new SubQuestionIntent("question", List.of(score(0.3D)))))
                .build());

        assertThat(enabled).isTrue();
    }

    @Test
    void shouldSearchEveryDistinctCollection() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort();
        KnowledgeBaseQueryPort knowledgeBaseQueryPort = new StaticKnowledgeBaseQueryPort(List.of(
                new KnowledgeBaseRef(1L, "A", "collection-a"),
                new KnowledgeBaseRef(2L, "B", "collection-a"),
                new KnowledgeBaseRef(3L, "C", "collection-c")));
        VectorGlobalSearchFeature feature = new VectorGlobalSearchFeature(knowledgeBaseQueryPort, vectorSearchPort);

        List<RetrievedChunk> chunks = feature.search(SearchContext.builder()
                        .rewrittenQuestion("如何使用产品")
                        .topK(3)
                        .build())
                .getChunks();

        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::collectionName)
                .containsExactly("collection-a", "collection-c");
        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::topK)
                .containsExactly(6, 6);
        assertThat(chunks).hasSize(2);
    }

    @Test
    void shouldContinueSearchingWhenOneCollectionFails() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort();
        vectorSearchPort.failingCollections.add("missing-collection");
        KnowledgeBaseQueryPort knowledgeBaseQueryPort = new StaticKnowledgeBaseQueryPort(List.of(
                new KnowledgeBaseRef(1L, "Missing", "missing-collection"),
                new KnowledgeBaseRef(2L, "Valid", "valid-collection")));
        VectorGlobalSearchFeature feature = new VectorGlobalSearchFeature(knowledgeBaseQueryPort, vectorSearchPort);

        var result = feature.search(SearchContext.builder()
                .rewrittenQuestion("Seahorse Agent vector model")
                .topK(3)
                .build());

        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::collectionName)
                .containsExactly("missing-collection", "valid-collection");
        assertThat(result.getChunks())
                .extracting(RetrievedChunk::getId)
                .containsExactly("valid-collection-chunk");
        assertThat(result.getMetadata()).containsEntry("failedCollectionCount", 1);
    }

    @Test
    void shouldEmbedQueryOnlyOnceForAllCollections() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort();
        CountingEmbeddingModelPort embeddingModelPort = new CountingEmbeddingModelPort();
        KnowledgeBaseQueryPort knowledgeBaseQueryPort = new StaticKnowledgeBaseQueryPort(List.of(
                new KnowledgeBaseRef(1L, "A", "collection-a"),
                new KnowledgeBaseRef(2L, "B", "collection-b"),
                new KnowledgeBaseRef(3L, "C", "collection-c")));
        VectorGlobalSearchFeature feature =
                new VectorGlobalSearchFeature(knowledgeBaseQueryPort, vectorSearchPort, embeddingModelPort);

        feature.search(SearchContext.builder()
                .rewrittenQuestion("Seahorse Agent vector model")
                .topK(3)
                .build());

        assertThat(embeddingModelPort.callCount).isOne();
        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::vector)
                .containsOnly(List.of(0.1F, 0.2F, 0.3F));
    }

    @Test
    void shouldLimitGlobalCollectionScanToKeepRetrievalResponsive() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort();
        KnowledgeBaseQueryPort knowledgeBaseQueryPort = new StaticKnowledgeBaseQueryPort(List.of(
                new KnowledgeBaseRef(1L, "A", "collection-a"),
                new KnowledgeBaseRef(2L, "B", "collection-b"),
                new KnowledgeBaseRef(3L, "C", "collection-c"),
                new KnowledgeBaseRef(4L, "D", "collection-d"),
                new KnowledgeBaseRef(5L, "E", "collection-e"),
                new KnowledgeBaseRef(6L, "F", "collection-f")));
        VectorGlobalSearchFeature feature = new VectorGlobalSearchFeature(knowledgeBaseQueryPort, vectorSearchPort);

        var result = feature.search(SearchContext.builder()
                .rewrittenQuestion("Seahorse Agent vector model")
                .topK(3)
                .build());

        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::collectionName)
                .containsExactly("collection-a", "collection-b", "collection-c", "collection-d", "collection-e");
        assertThat(result.getMetadata())
                .containsEntry("searchableCollectionCount", 6)
                .containsEntry("collectionCount", 5);
    }

    @Test
    void shouldRequestSearchableCollectionsForCurrentEmbeddingModel() {
        RecordingKnowledgeBaseQueryPort knowledgeBaseQueryPort = new RecordingKnowledgeBaseQueryPort(List.of(
                new KnowledgeBaseRef(1L, "A", "collection-a")));
        VectorGlobalSearchFeature feature =
                new VectorGlobalSearchFeature(knowledgeBaseQueryPort, new RecordingVectorSearchPort());

        feature.search(SearchContext.builder()
                .rewrittenQuestion("Seahorse Agent vector model")
                .options(RetrievalOptions.builder().embeddingModel("nomic-embed-text").build())
                .build());

        assertThat(knowledgeBaseQueryPort.embeddingModels).containsExactly("nomic-embed-text");
    }

    @Test
    void shouldOnlySearchCollectionsMatchingSystemKnowledgeBaseFilter() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort();
        KnowledgeBaseQueryPort knowledgeBaseQueryPort = new StaticKnowledgeBaseQueryPort(List.of(
                new KnowledgeBaseRef(1L, "Stale", "missing-collection"),
                new KnowledgeBaseRef(2L, "Current", "current-collection")));
        VectorGlobalSearchFeature feature = new VectorGlobalSearchFeature(knowledgeBaseQueryPort, vectorSearchPort);

        feature.search(SearchContext.builder()
                .rewrittenQuestion("Seahorse Agent vector model")
                .topK(3)
                .filter(RetrievalFilter.builder()
                        .system(SystemRetrievalFilter.builder()
                                .knowledgeBaseIds(List.of("2"))
                                .build())
                        .build())
                .build());

        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::collectionName)
                .containsExactly("current-collection");
    }

    private VectorGlobalSearchFeature featureWithCollections(List<KnowledgeBaseRef> refs) {
        return new VectorGlobalSearchFeature(new StaticKnowledgeBaseQueryPort(refs), request -> List.of());
    }

    private IntentScore score(double value) {
        return IntentScore.builder()
                .node(IntentNode.builder().id("intent-a").build())
                .score(value)
                .build();
    }

    private record StaticKnowledgeBaseQueryPort(List<KnowledgeBaseRef> refs) implements KnowledgeBaseQueryPort {

        @Override
        public List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
            return refs;
        }

        @Override
        public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeChunkSummary> listChunksByDocId(Long docId) {
            return List.of();
        }
    }

    private static final class RecordingKnowledgeBaseQueryPort implements KnowledgeBaseQueryPort {

        private final List<KnowledgeBaseRef> refs;
        private final List<String> embeddingModels = new ArrayList<>();

        private RecordingKnowledgeBaseQueryPort(List<KnowledgeBaseRef> refs) {
            this.refs = refs;
        }

        @Override
        public List<KnowledgeBaseRef> listSearchableKnowledgeBases(String embeddingModel) {
            embeddingModels.add(embeddingModel);
            return refs;
        }

        @Override
        public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeChunkSummary> listChunksByDocId(Long docId) {
            return List.of();
        }
    }

    private static class RecordingVectorSearchPort implements com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort {

        private final List<VectorSearchRequest> requests = new ArrayList<>();
        private final List<String> failingCollections = new ArrayList<>();

        @Override
        public List<RetrievedChunk> search(VectorSearchRequest request) {
            requests.add(request);
            if (failingCollections.contains(request.collectionName())) {
                throw new IllegalStateException("collection not found: " + request.collectionName());
            }
            return List.of(RetrievedChunk.builder()
                    .id(request.collectionName() + "-chunk")
                    .text("content")
                    .score(0.9F)
                    .build());
        }
    }

    private static class CountingEmbeddingModelPort implements EmbeddingModelPort {

        private int callCount;

        @Override
        public List<Float> embed(String modelId, String text) {
            callCount++;
            return List.of(0.1F, 0.2F, 0.3F);
        }
    }
}
