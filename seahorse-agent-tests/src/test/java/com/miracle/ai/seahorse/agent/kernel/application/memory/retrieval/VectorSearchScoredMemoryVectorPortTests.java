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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorHit;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VectorSearchScoredMemoryVectorPortTests {

    @Test
    void shouldMapVectorSearchResultsIntoScoredMemoryHits() {
        RecordingEmbeddingModelPort embeddingModelPort = new RecordingEmbeddingModelPort(List.of(0.1F, 0.2F));
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort(List.of(
                chunk(
                        "chunk-1",
                        0.87F,
                        Map.of(
                                "memoryId", "semantic-1",
                                "userId", "user-1",
                                "tenantId", "default",
                                "layer", "SEMANTIC",
                                "type", "PROJECT_FACT",
                                "generationId", "generation-9",
                                "status", "REFERENCED",
                                "embeddingModel", "text-embedding-test")),
                chunk(
                        "chunk-other-user",
                        0.99F,
                        Map.of("memoryId", "other-user-memory", "userId", "user-2", "tenantId", "default")),
                chunk(
                        "chunk-missing-user",
                        0.95F,
                        Map.of("memoryId", "missing-user-memory", "tenantId", "default"))));
        VectorSearchScoredMemoryVectorPort port = new VectorSearchScoredMemoryVectorPort(
                vectorSearchPort,
                embeddingModelPort,
                "memory_vectors",
                "memory-embedding");

        List<ScoredMemoryVectorHit> hits = port.search("user-1", "default", "Pulsar PIP-459", 5);

        assertThat(embeddingModelPort.calls).containsExactly("memory-embedding|Pulsar PIP-459");
        assertThat(vectorSearchPort.requests).hasSize(1);
        VectorSearchRequest request = vectorSearchPort.requests.get(0);
        assertThat(request.collectionName()).isEqualTo("memory_vectors");
        assertThat(request.query()).isEqualTo("Pulsar PIP-459");
        assertThat(request.vector()).containsExactly(0.1F, 0.2F);
        assertThat(request.topK()).isEqualTo(5);
        assertThat(request.filters())
                .containsEntry("userId", "user-1")
                .containsEntry("tenantId", "default");
        assertThat(request.compiledFilter().sourceFilter().system().userId()).isEqualTo("user-1");
        assertThat(request.compiledFilter().sourceFilter().system().tenantId()).isEqualTo("default");

        assertThat(hits).hasSize(1);
        ScoredMemoryVectorHit hit = hits.get(0);
        assertThat(hit.memoryId()).isEqualTo("semantic-1");
        assertThat(hit.score()).isCloseTo(0.87D, within(0.000001D));
        assertThat(hit.generationId()).isEqualTo("generation-9");
        assertThat(hit.embeddingModel()).isEqualTo("text-embedding-test");
        assertThat(hit.metadata())
                .containsEntry("layer", "SEMANTIC")
                .containsEntry("type", "PROJECT_FACT")
                .containsEntry("status", "REFERENCED")
                .containsEntry("chunkId", "chunk-1");
    }

    @Test
    void shouldSkipVectorSearchWhenEmbeddingIsUnavailable() {
        RecordingEmbeddingModelPort embeddingModelPort = new RecordingEmbeddingModelPort(List.of());
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort(List.of());
        VectorSearchScoredMemoryVectorPort port = new VectorSearchScoredMemoryVectorPort(
                vectorSearchPort,
                embeddingModelPort,
                "memory_vectors",
                "memory-embedding");

        List<ScoredMemoryVectorHit> hits = port.search("user-1", "default", "query", 5);

        assertThat(hits).isEmpty();
        assertThat(vectorSearchPort.requests).isEmpty();
    }

    private RetrievedChunk chunk(String id, Float score, Map<String, Object> metadata) {
        return RetrievedChunk.builder()
                .id(id)
                .text("content-" + id)
                .score(score)
                .metadata(metadata)
                .build();
    }

    private static final class RecordingEmbeddingModelPort implements EmbeddingModelPort {

        private final List<Float> embedding;
        private final List<String> calls = new ArrayList<>();

        private RecordingEmbeddingModelPort(List<Float> embedding) {
            this.embedding = embedding;
        }

        @Override
        public List<Float> embed(String modelId, String text) {
            calls.add(modelId + "|" + text);
            return embedding;
        }
    }

    private static final class RecordingVectorSearchPort implements VectorSearchPort {

        private final List<RetrievedChunk> chunks;
        private final List<VectorSearchRequest> requests = new ArrayList<>();

        private RecordingVectorSearchPort(List<RetrievedChunk> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> search(VectorSearchRequest request) {
            requests.add(request);
            return chunks;
        }
    }
}
