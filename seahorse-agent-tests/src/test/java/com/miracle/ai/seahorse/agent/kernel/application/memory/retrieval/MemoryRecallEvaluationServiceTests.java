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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenCase;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryContextAttribution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class MemoryRecallEvaluationServiceTests {

    @Test
    void shouldEvaluateGoldenRecallCasesAcrossMemoryZones() {
        MemoryRecallEvaluationService service = new MemoryRecallEvaluationService(new StaticPipeline(Map.of(
                "Pulsar PIP-459", context(List.of(
                        item("mem-pip", MemoryLayer.SEMANTIC, 0.92D),
                        item("mem-java", MemoryLayer.LONG_TERM, 0.74D),
                        item("mem-noise", MemoryLayer.SHORT_TERM, 0.15D))),
                "K8s alias", context(List.of(
                        item("mem-kubernetes", MemoryLayer.SEMANTIC, 0.88D),
                        item("mem-platform", MemoryLayer.LONG_TERM, 0.64D))))));

        MemoryRecallEvaluationReport report = service.evaluate(List.of(
                new MemoryRecallGoldenCase(
                        "case-keyword",
                        "user-1",
                        "conv-1",
                        "Pulsar PIP-459",
                        List.of("mem-pip", "mem-java")),
                new MemoryRecallGoldenCase(
                        "case-alias",
                        "user-1",
                        "conv-1",
                        "K8s alias",
                        List.of("mem-kubernetes", "missing-graph"))),
                3);

        assertThat(report.caseCount()).isEqualTo(2);
        assertThat(report.hitCount()).isEqualTo(2);
        assertThat(report.hitRate()).isEqualTo(1.0D);
        assertThat(report.meanReciprocalRank()).isEqualTo(1.0D);
        assertThat(report.averageRecall()).isEqualTo(0.75D);
        assertThat(report.averagePrecision()).isCloseTo(7.0D / 12.0D, offset(1.0E-15));
        assertThat(report.averageNoiseRate()).isCloseTo(5.0D / 12.0D, offset(1.0E-15));
        assertThat(report.results()).extracting(MemoryRecallEvaluationResult::caseId)
                .containsExactly("case-keyword", "case-alias");
        assertThat(report.results().get(0).retrievedMemoryIds())
                .containsExactly("mem-pip", "mem-java", "mem-noise");
        assertThat(report.results().get(0).missingExpectedMemoryIds()).isEmpty();
        assertThat(report.results().get(0).precision()).isEqualTo(2.0D / 3.0D);
        assertThat(report.results().get(0).noiseRate()).isEqualTo(1.0D / 3.0D);
        assertThat(report.results().get(1).missingExpectedMemoryIds()).containsExactly("missing-graph");
        assertThat(report.results().get(1).precision()).isEqualTo(0.5D);
        assertThat(report.results().get(1).noiseRate()).isEqualTo(0.5D);
    }

    @Test
    void shouldTreatEmptyExpectedIdsAsNonScoredCase() {
        MemoryRecallEvaluationService service = new MemoryRecallEvaluationService(
                new StaticPipeline(Map.of(
                        "empty", context(List.of(item("mem-1", MemoryLayer.SEMANTIC, 0.5D))))));

        MemoryRecallEvaluationReport report = service.evaluate(List.of(
                new MemoryRecallGoldenCase("case-empty", "user-1", "conv-1", "empty", List.of())),
                5);

        assertThat(report.caseCount()).isEqualTo(1);
        assertThat(report.scoredCaseCount()).isZero();
        assertThat(report.hitRate()).isZero();
        assertThat(report.meanReciprocalRank()).isZero();
        assertThat(report.averageRecall()).isZero();
        assertThat(report.averagePrecision()).isZero();
        assertThat(report.averageNoiseRate()).isZero();
        assertThat(report.results().get(0).hit()).isFalse();
        assertThat(report.results().get(0).precision()).isZero();
        assertThat(report.results().get(0).noiseRate()).isZero();
    }

    @Test
    void shouldEmitEvaluationObservationTaggedWithOutcome() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        MemoryRecallEvaluationService service = new MemoryRecallEvaluationService(
                new StaticPipeline(Map.of("scored", context(List.of(item("mem-1", MemoryLayer.SEMANTIC, 0.9D))))),
                observationPort);

        service.evaluate(List.of(
                new MemoryRecallGoldenCase("case-scored", "user-1", "conv-1", "scored", List.of("mem-1"))),
                3);

        assertThat(observationPort.events).hasSize(1);
        ObservationEvent event = observationPort.events.get(0);
        assertThat(event.name()).isEqualTo(MemoryRecallEvaluationService.OBSERVATION_EVALUATE_EVENT);
        assertThat(event.attributes())
                .containsEntry(MemoryRecallEvaluationService.OBSERVATION_ATTR_OUTCOME,
                        MemoryRecallEvaluationService.OBSERVATION_OUTCOME_SUCCESS);
        assertThat(event.amount()).isEqualTo(ObservationEvent.DEFAULT_AMOUNT);
    }

    @Test
    void shouldEmitEmptyOutcomeObservationWhenNoCasesAreScored() {
        RecordingObservationPort observationPort = new RecordingObservationPort();
        MemoryRecallEvaluationService service = new MemoryRecallEvaluationService(
                new StaticPipeline(Map.of("empty", context(List.of()))),
                observationPort);

        service.evaluate(List.of(
                new MemoryRecallGoldenCase("case-empty", "user-1", "conv-1", "empty", List.of())),
                3);

        assertThat(observationPort.events).singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry(MemoryRecallEvaluationService.OBSERVATION_ATTR_OUTCOME,
                                MemoryRecallEvaluationService.OBSERVATION_OUTCOME_EMPTY));
    }

    @Test
    void shouldReportPerChannelHitCountsWhenPipelineExposesAttribution() {
        Map<String, MemoryContext> contexts = Map.of(
                "alpha", context(List.of(item("mem-a", MemoryLayer.SEMANTIC, 0.9D),
                        item("mem-b", MemoryLayer.SEMANTIC, 0.8D))));
        Map<String, Map<String, List<String>>> attributionByQuery = Map.of(
                "alpha", Map.of(
                        "vector", List.of("mem-a", "mem-b"),
                        "keyword", List.of("mem-a", "mem-noise")));
        MemoryRecallEvaluationService service = new MemoryRecallEvaluationService(
                new AttributingStaticPipeline(contexts, attributionByQuery));

        MemoryRecallEvaluationReport report = service.evaluate(List.of(
                new MemoryRecallGoldenCase("case-channels", "user-1", "conv-1", "alpha",
                        List.of("mem-a", "mem-b"))),
                5);

        assertThat(report.channelHitCounts())
                .containsEntry("vector", 2)
                .containsEntry("keyword", 1);
        assertThat(report.results()).singleElement()
                .extracting(MemoryRecallEvaluationResult::channelHitCounts)
                .satisfies(channels -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> typed = (Map<String, Integer>) channels;
                    assertThat(typed)
                            .containsEntry("vector", 2)
                            .containsEntry("keyword", 1);
                });
    }

    @Test
    void shouldReportEmptyChannelHitCountsWhenPipelineLacksAttribution() {
        MemoryRecallEvaluationService service = new MemoryRecallEvaluationService(
                new StaticPipeline(Map.of("plain", context(List.of(
                        item("mem-1", MemoryLayer.SEMANTIC, 0.9D))))));

        MemoryRecallEvaluationReport report = service.evaluate(List.of(
                new MemoryRecallGoldenCase("case-plain", "user-1", "conv-1", "plain",
                        List.of("mem-1"))),
                3);

        assertThat(report.channelHitCounts()).isEmpty();
        assertThat(report.results()).singleElement()
                .extracting(MemoryRecallEvaluationResult::channelHitCounts)
                .satisfies(channels -> assertThat((Map<?, ?>) channels).isEmpty());
    }

    private static MemoryContext context(List<MemoryItem> items) {
        return MemoryContext.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .shortTermMemories(items.stream()
                        .filter(item -> item.getLayer() == MemoryLayer.SHORT_TERM)
                        .toList())
                .longTermMemories(items.stream()
                        .filter(item -> item.getLayer() == MemoryLayer.LONG_TERM)
                        .toList())
                .semanticMemories(items.stream()
                        .filter(item -> item.getLayer() == MemoryLayer.SEMANTIC)
                        .toList())
                .profileMemories(List.of())
                .correctionMemories(List.of())
                .businessDocumentMemories(List.of())
                .workingMemory(List.of())
                .promptMessages(List.of())
                .build();
    }

    private static MemoryItem item(String id, MemoryLayer layer, double relevanceScore) {
        return MemoryItem.builder()
                .id(id)
                .userId("user-1")
                .layer(layer)
                .content("content-" + id)
                .relevanceScore(relevanceScore)
                .build();
    }

    private record StaticPipeline(Map<String, MemoryContext> contexts) implements MemoryRetrievalPipelinePort {

        @Override
        public MemoryContext load(MemoryLoadRequest request) {
            return contexts.getOrDefault(
                    Objects.requireNonNullElse(request.currentQuestion(), ""),
                    MemoryContext.builder()
                            .userId(request.userId())
                            .conversationId(request.conversationId())
                            .shortTermMemories(List.of())
                            .longTermMemories(List.of())
                            .semanticMemories(List.of())
                            .profileMemories(List.of())
                            .correctionMemories(List.of())
                            .businessDocumentMemories(List.of())
                            .workingMemory(List.of())
                            .promptMessages(List.of())
                            .build());
        }
    }

    private static final class AttributingStaticPipeline implements MemoryRetrievalPipelinePort {

        private final Map<String, MemoryContext> contexts;
        private final Map<String, Map<String, List<String>>> attributionByQuery;

        AttributingStaticPipeline(Map<String, MemoryContext> contexts,
                                  Map<String, Map<String, List<String>>> attributionByQuery) {
            this.contexts = Map.copyOf(contexts);
            Map<String, Map<String, List<String>>> frozen = new LinkedHashMap<>();
            attributionByQuery.forEach((query, channels) -> frozen.put(query, Map.copyOf(channels)));
            this.attributionByQuery = Map.copyOf(frozen);
        }

        @Override
        public MemoryContext load(MemoryLoadRequest request) {
            return contexts.getOrDefault(
                    Objects.requireNonNullElse(request.currentQuestion(), ""),
                    MemoryContext.builder()
                            .userId(request.userId())
                            .conversationId(request.conversationId())
                            .shortTermMemories(List.of())
                            .longTermMemories(List.of())
                            .semanticMemories(List.of())
                            .profileMemories(List.of())
                            .correctionMemories(List.of())
                            .businessDocumentMemories(List.of())
                            .workingMemory(List.of())
                            .promptMessages(List.of())
                            .build());
        }

        @Override
        public MemoryContextAttribution loadWithAttribution(MemoryLoadRequest request) {
            Map<String, List<String>> attribution = attributionByQuery.getOrDefault(
                    Objects.requireNonNullElse(request.currentQuestion(), ""), Map.of());
            return new MemoryContextAttribution(load(request), attribution);
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
