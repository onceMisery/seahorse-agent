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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RerankPostProcessorFeatureTests {

    @Test
    void shouldEnableOnlyWhenOptionsRequestRerankModel() {
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature(RerankModelPort.noop());

        assertThat(feature.enabled(SearchContext.builder().build())).isFalse();
        assertThat(feature.enabled(SearchContext.builder()
                .options(RetrievalOptions.builder()
                        .enableRerank(false)
                        .rerankModel("rerank-a")
                        .build())
                .build())).isFalse();
        assertThat(feature.enabled(SearchContext.builder()
                .options(RetrievalOptions.builder()
                        .enableRerank(true)
                        .build())
                .build())).isFalse();
        assertThat(feature.enabled(SearchContext.builder()
                .options(RetrievalOptions.builder()
                        .enableRerank(true)
                        .rerankModel("rerank-a")
                        .build())
                .build())).isTrue();
    }

    @Test
    void shouldRerankLimitedCandidatesAndWriteScores() {
        RecordingRerankPort port = new RecordingRerankPort(List.of(
                chunk("c2", 0.95F),
                chunk("c1", 0.8F)));
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature(port);
        SearchContext context = SearchContext.builder()
                .originalQuestion("原始问题")
                .rewrittenQuestion("改写问题")
                .options(RetrievalOptions.builder()
                        .enableRerank(true)
                        .rerankModel("rerank-a")
                        .fusionTopK(3)
                        .rerankTopK(2)
                        .build())
                .build();

        List<RetrievedChunk> reranked = feature.process(
                List.of(chunk("c1", 0.1F), chunk("c2", 0.2F), chunk("c3", 0.3F)), List.of(), context);

        assertThat(port.modelId).isEqualTo("rerank-a");
        assertThat(port.query).isEqualTo("改写问题");
        assertThat(port.candidates).extracting(RetrievedChunk::getId).containsExactly("c1", "c2");
        assertThat(reranked).extracting(RetrievedChunk::getId).containsExactly("c2", "c1");
        assertThat(reranked).extracting(RetrievedChunk::getRerankScore).containsExactly(0.95F, 0.8F);
        assertThat(reranked).extracting(RetrievedChunk::getScore).containsExactly(0.95F, 0.8F);
    }

    @Test
    void shouldFallbackToOriginalChunksWhenRerankFails() {
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature((modelId, query, chunks) -> {
            throw new IllegalStateException("rerank failed");
        });
        List<RetrievedChunk> chunks = List.of(chunk("c1", 0.1F), chunk("c2", 0.2F));

        List<RetrievedChunk> reranked = feature.process(chunks, List.of(), enabledContext());

        assertThat(reranked).isSameAs(chunks);
    }

    @Test
    void shouldFallbackToOriginalChunksWhenRerankReturnsEmpty() {
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature((modelId, query, chunks) -> List.of());
        List<RetrievedChunk> chunks = List.of(chunk("c1", 0.1F), chunk("c2", 0.2F));

        List<RetrievedChunk> reranked = feature.process(chunks, List.of(), enabledContext());

        assertThat(reranked).isSameAs(chunks);
    }

    private SearchContext enabledContext() {
        return SearchContext.builder()
                .originalQuestion("问题")
                .options(RetrievalOptions.builder()
                        .enableRerank(true)
                        .rerankModel("rerank-a")
                        .rerankTopK(2)
                        .build())
                .build();
    }

    private static RetrievedChunk chunk(String id, Float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text("chunk-" + id)
                .score(score)
                .build();
    }

    private static class RecordingRerankPort implements RerankModelPort {

        private final List<RetrievedChunk> response;
        private String modelId;
        private String query;
        private List<RetrievedChunk> candidates;

        private RecordingRerankPort(List<RetrievedChunk> response) {
            this.response = response;
        }

        @Override
        public List<RetrievedChunk> rerank(String modelId, String query, List<RetrievedChunk> chunks) {
            this.modelId = modelId;
            this.query = query;
            this.candidates = chunks;
            return response;
        }
    }
}
