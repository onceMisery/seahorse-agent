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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class KernelRetrievalEvaluationServiceTests {

    @Test
    void shouldComputePrecisionAtKAndTrackNegativeChunkHits() {
        FixedRetrievalEngine retrievalEngine = new FixedRetrievalEngine(Map.of(
                "question-a", List.of(
                        chunk("expected", "doc-hit", "kb-1"),
                        chunk("negative", "doc-noise", "kb-1"),
                        chunk("neutral", "doc-neutral", "kb-1"))));
        KernelRetrievalEvaluationService service = new KernelRetrievalEvaluationService(retrievalEngine);

        RetrievalEvaluationReport report = service.evaluate(new RetrievalEvaluationCommand(
                "baseline",
                3,
                RetrievalOptions.defaults(3),
                List.of(new RetrievalEvaluationCase("case-1", "question-a",
                        List.of(), List.of(), List.of("expected"), null, null,
                        List.of("negative"), List.of("smoke", "release-gate"), 1D, 0.3D))));

        assertClose(1D / 3D, report.precisionAtK());
        assertEquals(1, report.cases().size());
        assertClose(1D / 3D, report.cases().get(0).precisionAtK());
        assertEquals(1, report.cases().get(0).negativeHitCount());
        assertIterableEquals(List.of("negative"), report.cases().get(0).negativeHitChunkIds());
    }

    @Test
    void shouldCompareRetrievalStrategyPrecisionDelta() {
        FixedRetrievalEngine retrievalEngine = new FixedRetrievalEngine(
                Map.of("question-a", List.of(chunk("miss", "doc-miss", "kb-1"))),
                Map.of("question-a", List.of(chunk("hit", "doc-hit", "kb-1"))));
        KernelRetrievalEvaluationService service = new KernelRetrievalEvaluationService(retrievalEngine);

        RetrievalEvaluationComparisonReport report = service.compare(new RetrievalEvaluationComparisonCommand(
                "baseline",
                1,
                List.of(
                        new RetrievalEvaluationStrategy("baseline", 1, RetrievalOptions.defaults(1)),
                        new RetrievalEvaluationStrategy("keyword", 1, RetrievalOptions.builder()
                                .finalTopK(1)
                                .enableKeyword(true)
                                .build())),
                List.of(new RetrievalEvaluationCase("case-1", "question-a",
                        List.of(), List.of("doc-hit"), List.of(), null, null))));

        assertEquals("keyword", report.winnerStrategyName());
        assertEquals(2, report.deltas().size());
        assertClose(0D, report.deltas().get(0).precisionAtKDelta());
        assertClose(1D, report.deltas().get(1).precisionAtKDelta());
    }

    private static RetrievedChunk chunk(String chunkId, String docId, String kbId) {
        return RetrievedChunk.builder()
                .id(chunkId)
                .docId(docId)
                .kbId(kbId)
                .text("text-" + chunkId)
                .score(1F)
                .build();
    }

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, 0.0001D);
    }

    private static final class FixedRetrievalEngine extends KernelRetrievalEngine {

        private final Map<String, List<RetrievedChunk>> chunksByQuestion;
        private final Map<String, List<RetrievedChunk>> keywordChunksByQuestion;

        private FixedRetrievalEngine(Map<String, List<RetrievedChunk>> chunksByQuestion) {
            this(chunksByQuestion, Map.of());
        }

        private FixedRetrievalEngine(Map<String, List<RetrievedChunk>> chunksByQuestion,
                                     Map<String, List<RetrievedChunk>> keywordChunksByQuestion) {
            super(new KernelMultiChannelRetrievalEngine(
                    new DefaultExtensionRegistry(), Runnable::run, FeatureActivationContext.empty()));
            this.chunksByQuestion = chunksByQuestion;
            this.keywordChunksByQuestion = keywordChunksByQuestion;
        }

        @Override
        public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                              int topK,
                                                              RetrievalFilter filter,
                                                              RetrievalOptions options) {
            String question = subIntents == null || subIntents.isEmpty() ? "" : subIntents.get(0).subQuestion();
            Map<String, List<RetrievedChunk>> source = options != null && options.enableKeyword()
                    ? keywordChunksByQuestion
                    : chunksByQuestion;
            return source.getOrDefault(question, List.of()).stream()
                    .limit(topK)
                    .toList();
        }
    }
}
