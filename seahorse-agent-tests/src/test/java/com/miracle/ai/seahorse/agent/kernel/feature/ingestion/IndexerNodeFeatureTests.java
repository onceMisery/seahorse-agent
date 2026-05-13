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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndexerNodeFeatureTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldWriteChunksAndVectorsThroughPorts() {
        RecordingPorts ports = new RecordingPorts();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports);
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .chunks(List.of(chunk("chunk-1", 0), chunk("chunk-2", 1)))
                .metadata(Map.of("kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(ports.collectionNames).containsExactly("collection-a");
        assertThat(ports.repositoryWrites).containsExactly("kb-1/doc-1/2");
        assertThat(ports.vectorWrites).containsExactly("collection-a/doc-1/2");
    }

    @Test
    void shouldWriteKeywordIndexThroughPort() {
        RecordingPorts ports = new RecordingPorts();
        RecordingKeywordIndexPort keywordIndexPort = new RecordingKeywordIndexPort();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports, keywordIndexPort);
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .chunks(List.of(chunk("chunk-1", 0), chunk("chunk-2", 1)))
                .metadata(Map.of("kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(keywordIndexPort.keywordWrites).containsExactly("kb-1/doc-1/2");
    }

    @Test
    void shouldValidateOnlyWhenSkipIndexerWriteEnabled() {
        RecordingPorts ports = new RecordingPorts();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports);
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .skipIndexerWrite(true)
                .chunks(List.of(chunk("chunk-1", 0)))
                .metadata(Map.of("kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(ports.collectionNames).containsExactly("collection-a");
        assertThat(ports.repositoryWrites).isEmpty();
        assertThat(ports.vectorWrites).isEmpty();
    }

    @Test
    void shouldPreferSettingsOverContextMetadata() {
        RecordingPorts ports = new RecordingPorts();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports);
        NodeConfig config = NodeConfig.builder()
                .nodeType("indexer")
                .settings(OBJECT_MAPPER.valueToTree(Map.of(
                        "collectionName", "collection-settings",
                        "kbId", "kb-settings",
                        "docId", "doc-settings")))
                .build();
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .chunks(List.of(chunk("chunk-1", 0)))
                .metadata(Map.of("kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, config);

        assertThat(result.isSuccess()).isTrue();
        assertThat(ports.repositoryWrites).containsExactly("kb-settings/doc-settings/1");
        assertThat(ports.vectorWrites).containsExactly("collection-settings/doc-settings/1");
    }

    @Test
    void shouldFailWhenEmbeddingMissing() {
        RecordingPorts ports = new RecordingPorts();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports);
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .chunks(List.of(VectorChunk.builder().chunkId("chunk-1").index(0).content("text").build()))
                .metadata(Map.of("kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(ports.repositoryWrites).isEmpty();
        assertThat(ports.vectorWrites).isEmpty();
    }

    private VectorChunk chunk(String chunkId, int index) {
        return VectorChunk.builder()
                .chunkId(chunkId)
                .index(index)
                .content("content-" + index)
                .embedding(new float[]{1.0F, 2.0F})
                .build();
    }

    private static class RecordingPorts implements com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort,
            com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort,
            com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort {

        private final List<String> collectionNames = new ArrayList<>();
        private final List<String> repositoryWrites = new ArrayList<>();
        private final List<String> vectorWrites = new ArrayList<>();

        @Override
        public boolean collectionExists(String collectionName) {
            return collectionNames.contains(collectionName);
        }

        @Override
        public void ensureCollection(String collectionName) {
            collectionNames.add(collectionName);
        }

        @Override
        public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
            vectorWrites.add(collectionName + "/" + docId + "/" + chunks.size());
        }

        @Override
        public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void deleteDocumentVectors(String collectionName, String docId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void deleteChunkById(String collectionName, String chunkId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void replaceDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            repositoryWrites.add(kbId + "/" + docId + "/" + chunks.size());
        }
    }

    private static class RecordingKeywordIndexPort
            implements com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort {

        private final List<String> keywordWrites = new ArrayList<>();

        @Override
        public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            keywordWrites.add(kbId + "/" + docId + "/" + chunks.size());
        }

        @Override
        public void deleteDocumentChunks(String kbId, String docId) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
