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

package com.miracle.ai.seahorse.agent.adapters.spring.metadata;

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentVectorPorts;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexRebuildResult;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataIndexCompensationAdapterTests {

    @Test
    void shouldRebuildKeywordAndVectorIndexesWithFilteredVectorMetadata() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.put(document(true));
        repository.putChunks(1L, List.of(chunk("1", 0, Map.of(
                "department", "hr",
                "owner", "alice",
                "acl_subjects", List.of("user-1"),
                "security_level", "internal"))));
        TrackingKeywordIndexMaintenanceInboundPort keywordPort = new TrackingKeywordIndexMaintenanceInboundPort();
        TrackingVectorIndexPort vectorIndexPort = new TrackingVectorIndexPort();
        MetadataIndexCompensationAdapter adapter = new MetadataIndexCompensationAdapter(
                repository,
                keywordPort,
                new KnowledgeDocumentVectorPorts(
                        (modelId, text) -> List.of(0.1F, 0.2F),
                        vectorIndexPort),
                schemaRegistry(),
                null);

        adapter.rebuildDocument("tenant-1", "1", "1");

        assertThat(keywordPort.rebuiltDocumentIds).containsExactly("1");
        assertThat(vectorIndexPort.deletedDocuments).containsExactly("collection-1/1");
        assertThat(vectorIndexPort.indexedDocuments).containsExactly("collection-1/1");
        assertThat(vectorIndexPort.lastIndexedChunks).hasSize(1);
        assertThat(vectorIndexPort.lastIndexedChunks.get(0).getMetadata())
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("kb_id", "1")
                .containsEntry("doc_id", "1")
                .containsEntry("collection_name", "collection-1")
                .containsEntry("chunk_id", "chunk-1")
                .containsEntry("chunk_index", 0)
                .containsEntry("enabled", true)
                .containsEntry("file_type", "text/plain")
                .containsEntry("source_type", "upload")
                .containsEntry("department", "hr")
                .containsEntry("security_level", "internal");
        assertThat(vectorIndexPort.lastIndexedChunks.get(0).getMetadata())
                .containsKey("acl_subjects")
                .doesNotContainKey("owner");
    }

    @Test
    void shouldOnlyDeleteVectorDocumentWhenDocumentDisabled() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.put(document(false));
        repository.putChunks(1L, List.of(chunk("1", 0, Map.of("department", "hr"))));
        TrackingKeywordIndexMaintenanceInboundPort keywordPort = new TrackingKeywordIndexMaintenanceInboundPort();
        TrackingVectorIndexPort vectorIndexPort = new TrackingVectorIndexPort();
        MetadataIndexCompensationAdapter adapter = new MetadataIndexCompensationAdapter(
                repository,
                keywordPort,
                new KnowledgeDocumentVectorPorts(
                        (modelId, text) -> List.of(0.1F, 0.2F),
                        vectorIndexPort),
                schemaRegistry(),
                null);

        adapter.rebuildDocument("tenant-1", "1", "1");

        assertThat(keywordPort.rebuiltDocumentIds).containsExactly("1");
        assertThat(vectorIndexPort.deletedDocuments).containsExactly("collection-1/1");
        assertThat(vectorIndexPort.indexedDocuments).isEmpty();
    }

    @Test
    void shouldCreateBackfillJobForSchemaCompensation() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        TrackingKeywordIndexMaintenanceInboundPort keywordPort = new TrackingKeywordIndexMaintenanceInboundPort();
        TrackingVectorIndexPort vectorIndexPort = new TrackingVectorIndexPort();
        CapturingMetadataBackfillInboundPort backfillPort = new CapturingMetadataBackfillInboundPort();
        MetadataIndexCompensationAdapter adapter = new MetadataIndexCompensationAdapter(
                repository,
                keywordPort,
                new KnowledgeDocumentVectorPorts(
                        (modelId, text) -> List.of(),
                        vectorIndexPort),
                MetadataSchemaRegistryPort.empty(),
                backfillPort);

        adapter.compensateSchemaChange(field("department", 4), field("department", 5));

        assertThat(backfillPort.commands).singleElement().satisfies(command -> assertThat(command)
                .extracting(MetadataBackfillCommand::tenantId,
                        MetadataBackfillCommand::knowledgeBaseId,
                        MetadataBackfillCommand::pipelineId,
                        MetadataBackfillCommand::batchSize,
                        MetadataBackfillCommand::operator)
                .containsExactly("tenant-1", "kb-1", "", 0, "metadata-schema-compensation"));
        assertThat(backfillPort.commands.get(0).metadata())
                .containsEntry("schemaVersion", 5)
                .containsEntry("forceRerun", true)
                .containsEntry("overwriteApproved", true)
                .containsEntry("schemaCompensation", true)
                .containsEntry("schemaCompensationReason", "SCHEMA_CHANGE")
                .containsEntry("schemaTriggerAction", "UPDATE")
                .containsEntry("schemaTriggerFieldKey", "department");
    }

    private MetadataSchemaRegistryPort schemaRegistry() {
        return (tenantId, knowledgeBaseId) -> new MetadataSchema(
                tenantId,
                knowledgeBaseId,
                5,
                List.of(
                        fieldDescriptor("department", true),
                        fieldDescriptor("owner", false)));
    }

    private MetadataFieldDescriptor fieldDescriptor(String fieldKey, boolean pushdownToVector) {
        return new MetadataFieldDescriptor(
                fieldKey,
                fieldKey,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ),
                false,
                true,
                false,
                false,
                pushdownToVector,
                MetadataIndexPolicy.SEARCH_KEYWORD,
                0.8D,
                Set.of(),
                Map.of(),
                new BackendFieldMapping(fieldKey, "", "", fieldKey, pushdownToVector, true, false, Map.of()));
    }

    private MetadataSchemaFieldRecord field(String fieldKey, int schemaVersion) {
        return new MetadataSchemaFieldRecord(
                "field-" + schemaVersion,
                "tenant-1",
                "kb-1",
                fieldKey,
                fieldKey,
                MetadataValueType.STRING,
                Set.of(MetadataOperator.EQ),
                false,
                true,
                false,
                false,
                true,
                MetadataIndexPolicy.SEARCH_KEYWORD,
                0.8D,
                Set.of(),
                Map.of(),
                new BackendFieldMapping(fieldKey, "", "", fieldKey, true, true, false, Map.of()),
                schemaVersion,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private KnowledgeDocumentDetail document(boolean enabled) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(1L);
        detail.setKbId(1L);
        detail.setCollectionName("collection-1");
        detail.setEmbeddingModel("text-embedding-3-small");
        detail.setDocName("doc-1.txt");
        detail.setEnabled(enabled);
        detail.setFileType("text/plain");
        detail.setSourceType("upload");
        detail.setCreateTime(Instant.parse("2026-05-16T00:00:00Z"));
        detail.setUpdateTime(Instant.parse("2026-05-16T00:05:00Z"));
        return detail;
    }

    private KnowledgeChunkRecord chunk(String id, int index, Map<String, Object> metadata) {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(Long.parseLong(id));
        record.setDocId(1L);
        record.setKbId(1L);
        record.setChunkIndex(index);
        record.setContent("hello world");
        record.setEnabled(1);
        record.setMetadata(metadata);
        return record;
    }

    private static final class TrackingKeywordIndexMaintenanceInboundPort implements KeywordIndexMaintenanceInboundPort {

        private final List<String> rebuiltDocumentIds = new ArrayList<>();

        @Override
        public KeywordIndexRebuildResult rebuildDocument(Long docId) {
            rebuiltDocumentIds.add(String.valueOf(docId));
            return new KeywordIndexRebuildResult("DOCUMENT", String.valueOf(docId), 1, 1, 1, 1, 0, 0, List.of());
        }

        @Override
        public KeywordIndexRebuildResult rebuildKnowledgeBase(Long kbId, int batchSize) {
            return new KeywordIndexRebuildResult("KNOWLEDGE_BASE", String.valueOf(kbId), 0, 0, 0, 0, 0, 0, List.of());
        }
    }

    private static final class TrackingVectorIndexPort implements VectorIndexPort {

        private final List<String> deletedDocuments = new ArrayList<>();
        private final List<String> indexedDocuments = new ArrayList<>();
        private List<VectorChunk> lastIndexedChunks = List.of();

        @Override
        public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
            indexedDocuments.add(collectionName + "/" + docId);
            lastIndexedChunks = List.copyOf(chunks);
        }

        @Override
        public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        }

        @Override
        public void deleteDocumentVectors(String collectionName, String docId) {
            deletedDocuments.add(collectionName + "/" + docId);
        }

        @Override
        public void deleteChunkById(String collectionName, String chunkId) {
        }

        @Override
        public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        }
    }

    private static final class CapturingMetadataBackfillInboundPort implements MetadataBackfillInboundPort {

        private final List<MetadataBackfillCommand> commands = new ArrayList<>();

        @Override
        public MetadataBackfillJobRecord createJob(MetadataBackfillCommand command) {
            commands.add(command);
            return new MetadataBackfillJobRecord(
                    "job-1",
                    command.tenantId(),
                    command.knowledgeBaseId(),
                    command.pipelineId(),
                    MetadataBackfillJobStatus.PENDING,
                    1,
                    command.batchSize(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    command.metadata(),
                    List.of(),
                    command.operator(),
                    Instant.EPOCH,
                    Instant.EPOCH);
        }

        @Override
        public MetadataBackfillRunResult runNextBatch(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataBackfillJobRecord getJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataBackfillJobPage pageJobs(MetadataBackfillJobQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataBackfillJobRecord pause(String jobId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataBackfillJobRecord resume(String jobId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataBackfillJobRecord cancel(String jobId, String operator) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final Map<Long, KnowledgeDocumentDetail> documents = new LinkedHashMap<>();
        private final Map<Long, List<KnowledgeChunkRecord>> chunksByDocument = new LinkedHashMap<>();

        void put(KnowledgeDocumentDetail detail) {
            documents.put(detail.getId(), detail);
        }

        void putChunks(Long docId, List<KnowledgeChunkRecord> chunks) {
            chunksByDocument.put(docId, List.copyOf(chunks));
        }

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(Long docId) {
            return Optional.empty();
        }

        @Override
        public Optional<KnowledgeDocumentDetail> findDetailById(Long docId) {
            return Optional.ofNullable(documents.get(docId));
        }

        @Override
        public KnowledgeDocumentPage page(Long kbId, long current, long size, String status, String keyword) {
            return new KnowledgeDocumentPage(List.of(), 0, size, current, 0);
        }

        @Override
        public boolean markRunning(Long docId, String operator) {
            return true;
        }

        @Override
        public void markSuccess(Long docId, int chunkCount, String operator) {
        }

        @Override
        public void markFailed(Long docId, String operator, String errorMessage) {
        }

        @Override
        public boolean updateEnabled(Long docId, boolean enabled, String operator) {
            return false;
        }

        @Override
        public boolean delete(Long docId, String operator) {
            return false;
        }

        @Override
        public List<KnowledgeChunkRecord> listEnabledChunks(Long docId) {
            return chunksByDocument.getOrDefault(docId, List.of());
        }
    }
}
