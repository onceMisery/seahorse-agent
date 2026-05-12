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

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionDocumentSource;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FetcherNodeFeatureTests {

    @Test
    void shouldKeepUploadedBytesAndDetectMimeType() {
        FetcherNodeFeature feature = new FetcherNodeFeature();
        IngestionContext context = IngestionContext.builder()
                .rawBytes("hello".getBytes(StandardCharsets.UTF_8))
                .metadata(Map.of("fileName", "guide.txt"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("fetcher").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldFetchContentThroughDocumentFetcherPort() {
        FetcherNodeFeature feature = new FetcherNodeFeature(new TestDocumentFetcherPort());
        IngestionContext context = IngestionContext.builder()
                .source(new IngestionDocumentSource("url", "https://example.test/doc.txt", "doc.txt", Map.of()))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("fetcher").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(new String(context.getRawBytes(), StandardCharsets.UTF_8)).isEqualTo("remote");
        assertThat(context.getMimeType()).isEqualTo("text/plain");
        assertThat(context.getMetadata()).containsEntry("fileName", "doc.txt");
    }

    private static class TestDocumentFetcherPort
            implements com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort {

        @Override
        public boolean supports(String sourceType) {
            return "url".equals(sourceType);
        }

        @Override
        public DocumentFetchResult fetch(
                com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest request) {
            return new DocumentFetchResult(
                    "remote".getBytes(StandardCharsets.UTF_8),
                    "text/plain",
                    request.fileName());
        }
    }
}
