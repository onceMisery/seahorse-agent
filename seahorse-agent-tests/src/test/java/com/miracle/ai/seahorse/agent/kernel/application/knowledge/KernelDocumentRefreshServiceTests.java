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

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionStart;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedule;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshScheduleUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelDocumentRefreshServiceTests {

    @Test
    void shouldRefreshChangedUrlDocumentAndRecordSuccess() {
        Ports ports = new Ports();
        ports.fetcher.result = new DocumentFetchResult("fresh content".getBytes(), "text/plain", "fresh.txt");
        KernelDocumentRefreshService service = newService(ports);

        DocumentRefreshResult result = service.refreshDocument("doc-1", "system");

        assertThat(result.status()).isEqualTo("success");
        assertThat(ports.repository.replacedFile.fileUrl()).isEqualTo("local://fresh.txt");
        assertThat(ports.documentPort.executedDocIds).containsExactly("doc-1");
        assertThat(ports.schedulePort.updates).extracting(DocumentRefreshScheduleUpdate::status)
                .containsExactly("success");
        assertThat(ports.stateRepository.finishes).extracting(DocumentRefreshExecutionFinish::status)
                .containsExactly("success");
    }

    @Test
    void shouldSkipRefreshWhenContentHashIsUnchanged() {
        Ports ports = new Ports();
        ports.fetcher.result = new DocumentFetchResult("same content".getBytes(), "text/plain", "same.txt");
        ports.schedulePort.schedule = ports.schedulePort.schedule.withLastContentHash(
                KernelDocumentRefreshService.contentHash("same content".getBytes()));
        KernelDocumentRefreshService service = newService(ports);

        DocumentRefreshResult result = service.refreshDocument("doc-1", "system");

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(ports.repository.replacedFile).isNull();
        assertThat(ports.documentPort.executedDocIds).isEmpty();
        assertThat(ports.schedulePort.updates).extracting(DocumentRefreshScheduleUpdate::status)
                .containsExactly("skipped");
    }

    @Test
    void shouldRecordFailedStateWhenFetchFails() {
        Ports ports = new Ports();
        ports.fetcher.failure = new IllegalStateException("remote unavailable");
        KernelDocumentRefreshService service = newService(ports);

        DocumentRefreshResult result = service.refreshDocument("doc-1", "system");

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.message()).contains("remote unavailable");
        assertThat(ports.repository.replacedFile).isNull();
        assertThat(ports.schedulePort.updates).extracting(DocumentRefreshScheduleUpdate::status)
                .containsExactly("failed");
        assertThat(ports.stateRepository.finishes).extracting(DocumentRefreshExecutionFinish::status)
                .containsExactly("failed");
    }

    private KernelDocumentRefreshService newService(Ports ports) {
        return new KernelDocumentRefreshService(new DocumentRefreshServicePorts(
                ports.schedulePort,
                ports.stateRepository,
                ports.repository,
                ports.fetcher,
                ports.storage,
                ports.documentPort,
                ports.pipelineRepository,
                ports.schedulerPort));
    }

    private static class Ports {

        private final InMemorySchedulePort schedulePort = new InMemorySchedulePort();
        private final InMemoryStateRepository stateRepository = new InMemoryStateRepository();
        private final InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        private final FakeDocumentFetcher fetcher = new FakeDocumentFetcher();
        private final InMemoryObjectStorage storage = new InMemoryObjectStorage();
        private final RecordingDocumentPort documentPort = new RecordingDocumentPort();
        private final PipelineDefinitionRepositoryPort pipelineRepository = pipelineId -> Optional.of(
                PipelineDefinition.builder().id(pipelineId).build());
        private final SchedulerPort schedulerPort = (cron, from) -> from.plusSeconds(60);
    }

    private static class InMemorySchedulePort implements DocumentRefreshSchedulePort {

        private DocumentRefreshSchedule schedule = new DocumentRefreshSchedule(
                "schedule-1", "doc-1", "kb-1", "0 0/5 * * * ?", true,
                Instant.parse("2026-05-10T00:00:00Z"), null, null, null);
        private final List<DocumentRefreshScheduleUpdate> updates = new ArrayList<>();

        @Override
        public Optional<DocumentRefreshSchedule> findByDocumentId(String docId) {
            return Optional.ofNullable(schedule).filter(value -> value.docId().equals(docId));
        }

        @Override
        public List<DocumentRefreshSchedule> findDueSchedules(Instant now, int limit) {
            return List.of(schedule);
        }

        @Override
        public void upsert(DocumentRefreshSchedule schedule) {
            this.schedule = schedule;
        }

        @Override
        public void updateState(DocumentRefreshScheduleUpdate update) {
            updates.add(update);
        }
    }

    private static class InMemoryStateRepository implements DocumentRefreshStateRepositoryPort {

        private final List<DocumentRefreshExecutionFinish> finishes = new ArrayList<>();

        @Override
        public String start(DocumentRefreshExecutionStart execution) {
            return "exec-1";
        }

        @Override
        public void finish(DocumentRefreshExecutionFinish execution) {
            finishes.add(execution);
        }
    }

    private static class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private KnowledgeDocumentFileRef replacedFile;

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            return null;
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(String docId) {
            return Optional.empty();
        }

        @Override
        public Optional<KnowledgeDocumentDetail> findDetailById(String docId) {
            KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
            detail.setId(docId);
            detail.setKbId("kb-1");
            detail.setCollectionName("collection-a");
            detail.setDocName("remote.txt");
            detail.setSourceType("URL");
            detail.setSourceLocation("https://example.test/remote.txt");
            detail.setScheduleEnabled(1);
            detail.setScheduleCron("0 0/5 * * * ?");
            detail.setEnabled(true);
            detail.setStatus("success");
            detail.setPipelineId("pipeline-1");
            return Optional.of(detail);
        }

        @Override
        public boolean markRunning(String docId, String operator) {
            return true;
        }

        @Override
        public void markSuccess(String docId, int chunkCount, String operator) {
        }

        @Override
        public void markFailed(String docId, String operator, String errorMessage) {
        }

        @Override
        public boolean replaceFileForRefresh(String docId, KnowledgeDocumentFileRef file, String operator) {
            replacedFile = file;
            return true;
        }
    }

    private static class FakeDocumentFetcher implements DocumentFetcherPort {

        private DocumentFetchResult result;
        private RuntimeException failure;

        @Override
        public boolean supports(String sourceType) {
            return "url".equalsIgnoreCase(sourceType);
        }

        @Override
        public DocumentFetchResult fetch(DocumentFetchRequest request) {
            if (failure != null) {
                throw failure;
            }
            return result;
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
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }

    private static class RecordingDocumentPort implements KnowledgeDocumentInboundPort {

        private final List<String> executedDocIds = new ArrayList<>();

        @Override
        public KnowledgeDocumentRecord upload(UploadKnowledgeDocumentCommand command) {
            return null;
        }

        @Override
        public void startChunk(String docId, String operator) {
        }

        @Override
        public void executeChunk(String docId, PipelineDefinition pipeline, String operator) {
            executedDocIds.add(docId);
        }

        @Override
        public KnowledgeDocumentDetail queryById(String docId) {
            return null;
        }

        @Override
        public KnowledgeDocumentPage page(String kbId, KnowledgeDocumentPageCommand command) {
            return null;
        }

        @Override
        public List<KnowledgeDocumentSummary> search(String keyword, int limit) {
            return List.of();
        }

        @Override
        public void update(String docId, UpdateKnowledgeDocumentCommand command) {
        }

        @Override
        public void enable(String docId, boolean enabled, String operator) {
        }

        @Override
        public void delete(String docId, String operator) {
        }

        @Override
        public KnowledgeDocumentChunkLogPage chunkLogs(String docId, long current, long size) {
            return null;
        }
    }
}
