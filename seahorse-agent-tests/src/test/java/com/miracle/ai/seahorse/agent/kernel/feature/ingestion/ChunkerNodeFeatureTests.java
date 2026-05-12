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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerNodeFeatureTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldChunkAndEmbedByDefault() {
        ChunkerNodeFeature feature = new ChunkerNodeFeature((modelId, text) -> List.of(1.0F, 2.0F));
        NodeConfig config = NodeConfig.builder()
                .nodeType("chunker")
                .settings(OBJECT_MAPPER.valueToTree(Map.of("chunkSize", 5, "overlapSize", 1)))
                .build();
        IngestionContext context = IngestionContext.builder().rawText("abcdefghijkl").build();

        NodeResult result = feature.execute(context, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getChunks()).hasSize(3);
        assertThat(context.getChunks()).allSatisfy(chunk ->
                assertThat(chunk.getEmbedding()).containsExactly(1.0F, 2.0F));
    }

    @Test
    void shouldSupportExplicitEmbedderNodeWhenChunkerEmbeddingDisabled() {
        ChunkerNodeFeature chunker = new ChunkerNodeFeature((modelId, text) -> List.of(9.0F));
        EmbedderNodeFeature embedder = new EmbedderNodeFeature((modelId, text) -> List.of(3.0F, 4.0F));
        NodeConfig config = NodeConfig.builder()
                .nodeType("chunker")
                .settings(OBJECT_MAPPER.valueToTree(Map.of("embed", false)))
                .build();
        IngestionContext context = IngestionContext.builder().rawText("hello world").build();

        NodeResult chunkResult = chunker.execute(context, config);
        NodeResult embedResult = embedder.execute(context, NodeConfig.builder().nodeType("embedder").build());

        assertThat(chunkResult.isSuccess()).isTrue();
        assertThat(embedResult.isSuccess()).isTrue();
        assertThat(context.getChunks()).extracting(VectorChunk::getEmbedding)
                .allSatisfy(embedding -> assertThat(embedding).containsExactly(3.0F, 4.0F));
    }
}
