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
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinalTruncatePostProcessorFeatureTests {

    @Test
    void shouldTruncateAndRecordFinalObservation() {
        RecordingObservationPort observation = new RecordingObservationPort();
        FinalTruncatePostProcessorFeature feature = new FinalTruncatePostProcessorFeature(observation);
        SearchContext context = SearchContext.builder()
                .filter(RetrievalFilter.builder()
                        .system(SystemRetrievalFilter.builder().tenantId("tenant-1").build())
                        .build())
                .options(RetrievalOptions.builder().finalTopK(2).build())
                .build();

        List<RetrievedChunk> output = feature.process(List.of(
                chunk("a"),
                chunk("b"),
                chunk("c")), List.of(), context);

        assertThat(output).extracting(RetrievedChunk::getId).containsExactly("a", "b");
        assertThat(observation.events).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("retrieval.final");
            assertThat(event.attributes()).containsEntry("tenantId", "tenant-1");
            assertThat(event.attributes()).containsEntry("inputCount", "3");
            assertThat(event.attributes()).containsEntry("outputCount", "2");
            assertThat(event.attributes()).containsEntry("finalTopK", "2");
            assertThat(event.attributes()).containsEntry("truncated", "true");
        });
    }

    @Test
    void shouldRecordFinalObservationWithoutTenant() {
        RecordingObservationPort observation = new RecordingObservationPort();
        FinalTruncatePostProcessorFeature feature = new FinalTruncatePostProcessorFeature(observation);
        SearchContext context = SearchContext.builder()
                .options(RetrievalOptions.builder().finalTopK(3).build())
                .build();

        List<RetrievedChunk> output = feature.process(List.of(chunk("a")), List.of(), context);

        assertThat(output).extracting(RetrievedChunk::getId).containsExactly("a");
        assertThat(observation.events).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("retrieval.final");
            assertThat(event.attributes()).containsEntry("tenantId", "");
            assertThat(event.attributes()).containsEntry("knowledgeBaseId", "");
            assertThat(event.attributes()).containsEntry("inputCount", "1");
            assertThat(event.attributes()).containsEntry("outputCount", "1");
            assertThat(event.attributes()).containsEntry("finalTopK", "3");
            assertThat(event.attributes()).containsEntry("truncated", "false");
        });
    }

    private RetrievedChunk chunk(String id) {
        return RetrievedChunk.builder()
                .id(id)
                .text("chunk-" + id)
                .score(1F)
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
