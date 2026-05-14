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

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    void shouldPreserveFusionExplanationAfterRerank() {
        RetrievedChunk original = chunk("c1", 0.1F);
        original.getFusionExplanation().putAll(Map.of(
                "strategy", "RRF",
                "rrfK", 60));
        RecordingRerankPort port = new RecordingRerankPort(List.of(chunk("c1", 0.95F)));
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature(port);

        List<RetrievedChunk> reranked = feature.process(List.of(original), List.of(), enabledContext());

        assertThat(reranked).hasSize(1);
        assertThat(reranked.get(0).getRerankScore()).isEqualTo(0.95F);
        assertThat(reranked.get(0).getFusionExplanation())
                .containsEntry("strategy", "RRF")
                .containsEntry("rrfK", 60);
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

    @Test
    void shouldTimeoutRerankAndRecordObservation() {
        RecordingObservationPort observation = new RecordingObservationPort();
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature((modelId, query, chunks) -> {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return List.of(chunk("c2", 0.95F));
        }, observation);
        List<RetrievedChunk> chunks = List.of(chunk("c1", 0.1F), chunk("c2", 0.2F));
        SearchContext context = SearchContext.builder()
                .filter(RetrievalFilter.builder()
                        .system(SystemRetrievalFilter.builder().tenantId("tenant-1").build())
                        .build())
                .originalQuestion("问题")
                .options(RetrievalOptions.builder()
                        .enableRerank(true)
                        .rerankModel("rerank-a")
                        .rerankTopK(2)
                        .rerankTimeout(Duration.ofMillis(20L))
                        .build())
                .build();

        List<RetrievedChunk> reranked = feature.process(chunks, List.of(), context);

        assertThat(reranked).isSameAs(chunks);
        assertThat(observation.events).anySatisfy(event -> {
            assertThat(event.name()).isEqualTo("retrieval.rerank");
            assertThat(event.attributes()).containsEntry("tenant", "tenant-1");
            assertThat(event.attributes()).containsEntry("status", "timeout");
            assertThat(event.attributes()).containsEntry("timeoutMs", "20");
        });
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

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}
