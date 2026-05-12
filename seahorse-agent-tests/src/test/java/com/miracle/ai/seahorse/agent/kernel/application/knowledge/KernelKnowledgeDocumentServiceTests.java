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
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadFileContent;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadProcessOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
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
                "kb-1",
                new UploadFileContent(new ByteArrayInputStream("content".getBytes()), 7L, "policy.pdf", "pdf"),
                "tester",
                new UploadProcessOptions("pipeline", "pipeline-1")));
        service.startChunk(document.id(), "tester");

        assertThat(document.kbId()).isEqualTo("kb-1");
        assertThat(document.file().fileUrl()).isEqualTo("local://policy.pdf");
        assertThat(ports.repository.records).hasSize(1);
        assertThat(ports.messageQueue.messages).hasSize(1);
        assertThat(ports.messageQueue.messages.get(0).body()).isInstanceOf(KnowledgeDocumentChunkEvent.class);
        KnowledgeDocumentChunkEvent event = (KnowledgeDocumentChunkEvent) ports.messageQueue.messages.get(0).body();
        assertThat(event.pipelineId()).isEqualTo("pipeline-1");
        assertThat(ports.repository.records.get(0).process().status()).isEqualTo("running");
    }

    @Test
    void shouldExecutePipelineAndMarkSuccess() {
        Ports ports = new Ports();
        KernelKnowledgeDocumentService service = newService(ports);
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                "kb-1",
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
    void chunkHandlerShouldLoadPipelineAndExecuteDocument() {
        Ports ports = new Ports();
        KnowledgeDocumentRecord document = ports.repository.createPendingDocument(new CreateKnowledgeDocumentCommand(
                "kb-1",
                "policy.pdf",
                new KnowledgeDocumentFileRef("local://policy.pdf", "pdf", 7L),
                new KnowledgeDocumentProcessRef("pending", "pipeline", "pipeline-1"),
                "tester"));
        KernelKnowledgeDocumentChunkHandler handler = new KernelKnowledgeDocumentChunkHandler(
                newService(ports),
                pipelineId -> Optional.of(pipeline()));

        handler.handle(new KnowledgeDocumentChunkEvent(document.id(), "kb-1", "tester", "pipeline-1"));

        assertThat(ports.repository.findById(document.id()).orElseThrow().process().status()).isEqualTo("success");
    }

    private KernelKnowledgeDocumentService newService(Ports ports) {
        KnowledgeDocumentServicePorts servicePorts = new KnowledgeDocumentServicePorts(
                ports.knowledgeBaseQuery, ports.repository, ports.storage, ports.messageQueue, ingestionEngine());
        KnowledgeDocumentVectorPorts vectorPorts = new KnowledgeDocumentVectorPorts(
                EmbeddingModelPort.noop(), new NoopVectorIndexPort());
        return new KernelKnowledgeDocumentService(servicePorts, vectorPorts, "topic");
    }

    private KernelIngestionEngine ingestionEngine() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        IngestionNodeFeature feature = new ChunkNodeFeature();
        registry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return new KernelIngestionEngine(registry, FeatureActivationContext.empty());
    }

    private PipelineDefinition pipeline() {
        return PipelineDefinition.builder()
                .id("pipeline-1")
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
                    .chunkId("chunk-1")
                    .index(0)
                    .content("content")
                    .embedding(new float[]{0.1F})
                    .build()));
            return NodeResult.ok("done");
        }
    }

    private static class Ports {

        private final KnowledgeBaseQueryPort knowledgeBaseQuery = new StaticKnowledgeBaseQueryPort();
        private final InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        private final ObjectStoragePort storage = new InMemoryObjectStorage();
        private final RecordingMessageQueue messageQueue = new RecordingMessageQueue();
    }

    private static class StaticKnowledgeBaseQueryPort implements KnowledgeBaseQueryPort {

        @Override
        public List<KnowledgeBaseRef> listSearchableKnowledgeBases() {
            return List.of(new KnowledgeBaseRef("kb-1", "知识库", "collection-a"));
        }

        @Override
        public List<KnowledgeDocumentSummary> searchDocuments(String keyword, int limit) {
            return List.of();
        }

        @Override
        public List<KnowledgeChunkSummary> listChunksByDocId(String docId) {
            return List.of();
        }
    }

    private static class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final List<KnowledgeDocumentRecord> records = new ArrayList<>();
        private int successChunkCount;

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            KnowledgeDocumentRecord record = new KnowledgeDocumentRecord(
                    "doc-" + (records.size() + 1),
                    command.kbId(),
                    command.docName(),
                    command.file(),
                    new KnowledgeDocumentProcessRef("pending",
                            command.process().processMode(), command.process().pipelineId()));
            records.add(record);
            return record;
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(String docId) {
            return records.stream().filter(record -> record.id().equals(docId)).findFirst();
        }

        @Override
        public boolean markRunning(String docId, String operator) {
            return replaceStatus(docId, "running");
        }

        @Override
        public void markSuccess(String docId, int chunkCount, String operator) {
            successChunkCount = chunkCount;
            replaceStatus(docId, "success");
        }

        @Override
        public void markFailed(String docId, String operator, String errorMessage) {
            replaceStatus(docId, "failed");
        }

        private boolean replaceStatus(String docId, String status) {
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

    private static class InMemoryObjectStorage implements ObjectStoragePort {

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size, String originalFilename,
                                   String contentType) {
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
