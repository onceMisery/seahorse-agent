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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionPostProcessorFeatureTests {

    @Test
    void shouldFuseDuplicatedChunksByChannelRank() {
        RrfFusionPostProcessorFeature rrf = new RrfFusionPostProcessorFeature();
        FinalTruncatePostProcessorFeature truncate = new FinalTruncatePostProcessorFeature();
        SearchContext context = SearchContext.builder()
                .options(RetrievalOptions.builder()
                        .enableRrf(true)
                        .fusionTopK(3)
                        .finalTopK(2)
                        .build())
                .build();
        List<SearchChannelResult> results = List.of(
                result(IntentDirectedSearchFeature.NAME, SearchChannelType.INTENT_DIRECTED,
                        List.of(chunk("a", 0.9F), chunk("b", 0.8F))),
                result("KeywordSearch", SearchChannelType.KEYWORD_BM25,
                        List.of(chunk("b", 0.7F), chunk("c", 0.6F))));

        List<RetrievedChunk> fused = rrf.process(List.of(), results, context);
        List<RetrievedChunk> finalChunks = truncate.process(fused, results, context);

        assertThat(fused).extracting(RetrievedChunk::getId).containsExactly("b", "a", "c");
        assertThat(fused.get(0).getChannelRanks()).containsEntry(IntentDirectedSearchFeature.NAME, 2);
        assertThat(fused.get(0).getChannelRanks()).containsEntry("KeywordSearch", 1);
        Map<String, Object> fusionExplanation = fused.get(0).getFusionExplanation();
        assertThat(fusionExplanation)
                .containsEntry("strategy", "RRF")
                .containsEntry("rrfK", 60)
                .containsEntry("fusionScore", fused.get(0).getFusionScore());
        @SuppressWarnings("unchecked")
        Map<String, Object> explainedRanks = (Map<String, Object>) fusionExplanation.get("channelRanks");
        assertThat(explainedRanks)
                .containsEntry(IntentDirectedSearchFeature.NAME, 2)
                .containsEntry("KeywordSearch", 1);
        @SuppressWarnings("unchecked")
        Map<String, Float> explainedContributions = (Map<String, Float>) fusionExplanation.get("channelContributions");
        assertThat(explainedContributions)
                .containsKeys(IntentDirectedSearchFeature.NAME, "KeywordSearch");
        assertThat(finalChunks).extracting(RetrievedChunk::getId).containsExactly("b", "a");
    }

    @Test
    void shouldUseConfiguredRrfWeightsAndRecordObservation() {
        RecordingObservationPort observation = new RecordingObservationPort();
        RrfFusionPostProcessorFeature rrf = new RrfFusionPostProcessorFeature(observation);
        SearchContext context = SearchContext.builder()
                .filter(RetrievalFilter.builder()
                        .system(SystemRetrievalFilter.builder().tenantId("tenant-1").build())
                        .build())
                .options(RetrievalOptions.builder()
                        .enableRrf(true)
                        .fusionTopK(2)
                        .channelSettings(Map.of(
                                "rrfK", 1,
                                "channelWeights", Map.of("KeywordSearch", 10.0D)))
                        .build())
                .build();
        List<SearchChannelResult> results = List.of(
                result(IntentDirectedSearchFeature.NAME, SearchChannelType.INTENT_DIRECTED,
                        List.of(chunk("a", 0.9F))),
                result("KeywordSearch", SearchChannelType.KEYWORD_BM25,
                        List.of(chunk("b", 0.7F))));

        List<RetrievedChunk> fused = rrf.process(List.of(), results, context);

        assertThat(fused).extracting(RetrievedChunk::getId).containsExactly("b", "a");
        assertThat(observation.events).anySatisfy(event -> {
            assertThat(event.name()).isEqualTo("retrieval.rrf");
            assertThat(event.attributes()).containsEntry("tenant", "tenant-1");
            assertThat(event.attributes()).containsEntry("status", "success");
            assertThat(event.attributes()).containsEntry("fusionTopK", "2");
            assertThat(event.attributes()).containsEntry("rrfK", "1");
            assertThat(event.attributes()).containsEntry("channelWeights", "KeywordSearch=10.0");
        });
    }

    private SearchChannelResult result(String name, SearchChannelType type, List<RetrievedChunk> chunks) {
        return SearchChannelResult.builder()
                .channelName(name)
                .channelType(type)
                .chunks(chunks)
                .build();
    }

    private RetrievedChunk chunk(String id, Float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text("chunk-" + id)
                .score(score)
                .build();
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
