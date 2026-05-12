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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EnricherNodeFeatureTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldEnrichChunksAndAttachDocumentMetadata() {
        AtomicInteger calls = new AtomicInteger();
        EnricherNodeFeature feature = new EnricherNodeFeature((request, modelId) -> switch (calls.getAndIncrement()) {
            case 0 -> "[\"段落\", \"规则\"]";
            case 1 -> "摘要内容";
            default -> "";
        }, taskType -> "system:" + taskType);
        NodeConfig config = NodeConfig.builder()
                .nodeType("enricher")
                .settings(OBJECT_MAPPER.valueToTree(Map.of(
                        "tasks", List.of(
                                Map.of("type", "keywords"),
                                Map.of("type", "summary")))))
                .build();
        VectorChunk chunk = VectorChunk.builder().chunkId("c1").index(0).content("片段内容").build();
        IngestionContext context = IngestionContext.builder()
                .chunks(List.of(chunk))
                .metadata(Map.of("docType", "policy"))
                .build();

        NodeResult result = feature.execute(context, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(chunk.getMetadata()).containsEntry("docType", "policy");
        assertThat(chunk.getMetadata()).containsEntry("summary", "摘要内容");
        assertThat(chunk.getMetadata().get("keywords")).isEqualTo(List.of("段落", "规则"));
    }
}
