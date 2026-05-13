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
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                result("KeywordSearch", SearchChannelType.KEYWORD_ES,
                        List.of(chunk("b", 0.7F), chunk("c", 0.6F))));

        List<RetrievedChunk> fused = rrf.process(List.of(), results, context);
        List<RetrievedChunk> finalChunks = truncate.process(fused, results, context);

        assertThat(fused).extracting(RetrievedChunk::getId).containsExactly("b", "a", "c");
        assertThat(fused.get(0).getChannelRanks()).containsEntry(IntentDirectedSearchFeature.NAME, 2);
        assertThat(fused.get(0).getChannelRanks()).containsEntry("KeywordSearch", 1);
        assertThat(finalChunks).extracting(RetrievedChunk::getId).containsExactly("b", "a");
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
}
