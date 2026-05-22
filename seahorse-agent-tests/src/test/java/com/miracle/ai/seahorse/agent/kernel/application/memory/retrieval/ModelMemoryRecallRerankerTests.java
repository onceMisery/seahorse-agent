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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelMemoryRecallRerankerTests {

    @Test
    void shouldRerankMemoryCandidatesWithRerankModelScores() {
        RecordingRerankModelPort modelPort = new RecordingRerankModelPort(List.of(
                chunk("memory-b", 0.98F),
                chunk("memory-a", 0.42F)));
        ModelMemoryRecallReranker reranker = new ModelMemoryRecallReranker(modelPort, "rerank-model", 5, 100);
        MemoryRecallRequest request = new MemoryRecallRequest(
                "user-1",
                "default",
                "Pulsar PIP-459",
                Set.of(),
                5,
                Map.of());

        List<MemoryRecallCandidate> reranked = reranker.rerank(request, List.of(
                candidate("memory-a", 0.9D, "first memory"),
                candidate("memory-b", 0.8D, "second memory")));

        assertThat(modelPort.modelIds).containsExactly("rerank-model");
        assertThat(modelPort.queries).containsExactly("Pulsar PIP-459");
        assertThat(modelPort.inputs.get(0)).extracting(RetrievedChunk::getId)
                .containsExactly("memory-a", "memory-b");
        assertThat(reranked).extracting(MemoryRecallCandidate::memoryId)
                .containsExactly("memory-b", "memory-a");
        assertThat(reranked.get(0).rawScore()).isCloseTo(0.98D, within(0.0001D));
        assertThat(reranked.get(1).rawScore()).isCloseTo(0.42D, within(0.0001D));
    }

    @Test
    void shouldFallbackToOriginalCandidatesWhenRerankOutputDoesNotMatch() {
        RecordingRerankModelPort modelPort = new RecordingRerankModelPort(List.of(chunk("unknown", 1.0F)));
        ModelMemoryRecallReranker reranker = new ModelMemoryRecallReranker(modelPort, "rerank-model");
        List<MemoryRecallCandidate> candidates = List.of(
                candidate("memory-a", 0.9D, "first memory"),
                candidate("memory-b", 0.8D, "second memory"));

        List<MemoryRecallCandidate> reranked = reranker.rerank(
                new MemoryRecallRequest("user-1", "default", "query", Set.of(), 5, Map.of()),
                candidates);

        assertThat(reranked).containsExactlyElementsOf(candidates);
    }

    private MemoryRecallCandidate candidate(String memoryId, double score, String content) {
        return new MemoryRecallCandidate(
                memoryId,
                "semantic",
                1,
                score,
                "user-1",
                "default",
                "SEMANTIC",
                "PROJECT_FACT",
                content,
                "generation-1",
                "ACTIVE",
                Map.of());
    }

    private static RetrievedChunk chunk(String id, Float score) {
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setId(id);
        chunk.setScore(score);
        chunk.setRerankScore(score);
        return chunk;
    }

    private static final class RecordingRerankModelPort implements RerankModelPort {

        private final List<RetrievedChunk> response;
        private final List<String> modelIds = new ArrayList<>();
        private final List<String> queries = new ArrayList<>();
        private final List<List<RetrievedChunk>> inputs = new ArrayList<>();

        private RecordingRerankModelPort(List<RetrievedChunk> response) {
            this.response = response;
        }

        @Override
        public List<RetrievedChunk> rerank(String modelId, String query, List<RetrievedChunk> chunks) {
            modelIds.add(modelId);
            queries.add(query);
            inputs.add(List.copyOf(chunks));
            return response;
        }
    }
}
