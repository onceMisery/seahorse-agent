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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import org.junit.jupiter.api.Test;

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
}
