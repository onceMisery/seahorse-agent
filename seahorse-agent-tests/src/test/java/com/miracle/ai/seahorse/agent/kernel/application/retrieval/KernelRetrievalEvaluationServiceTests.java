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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KernelRetrievalEvaluationServiceTests {

    @Test
    void shouldComputeOfflineRetrievalMetrics() {
        FixedRetrievalEngine retrievalEngine = new FixedRetrievalEngine(Map.of(
                "question-a", List.of(
                        chunk("c-2", "doc-2", "kb-1"),
                        chunk("c-1", "doc-1", "kb-1"),
                        chunk("c-3", "doc-3", "kb-1")),
                "question-empty", List.of()));
        KernelRetrievalEvaluationService service = new KernelRetrievalEvaluationService(retrievalEngine);

        RetrievalEvaluationReport report = service.evaluate(new RetrievalEvaluationCommand(
                "baseline",
                2,
                RetrievalOptions.defaults(2),
                List.of(
                        new RetrievalEvaluationCase("case-1", "question-a",
                                List.of(), List.of(), List.of("c-1"), null, null),
                        new RetrievalEvaluationCase("case-2", "question-empty",
                                List.of(), List.of("doc-miss"), List.of(), null, null))));

        assertThat(report.strategyName()).isEqualTo("baseline");
        assertThat(report.topK()).isEqualTo(2);
        assertThat(report.caseCount()).isEqualTo(2);
        assertThat(report.evaluableCaseCount()).isEqualTo(2);
        assertThat(report.recallAtK()).isCloseTo(0.5D, within(0.0001D));
        assertThat(report.mrr()).isCloseTo(0.25D, within(0.0001D));
        assertThat(report.ndcgAtK()).isCloseTo(0.3154D, within(0.0001D));
        assertThat(report.emptyRecallRate()).isCloseTo(0.5D, within(0.0001D));
        assertThat(report.averageLatencyMs()).isGreaterThanOrEqualTo(0D);
        assertThat(report.p95LatencyMs()).isGreaterThanOrEqualTo(0D);
        assertThat(report.cases()).hasSize(2);
        assertThat(report.cases().get(0).reciprocalRank()).isCloseTo(0.5D, within(0.0001D));
        assertThat(report.cases().get(0).retrievedChunkIds()).containsExactly("c-2", "c-1");
        assertThat(report.cases().get(1).status()).isEqualTo("EMPTY");
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

    private static final class FixedRetrievalEngine extends KernelRetrievalEngine {

        private final Map<String, List<RetrievedChunk>> chunksByQuestion;
        private final Map<String, Integer> topKByQuestion = new HashMap<>();

        private FixedRetrievalEngine(Map<String, List<RetrievedChunk>> chunksByQuestion) {
            super(new KernelMultiChannelRetrievalEngine(
                    new DefaultExtensionRegistry(), Runnable::run, FeatureActivationContext.empty()));
            this.chunksByQuestion = chunksByQuestion;
        }

        @Override
        public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                              int topK,
                                                              RetrievalFilter filter,
                                                              RetrievalOptions options) {
            String question = subIntents == null || subIntents.isEmpty() ? "" : subIntents.get(0).subQuestion();
            topKByQuestion.put(question, topK);
            return chunksByQuestion.getOrDefault(question, List.of()).stream()
                    .limit(topK)
                    .toList();
        }
    }
}
