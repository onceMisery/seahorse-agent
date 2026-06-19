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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.ChunkerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EmbedderNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IndexerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.ParserNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadFileContent;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadProcessOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelKnowledgeDocumentServiceTests {

    @Test
    void shouldUploadDocumentAndPublishChunkEvent() {
        Ports ports = new Ports();
        KernelKnowledgeDocumentService service = newService(ports);

        KnowledgeDocumentRecord document = service.upload(new UploadKnowledgeDocumentCommand(
                1L,
                new UploadFileContent(new ByteArrayInputStream("content".getBytes()), 7L, "policy.pdf", "pdf"),
                "tester",
                new UploadProcessOptions("pipeline", "pipeline-1")));
        service.startChunk(document.id(), "tester");

        assertThat(document.kbId()).isEqualTo(1L);
        assertThat(document.file().fileUrl()).isEqualTo("local://policy.pdf");
        assertThat(ports.storage.uploadedBuckets).containsExactly(
                KnowledgeStorageBucketNames.fromCollectionName("collection-a"));
        assertThat(ports.repository.records).hasSize(1);
        assertThat(ports.messageQueue.messages).hasSize(1);
        assertThat(ports.messageQueue.messages.get(0).body()).isInstanceOf(KnowledgeDocumentChunkEvent.class);
        KnowledgeDocumentChunkEvent event = (KnowledgeDocumentChunkEvent) ports.messageQueue.messages.get(0).body();
        assertThat(event.pipelineId()).isEqualTo("pipeline-1");
        assertThat(ports.repository.records.get(0).process().status()).isEqualTo("running");
    }

    @Test
    void shouldUploadDocumentBeforeKnowledgeBaseHasAnyChunks() {
        Ports ports = new Ports(new UploadableOnlyKnowledgeBaseQueryPort());
        KernelKnowledgeDocumentService service = newService(ports);

        KnowledgeDocumentRecord document = service.upload(new UploadKnowledgeDocumentCommand(
                1L,
                new UploadFileContent(new ByteArrayInputStream("content".getBytes()), 7L, "policy.pdf", "pdf"),
                "tester",
                new UploadProcessOptions("pipeline", "pipeline-1")));

        assertThat(document.kbId()).isEqualTo(1L);
        assertThat(document.file().fileUrl()).isEqualTo("local://policy.pdf");
        assertThat(ports.storage.uploadedBuckets).containsExactly(
                KnowledgeStorageBucketNames.fromCollectionName("collection-a"));
    }

    @Test
    void shouldExecutePipelineAndMarkSuccess() {
        Ports ports = new Ports();
        KernelKnowledgeDocumentService service = newService(ports);
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                1L,
                "policy.pdf",
                new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 7L),
                new KnowledgeDocumentProcessRef("pending", "pipeline", "pipeline-1"),
                "tester"));

        service.executeChunk(document.id(), pipeline(), "tester");

        KnowledgeDocumentRecord updated = ports.repository.findById(document.id()).orElseThrow();
        assertThat(updated.process().status()).isEqualTo("success");
        assertThat(ports.repository.successChunkCount).isEqualTo(1);
    }

    @Test
    void shouldAttachTenantMetadataWhenExecutingDefaultDocumentPipeline() {
        Ports ports = new Ports();
        RecordingVectorIndexPort vectorIndexPort = new RecordingVectorIndexPort();
        KernelKnowledgeDocumentService service = newService(ports, vectorIndexPort, KeywordIndexPort.noop());
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                1L,
                "policy.pdf",
                new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 7L),
                new KnowledgeDocumentProcessRef("pending", "pipeline", "pipeline-1"),
                "tester"));

        TenantContext.set("tenant-1");
        try {
            service.executeChunk(document.id(), PipelineDefinition.builder().id("pipeline-1").build(), "tester");
        } finally {
            TenantContext.clear();
        }

        assertThat(vectorIndexPort.indexedChunks).hasSize(1);
        assertThat(vectorIndexPort.indexedChunks.get(0).getMetadata())
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("kb_id", "1")
                .containsEntry("doc_id", "1");
    }

    @Test
    void chunkHandlerShouldLoadPipelineAndExecuteDocument() {
        Ports ports = new Ports();
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                1L,
                "policy.pdf",
                new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 7L),
                new KnowledgeDocumentProcessRef("pending", "pipeline", "pipeline-1"),
                "tester"));
        KernelKnowledgeDocumentChunkHandler handler = new KernelKnowledgeDocumentChunkHandler(
                newService(ports),
                pipelineId -> Optional.of(pipeline()));

        handler.handle(new KnowledgeDocumentChunkEvent(document.id(), 1L, "tester", "pipeline-1"));

        assertThat(ports.repository.findById(document.id()).orElseThrow().process().status()).isEqualTo("success");
    }

    @Test
    void chunkHandlerShouldUseDefaultPipelineWhenEventPipelineIdIsBlank() {
        Ports ports = new Ports();
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                1L,
                "policy.pdf",
                new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 7L),
                new KnowledgeDocumentProcessRef("pending", "pipeline", ""),
                "tester"));
        List<String> requestedPipelineIds = new ArrayList<>();
        KernelKnowledgeDocumentChunkHandler handler = new KernelKnowledgeDocumentChunkHandler(
                newService(ports),
                pipelineId -> {
                    requestedPipelineIds.add(pipelineId);
                    return Optional.empty();
                });

        handler.handle(new KnowledgeDocumentChunkEvent(document.id(), 1L, "tester", ""));

        assertThat(requestedPipelineIds).isEmpty();
        assertThat(ports.repository.findById(document.id()).orElseThrow().process().status()).isEqualTo("success");
        assertThat(ports.repository.successChunkCount).isEqualTo(1);
    }

    @Test
    void shouldSyncKeywordIndexWhenEnableOrDeleteDocument() {
        Ports ports = new Ports();
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                1L,
                "policy.pdf",
                new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 7L),
                new KnowledgeDocumentProcessRef("success", "pipeline", "pipeline-1"),
                "tester"));
        ports.repository.detail = documentDetail(document.id(), true);
        ports.repository.enabledChunks = List.of(chunkRecord(document.id(), 1L, "content"));
        RecordingVectorIndexPort vectorIndexPort = new RecordingVectorIndexPort();
        RecordingKeywordIndexPort keywordIndexPort = new RecordingKeywordIndexPort();
        KernelKnowledgeDocumentService service = newService(ports, vectorIndexPort, keywordIndexPort);

        service.enable(document.id(), false, "tester");
        service.enable(document.id(), true, "tester");
        service.delete(document.id(), "tester");

        assertThat(keywordIndexPort.deletedDocuments).containsExactly("1/" + document.id(),
                "1/" + document.id());
        assertThat(keywordIndexPort.indexedDocuments).containsExactly("1/" + document.id() + "/1");
        assertThat(vectorIndexPort.deletedDocuments).containsExactly("collection-a/" + document.id(),
                "collection-a/" + document.id());
        assertThat(vectorIndexPort.indexedDocuments).containsExactly("collection-a/" + document.id() + "/1");
    }

    private KernelKnowledgeDocumentService newService(Ports ports) {
        return newService(ports, new NoopVectorIndexPort(), KeywordIndexPort.noop());
    }

    private KernelKnowledgeDocumentService newService(Ports ports,
                                                     VectorIndexPort vectorIndexPort,
                                                     KeywordIndexPort keywordIndexPort) {
        KnowledgeDocumentServicePorts servicePorts = new KnowledgeDocumentServicePorts(
                ports.knowledgeBaseQuery, ports.repository, ports.storage, ports.messageQueue,
                ingestionEngine(vectorIndexPort, keywordIndexPort));
        KnowledgeDocumentVectorPorts vectorPorts = new KnowledgeDocumentVectorPorts(
                EmbeddingModelPort.noop(), vectorIndexPort, keywordIndexPort);
        return new KernelKnowledgeDocumentService(servicePorts, vectorPorts, "topic");
    }

    private KnowledgeDocumentDetail documentDetail(Long docId, boolean enabled) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(docId);
        detail.setKbId(1L);
        detail.setCollectionName("collection-a");
        detail.setEmbeddingModel("embedding-a");
        detail.setFileUrl("local://policy.pdf");
        detail.setStatus("success");
        detail.setEnabled(enabled);
        return detail;
    }

    private KnowledgeChunkRecord chunkRecord(Long docId, Long chunkId, String content) {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(chunkId);
        record.setKbId(1L);
        record.setDocId(docId);
        record.setChunkIndex(0);
        record.setContent(content);
        record.setEnabled(1);
        return record;
    }

    private KernelIngestionEngine ingestionEngine() {
        return ingestionEngine(new NoopVectorIndexPort(), KeywordIndexPort.noop());
    }

    private KernelIngestionEngine ingestionEngine(VectorIndexPort vectorIndexPort, KeywordIndexPort keywordIndexPort) {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registerNodeFeature(registry, new ChunkNodeFeature());
        registerNodeFeature(registry, new ParserNodeFeature(DocumentParserPort.plainText()));
        registerNodeFeature(registry, new ChunkerNodeFeature((modelId, text) -> List.of(0.1F)));
        registerNodeFeature(registry, new EmbedderNodeFeature((modelId, text) -> List.of(0.1F)));
        registerNodeFeature(registry, new IndexerNodeFeature(
                new NoopVectorCollectionAdminPort(),
                vectorIndexPort,
                new RecordingKnowledgeChunkRepository(),
                keywordIndexPort));
        return new KernelIngestionEngine(registry, FeatureActivationContext.empty());
    }

    private void registerNodeFeature(DefaultExtensionRegistry registry, IngestionNodeFeature feature) {
        registry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
    }

    private PipelineDefinition pipeline() {
        return pipeline("pipeline-1");
    }

    private PipelineDefinition pipeline(String pipelineId) {
        return PipelineDefinition.builder()
                .id(pipelineId)
                .nodes(List.of(NodeConfig.builder().nodeId("chunk").nodeType("chunk").build()))
                .build();
    }

    private static class ChunkNodeFeature implements IngestionNodeFeature {

        @Override
        public String nodeType() {
            return "chunk";
        }

        @Override
        public String name() {
            return "chunk";
        }

        @Override
        public NodeResult execute(IngestionContext context, NodeConfig config) {
            context.setChunks(List.of(VectorChunk.builder()
                    .chunkId("1")
                    .index(0)
                    .content("content")
                    .embedding(new float[]{0.1F})
                    .build()));
            return NodeResult.ok("done");
        }
    }

    private static class Ports {

        private final KnowledgeBaseQueryPort knowledgeBaseQuery;
        private final InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        private final InMemoryObjectStorage storage = new InMemoryObjectStorage();
        private final RecordingMessageQueue messageQueue = new RecordingMessageQueue();

        private Ports() {
            this(new StaticKnowledgeBaseQueryPort());
        }

        private Ports(KnowledgeBaseQueryPort knowledgeBaseQuery) {
            this.knowledgeBaseQuery = knowledgeBaseQuery;
        }
    }

    private static class StaticKnowledgeBaseQueryPort implements KnowledgeBaseQueryPort {

        @Override
        public Optional<KnowledgeBaseRef> findById(Long kbId) {
            return Optional.of(new KnowledgeBaseRef(1L, "knowledge-base", "collection-a"));
        }

        @Override
        public List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
            return List.of(new KnowledgeBaseRef(1L, "知识库", "collection-a"));
        }

        @Override
        public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeChunkSummary> listChunksByDocId(Long docId) {
            return List.of();
        }
    }

    private static final class UploadableOnlyKnowledgeBaseQueryPort implements KnowledgeBaseQueryPort {

        @Override
        public Optional<KnowledgeBaseRef> findById(Long kbId) {
            return Optional.of(new KnowledgeBaseRef(1L, "knowledge-base", "collection-a"));
        }

        @Override
        public List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
            return List.of();
        }

        @Override
        public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeChunkSummary> listChunksByDocId(Long docId) {
            return List.of();
        }
    }

    private static class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final List<KnowledgeDocumentRecord> records = new ArrayList<>();
        private KnowledgeDocumentDetail detail;
        private List<KnowledgeChunkRecord> enabledChunks = List.of();
        private int successChunkCount;

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                    (long) (records.size() + 1),
                    command.kbId(),
                    command.docName(),
                    command.file(),
                    new KnowledgeDocumentProcessRef("pending",
                            command.process().processMode(), command.process().pipelineId()));
            records.add(record);
            return record;
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(Long docId) {
            return records.stream().filter(record -> record.id().equals(docId)).findFirst();
        }

        @Override
        public Optional<KnowledgeDocumentDetail> findDetailById(Long docId) {
            return detail != null && detail.getId().equals(docId) ? Optional.of(detail) : Optional.empty();
        }

        @Override
        public boolean markRunning(Long docId, String operator) {
            return replaceStatus(docId, "running");
        }

        @Override
        public void markSuccess(Long docId, int chunkCount, String operator) {
            successChunkCount = chunkCount;
            replaceStatus(docId, "success");
        }

        @Override
        public void markFailed(Long docId, String operator, String errorMessage) {
            replaceStatus(docId, "failed");
        }

        @Override
        public boolean updateEnabled(Long docId, boolean enabled, String operator) {
            if (detail == null || !detail.getId().equals(docId)) {
                return false;
            }
            detail.setEnabled(enabled);
            return true;
        }

        @Override
        public boolean delete(Long docId, String operator) {
            return findById(docId).isPresent();
        }

        @Override
        public List<KnowledgeChunkRecord> listEnabledChunks(Long docId) {
            return enabledChunks;
        }

        private boolean replaceStatus(Long docId, String status) {
            for (int index = 0; index < records.size(); index++) {
                KnowledgeDocumentRecord record = records.get(index);
                if (!record.id().equals(docId)) {
                    continue;
                }
                records.set(index, new KnowledgeDocumentRecord(record.id(), record.kbId(), record.docName(),
                        record.file(), new KnowledgeDocumentProcessRef(status,
                        record.process().processMode(), record.process().pipelineId())));
                return true;
            }
            return false;
        }
    }

    private static class RecordingVectorIndexPort implements VectorIndexPort {

        private final List<String> indexedDocuments = new ArrayList<>();
        private final List<String> deletedDocuments = new ArrayList<>();
        private final List<VectorChunk> indexedChunks = new ArrayList<>();

        @Override
        public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
            indexedDocuments.add(collectionName + "/" + docId + "/" + chunks.get(0).getChunkId());
            indexedChunks.addAll(chunks);
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

    private static class NoopVectorCollectionAdminPort implements VectorCollectionAdminPort {

        @Override
        public boolean collectionExists(String collectionName) {
            return true;
        }

        @Override
        public void ensureCollection(String collectionName) {
        }
    }

    private static class RecordingKnowledgeChunkRepository implements KnowledgeChunkRepositoryPort {

        @Override
        public void replaceDocumentChunks(Long kbId, Long docId, List<VectorChunk> chunks) {
        }
    }

    private static class RecordingKeywordIndexPort implements KeywordIndexPort {

        private final List<String> indexedDocuments = new ArrayList<>();
        private final List<String> deletedDocuments = new ArrayList<>();

        @Override
        public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            indexedDocuments.add(kbId + "/" + docId + "/" + chunks.get(0).getChunkId());
        }

        @Override
        public void deleteDocumentChunks(String kbId, String docId) {
            deletedDocuments.add(kbId + "/" + docId);
        }
    }

    private static class InMemoryObjectStorage implements ObjectStoragePort {

        private final List<String> uploadedBuckets = new ArrayList<>();

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
            uploadedBuckets.add(bucketName);
            return new StoredObject("local://" + originalFilename, contentType, size, originalFilename);
        }

        @Override
        public StoredObject reliableUpload(String bucketName, InputStream content, long size,
                                           String originalFilename, String contentType) {
            return upload(bucketName, content, size, originalFilename, contentType);
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream("content".getBytes());
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }

    private static class RecordingMessageQueue implements MessageQueuePort {

        private final List<Message> messages = new ArrayList<>();

        @Override
        public com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSendReceipt send(
                String topic, String key, String bizDesc, Object body) {
            messages.add(new Message(topic, key, body));
            return new com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSendReceipt(
                    "message-1", topic, key, 1L);
        }

        @Override
        public void publishReliable(String topic, String key, String bizDesc, Object body) {
            send(topic, key, bizDesc, body);
        }

        private record Message(String topic, String key, Object body) {
        }
    }

    private static class NoopVectorIndexPort implements VectorIndexPort {

        @Override
        public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        }

        @Override
        public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        }

        @Override
        public void deleteDocumentVectors(String collectionName, String docId) {
        }

        @Override
        public void deleteChunkById(String collectionName, String chunkId) {
        }

        @Override
        public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        }
    }
}
