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
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
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
        assertThat(keywordIndexPort.indexedDocuments).containsExactly("1/" + document.id() + "/chunk-1");
        assertThat(vectorIndexPort.deletedDocuments).containsExactly("collection-a/" + document.id(),
                "collection-a/" + document.id());
        assertThat(vectorIndexPort.indexedDocuments).containsExactly("collection-a/" + document.id() + "/chunk-1");
    }

    private KernelKnowledgeDocumentService newService(Ports ports) {
        return newService(ports, new NoopVectorIndexPort(), KeywordIndexPort.noop());
    }

    private KernelKnowledgeDocumentService newService(Ports ports,
                                                     VectorIndexPort vectorIndexPort,
                                                     KeywordIndexPort keywordIndexPort) {
        KnowledgeDocumentServicePorts servicePorts = new KnowledgeDocumentServicePorts(
                ports.knowledgeBaseQuery, ports.repository, ports.storage, ports.messageQueue, ingestionEngine());
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
        public NodeResult execute(IngestionContext