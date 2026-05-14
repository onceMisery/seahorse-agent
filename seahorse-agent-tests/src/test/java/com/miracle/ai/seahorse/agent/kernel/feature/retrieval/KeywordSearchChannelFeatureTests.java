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
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordSearchChannelFeatureTests {

    @Test
    void shouldBeDisabledByDefault() {
        KeywordSearchChannelFeature feature = new KeywordSearchChannelFeature(KeywordSearchPort.noop());

        boolean enabled = feature.enabled(SearchContext.builder()
                .rewrittenQuestion("入职流程")
                .topK(5)
                .build());

        assertThat(enabled).isFalse();
    }

    @Test
    void shouldCallKeywordSearchPortWhenEnabled() {
        RecordingKeywordSearchPort port = new RecordingKeywordSearchPort();
        KeywordSearchChannelFeature feature = new KeywordSearchChannelFeature(port);

        SearchChannelResult result = feature.search(SearchContext.builder()
                .rewrittenQuestion("入职流程")
                .topK(5)
                .options(RetrievalOptions.builder()
                        .enableKeyword(true)
                        .keywordTopK(7)
                        .build())
                .build());

        assertThat(port.request).isNotNull();
        assertThat(port.request.query()).isEqualTo("入职流程");
        assertThat(port.request.topK()).isEqualTo(7);
        assertThat(result.getChannelType()).isEqualTo(SearchChannelType.KEYWORD_BM25);
        assertThat(result.getChunks()).extracting(RetrievedChunk::getId).containsExactly("chunk-1");
    }

    private static class RecordingKeywordSearchPort implements KeywordSearchPort {

        private KeywordSearchRequest request;

        @Override
        public List<RetrievedChunk> search(KeywordSearchRequest request) {
            this.request = request;
            return List.of(RetrievedChunk.builder()
                    .id("chunk-1")
                    .text("入职流程说明")
                    .score(0.8F)
                    .build());
        }
    }
}
