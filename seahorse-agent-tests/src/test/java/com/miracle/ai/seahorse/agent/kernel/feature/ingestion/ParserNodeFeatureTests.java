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
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParseResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParserNodeFeatureTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldParseRawBytesThroughParserPort() {
        ParserNodeFeature feature = new ParserNodeFeature((content, mimeType, fileName, options) ->
                new DocumentParseResult("parsed:" + fileName, Map.of("mimeType", mimeType)));
        IngestionContext context = IngestionContext.builder()
                .rawBytes("hello".getBytes(StandardCharsets.UTF_8))
                .mimeType("text/plain")
                .metadata(Map.of("fileName", "demo.txt"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("parser").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getRawText()).isEqualTo("parsed:demo.txt");
        assertThat(context.getMetadata()).containsKey("parseMetadata");
    }

    @Test
    void shouldRejectUnsupportedTypeWhenRulesDoNotMatch() {
        ParserNodeFeature feature = new ParserNodeFeature((content, mimeType, fileName, options) ->
                DocumentParseResult.ofText("parsed"));
        NodeConfig config = NodeConfig.builder()
                .nodeType("parser")
                .settings(OBJECT_MAPPER.valueToTree(Map.of(
                        "rules", java.util.List.of(Map.of("mimeType", "PDF")))))
                .build();
        IngestionContext context = IngestionContext.builder()
                .rawBytes("hello".getBytes(StandardCharsets.UTF_8))
                .mimeType("text/plain")
                .metadata(Map.of("fileName", "demo.txt"))
                .build();

        NodeResult result = feature.execute(context, config);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("unsupported document type");
    }
}
