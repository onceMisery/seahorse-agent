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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                .metadata(Map.of("tenantId", "tenant-1", "kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(ports.collectionNames).containsExactly("collection-a");
        assertThat(ports.repositoryWrites).containsExactly("kb-1/doc-1/2");
        assertThat(ports.vectorWrites).containsExactly("collection-a/doc-1/2");
        assertThat(ports.repositoryBatches.get(0).get(0).getMetadata())
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("kb_id", "kb-1")
                .containsEntry("doc_id", "doc-1")
                .containsEntry("chunk_id", "chunk-1")
                .containsEntry("chunk_index", 0)
                .containsEntry("collection_name", "collection-a")
                .containsEntry("enabled", true);
    }

    @Test
    void shouldWriteKeywordIndexThroughPort() {
        RecordingPorts ports = new RecordingPorts();
        RecordingKeywordIndexPort keywordIndexPort = new RecordingKeywordIndexPort();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports, keywordIndexPort);
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .chunks(List.of(chunk("chunk-1", 0), chunk("chunk-2", 1)))
                .metadata(Map.of("tenantId", "tenant-1", "kbId", "kb-1", "collectionName", "collection-a"))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(keywordIndexPort.keywordWrites).containsExactly("kb-1/doc-1/2");
        assertThat(keywordIndexPort.lastChunks.get(0).getMetadata())
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("kb_id", "kb-1")
                .containsEntry("doc_id", "doc-1");
    }

    @Test
    void shouldFilterVectorMetadataBySystemKeysAndSchemaPushdown() {
        RecordingPorts ports = new RecordingPorts();
        IndexerNodeFeature feature = new IndexerNodeFeature(ports, ports, ports);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenant_id", "tenant-1");
        metadata.put("file_type", "pdf");
        metadata.put("acl_subjects", List.of("dept-a"));
        metadata.put("department", "HR");
        metadata.put("internal_note", "only-for-postgres");
        IngestionContext context = IngestionContext.builder()
                .taskId("doc-1")
                .chunks(List.of(chunk("chunk-1", 0, metadata)))
                .metadata(Map.of("kbId", "kb-1", "collectionName", "collection-a"))
                .metadataSchema(schema(
                        field("department", "department", true),
                        field("internal_note", "internal_note", false)))
                .build();

        NodeResult result = feature.execute(context, NodeConfig.builder().nodeType("indexer").build());

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> repositoryMetadata = ports.repositoryBatches.get(0).get(0).getMetadata();
        assertThat(repositoryMetadata)
                .containsEntry("department", "HR")
                .containsEntry("internal_note", "only-for-postgres");
        Map<String, Object> vectorMetadata = ports.vectorBatches.get(0).get(0).getMetadata();
        assertThat(vectorMetadata)
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("kb_id", "kb-1")
                .containsEntry("doc_id", "doc-1")
                .containsEntry("chunk_id", "chunk-1")
                .containsEntry("chunk_index", 0)
                .containsEntry("collection_name", "collection-a")
                .containsEntry("file_type", "pdf")
                .containsEntry("acl_subjects", List.of("dept-a"))
                .containsEntry("department", "HR")
                .doesNotContainKey("internal_note");
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

    private VectorChunk chunk(String chunkId, int index, Map<String, Object> metadata) {
        return VectorChunk.builder()
                .chunkId(chunkId)
                .index(index)
                .content("content-" + index)
                .metadata(metadata)
                .embedding(new float[]{1.0F, 2.0F})
                .build();
    }

    private MetadataSchema schema(MetadataFieldDescriptor... fields) {
        return new MetadataSchema("tenant-1", "kb-1", 1, List.of(fields));
    }

    private MetadataFieldDescriptor field(String fieldKey, String canonicalName, boolean pushdownToVector) {
        return new MetadataFieldDescriptor(
                fieldKey,
                fieldKey,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ),
                false,
                true,
                false,
                false,
                true,
                MetadataIndexPolicy.MILVUS_JSON,
                0.8D,
                Set.of("source"),
                Map.of(),
                new BackendFieldMapping(canonicalName, "", "", canonicalName, pushdownToVector,
                        true, false, Map.of()));
    }

    private static class RecordingPorts implements com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort,
            com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort,
            com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort {

        private final List<String> collectionNames = new ArrayList<>();
        private final List<String> repositoryWrites = new ArrayList<>();
        private final List<String> vectorWrites = new ArrayList<>();
        private final List<List<VectorChunk>> repositoryBatches = new ArrayList<>();
        private final List<List<VectorChunk>> vectorBatches = new ArrayList<>();

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
            vectorBatches.add(List.copyOf(chunks));
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
        public void replaceDocumentChunks(Long kbId, Long docId, List<VectorChunk> chunks) {
            repositoryWrites.add(kbId + "/" + docId + "/" + chunks.size());
            repositoryBatches.add(List.copyOf(chunks));
        }
    }

    private static class RecordingKeywordIndexPort
            implements com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort {

        private final List<String> keywordWrites = new ArrayList<>();
        private List<VectorChunk> lastChunks = List.of();

        @Override
        public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            keywordWrites.add(kbId + "/" + docId + "/" + chunks.size());
            lastChunks = List.copyOf(chunks);
        }

        @Override
        public void deleteDocumentChunks(String kbId, String docId) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
