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

import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentKind;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDirectedSearchFeatureTests {

    @Test
    void shouldEnableOnlyWhenKbIntentHasEnoughScoreAndCollection() {
        IntentDirectedSearchFeature feature = new IntentDirectedSearchFeature(request -> List.of(), Runnable::run);

        SearchContext context = SearchContext.builder()
                .intents(List.of(new SubQuestionIntent("question", List.of(
                        score("kb-low", "collection-a", IntentKind.KB, 0.2D, null),
                        score("mcp", "collection-b", IntentKind.MCP, 0.9D, null),
                        score("kb-ok", "collection-c", IntentKind.KB, 0.8D, null)))))
                .build();

        assertThat(feature.enabled(context)).isTrue();
    }

    @Test
    void shouldSearchKbIntentsByScoreOrderAndNodeTopKFirst() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort(false);
        IntentDirectedSearchFeature feature = new IntentDirectedSearchFeature(vectorSearchPort, Runnable::run);

        List<RetrievedChunk> chunks = feature.search(SearchContext.builder()
                        .rewrittenQuestion("如何报销")
                        .topK(3)
                        .intents(List.of(new SubQuestionIntent("报销", List.of(
                                score("intent-a", "collection-a", IntentKind.KB, 0.7D, 4),
                                score("intent-b", "collection-b", IntentKind.KB, 0.9D, null)))))
                        .build())
                .getChunks();

        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::collectionName)
                .containsExactly("collection-b", "collection-a");
        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::topK)
                .containsExactly(6, 8);
        assertThat(chunks).hasSize(2);
    }

    @Test
    void shouldIgnoreSingleIntentFailure() {
        RecordingVectorSearchPort vectorSearchPort = new RecordingVectorSearchPort(true);
        IntentDirectedSearchFeature feature = new IntentDirectedSearchFeature(vectorSearchPort, Runnable::run);

        List<RetrievedChunk> chunks = feature.search(SearchContext.builder()
                        .rewrittenQuestion("查询制度")
                        .topK(2)
                        .intents(List.of(new SubQuestionIntent("制度", List.of(
                                score("fail", "fail-collection", IntentKind.KB, 0.9D, null),
                                score("ok", "ok-collection", IntentKind.KB, 0.8D, null)))))
                        .build())
                .getChunks();

        assertThat(chunks).hasSize(1);
        assertThat(vectorSearchPort.requests)
                .extracting(VectorSearchRequest::collectionName)
                .containsExactly("fail-collection", "ok-collection");
    }

    private IntentScore score(String id, String collectionName, IntentKind kind, double score, Integer topK) {
        return IntentScore.builder()
                .node(IntentNode.builder()
                        .id(id)
                        .name(id)
                        .kind(kind)
                        .collectionName(collectionName)
                        .topK(topK)
                        .build())
                .score(score)
                .build();
    }

    private static class RecordingVectorSearchPort implements VectorSearchPort {

        private final List<VectorSearchRequest> requests = new ArrayList<>();
        private final boolean failFirstCollection;

        RecordingVectorSearchPort(boolean failFirstCollection) {
            this.failFirstCollection = failFirstCollection;
        }

        @Override
        public List<RetrievedChunk> search(VectorSearchRequest request) {
            requests.add(request);
            if (failFirstCollection && "fail-collection".equals(request.collectionName())) {
                throw new IllegalStateException("failed");
            }
            return List.of(RetrievedChunk.builder()
                    .id(request.collectionName() + "-chunk")
                    .text("content")
                    .score(0.9F)
                    .build());
        }
    }
}
