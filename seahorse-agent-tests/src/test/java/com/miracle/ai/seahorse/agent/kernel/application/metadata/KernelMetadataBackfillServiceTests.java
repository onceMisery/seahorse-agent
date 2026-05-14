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

package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KernelMetadataBackfillServiceTests {

    @Test
    void shouldRunPagedBackfillAndUpdateCheckpoint() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        documents.add(document("doc-2", true, "pipe-1"));
        documents.add(document("doc-3", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of(
                "doc-1", MetadataValidationDecision.ACCEPT,
                "doc-2", MetadataValidationDecision.REVIEW_REQUIRED,
                "doc-3", MetadataValidationDecision.QUARANTINE));

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 2, "admin", Map.of()));
        MetadataBackfillRunResult firstBatch = service.runNextBatch(job.jobId());

        assertThat(firstBatch.status()).isEqualTo(MetadataBackfillJobStatus.PENDING);
        assertThat(firstBatch.currentPage()).isEqualTo(2);
        assertThat(firstBatch.processedDocuments()).isEqualTo(2);
        assertThat(firstBatch.reviewDocuments()).isEqualTo(1);
        assertThat(firstBatch.checkpoint()).containsEntry("currentPage", 2L);
        assertThat(documents.successDocuments).containsExactly("doc-1", "doc-2");

        MetadataBackfillRunResult secondBatch = service.runNextBatch(job.jobId());

        assertThat(secondBatch.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(secondBatch.processedDocuments()).isEqualTo(3);
        assertThat(secondBatch.succeededDocuments()).isEqualTo(3);
        assertThat(secondBatch.reviewDocuments()).isEqualTo(1);
        assertThat(secondBatch.quarantineDocuments()).isEqualTo(1);
        assertThat(secondBatch.failedDocuments()).isZero();
    }

    @Test
    void shouldKeepProcessingWhenOneDocumentFails() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        documents.add(document("doc-2", true, "pipe-1"));
        documents.add(document("doc-3", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = serviceWithFailure(documents, jobs, "doc-2");

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(3);
        assertThat(result.succeededDocuments()).isEqualTo(2);
        assertThat(result.failedDocuments()).isEqualTo(1);
        assertThat(result.failures()).singleElement().asString().contains("doc-2").contains("boom");
        assertThat(documents.failedDocuments).containsExactly("doc-2");
        assertThat(documents.successDocuments).containsExactly("doc-1", "doc-3");
    }

    @Test
    void shouldMarkJobFailedWhenBatchFailsBeforeDocumentProcessing() {
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelIngestionEngine engine = mock(KernelIngestionEngine.class);
        KernelMetadataBackfillService service = new KernelMetadataBackfillService(
                new FailingPageDocumentRepository(),
                new InMemoryObjectStorage(),
                pipelineRepository(),
                engine,
                jobs);

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.FAILED);
        assertThat(result.failures()).singleElement().asString().contains("page unavailable");
        assertThat(jobs.findById(job.jobId())).get()
                .extracting(MetadataBackfillJobRecord::status)
                .isEqualTo(MetadataBackfillJobStatus.FAILED);
    }

    @Test
    void shouldQuarantineFailedDocumentDuringBackfill() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        List<MetadataQuarantineItem> quarantines = new ArrayList<>();
        KernelMetadataBackfillService service = serviceWithFailure(
                documents, jobs, "doc-1", quarantines::add);

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.failedDocuments()).isEqualTo(1);
        assertThat(quarantines).hasSize(1);
        MetadataQuarantineItem quarantine = quarantines.get(0);
        assertThat(quarantine.stage()).isEqualTo("EXTRACT");
        assertThat(quarantine.reasonCode()).isEqualTo("BACKFILL_DOCUMENT_FAILED");
        assertThat(quarantine.taskId()).isEqualTo(job.jobId());
        assertThat(quarantine.sourceSnapshot())
                .containsEntry("backfillJobId", job.jobId())
                .containsEntry("pipelineId", "pipe-1");
    }

    @Test
    void shouldHonorPauseAndResume() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin", Map.of()));
        service.pause(job.jobId(), "ops");

        MetadataBackfillRunResult paused = service.runNextBatch(job.jobId());
        assertThat(paused.status()).isEqualTo(MetadataBackfillJobStatus.PAUSED);
        assertThat(documents.runningDocuments).isEmpty();

        service.resume(job.jobId(), "ops");
        MetadataBackfillRunResult resumed = service.runNextBatch(job.jobId());

        assertThat(resumed.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(resumed.succeededDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly("doc-1");
    }

    @Test
    void shouldPageBackfillJobsForManagement() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillJobPage page = service.pageJobs(new MetadataBackfillJobQuery(
                "tenant-1", "kb-1", MetadataBackfillJobStatus.PENDING, 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataBackfillJobRecord::jobId).containsExactly(job.jobId());
    }

    @Test
    void shouldSkipAcceptedDocumentWithSameSchemaAndExtractorVersion() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        documents.add(document("doc-2", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        InMemoryExtractionResultRepository results = new InMemoryExtractionResultRepository();
        results.accept("tenant-1", "kb-1", "doc-1", 3, "extractor-v2");
        KernelMetadataBackfillService service = service(documents, jobs, results, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 3, "extractorVersion", "extractor-v2")));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(2);
        assertThat(result.skippedDocuments()).isEqualTo(1);
        assertThat(result.succeededDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly("doc-2");
    }

    @Test
    void shouldRerunAcceptedDocumentWhenOverwriteApprovedEnabled() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        InMemoryExtractionResultRepository results = new InMemoryExtractionResultRepository();
        results.accept("tenant-1", "kb-1", "doc-1", 3, "extractor-v2");
        KernelMetadataBackfillService service = service(documents, jobs, results, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 3, "extractorVersion", "extractor-v2", "overwriteApproved", true)));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.skippedDocuments()).isZero();
        assertThat(result.succeededDocuments()).isEqualTo(1);
        assertThat(result.checkpoint()).containsEntry("overwriteApproved", true);
        assertThat(documents.runningDocuments).containsExactly("doc-1");
    }

    @Test
    void shouldCreateSingleDocumentBackfillJobForReviewReExtract() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        documents.add(document("doc-2", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        List<Map<String, Object>> contextMetadata = new ArrayList<>();
        KernelIngestionEngine engine = mock(KernelIngestionEngine.class);
        when(engine.execute(any(PipelineDefinition.class), any(IngestionContext.class))).thenAnswer(invocation -> {
            IngestionContext context = invocation.getArgument(1);
            contextMetadata.add(Map.copyOf(context.getMetadata()));
            context.setMetadataValidationResult(new MetadataValidationResult(
                    MetadataValidationDecision.ACCEPT, List.of(), Map.of(), Map.of()));
            context.setChunks(List.of(new VectorChunk()));
            return context;
        });
        KernelMetadataBackfillService service = new KernelMetadataBackfillService(
                documents,
                new InMemoryObjectStorage(),
                pipelineRepository(),
                engine,
                jobs,
                MetadataExtractionResultRepositoryPort.noop(),
                MetadataQuarantinePort.noop(),
                null);

        String jobId = service.requestReExtract(new MetadataReviewReExtractRequest(
                "tenant-1", "kb-1", "doc-2", "review-1", "extractor-v2", "pipe-1", "auditor"));
        MetadataBackfillJobRecord created = jobs.findById(jobId).orElseThrow();
        MetadataBackfillRunResult result = service.runNextBatch(jobId);

        assertThat(created.checkpoint())
                .containsEntry("sourceReviewItemId", "review-1")
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("forceRerun", true)
                .containsEntry("overwriteApproved", true)
                .containsEntry("reExtract", true);
        assertThat(created.checkpoint().get("documentIds")).isEqualTo(List.of("doc-2"));
        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly("doc-2");
        assertThat(contextMetadata).hasSize(1);
        assertThat(contextMetadata.get(0))
                .containsEntry("sourceReviewItemId", "review-1")
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("reExtract", true);
    }

    @Test
    void shouldResumeCurrentPageAfterCheckpointDocument() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        documents.add(document("doc-2", true, "pipe-1"));
        documents.add(document("doc-3", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 3, "admin", Map.of()));
        Map<String, Object> checkpoint = new LinkedHashMap<>(job.checkpoint());
        checkpoint.put("currentPage", 1L);
        checkpoint.put("lastDocumentId", "doc-2");
        // 模拟进程在同一页处理完 doc-2 后中断，恢复时应从 doc-3 继续，而不是重跑 doc-1/doc-2。
        jobs.save(new MetadataBackfillJobRecord(
                job.jobId(), job.tenantId(), job.knowledgeBaseId(), job.pipelineId(),
                MetadataBackfillJobStatus.RUNNING, 1L, job.batchSize(), 2, 2,
                0, 0, 0, 0, checkpoint, List.of(), job.operator(),
                job.createTime(), job.updateTime()));

        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(3);
        assertThat(result.succeededDocuments()).isEqualTo(3);
        assertThat(documents.runningDocuments).containsExactly("doc-3");
    }

    @Test
    void shouldRerunDocumentWhenSchemaVersionChanges() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document("doc-1", true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        InMemoryExtractionResultRepository results = new InMemoryExtractionResultRepository();
        results.accept("tenant-1", "kb-1", "doc-1", 3, "extractor-v2");
        KernelMetadataBackfillService service = service(documents, jobs, results, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "kb-1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 4, "extractorVersion", "extractor-v2")));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.skippedDocuments()).isZero();
        assertThat(result.succeededDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly("doc-1");
    }

    private static KernelMetadataBackfillService service(InMemoryDocumentRepository documents,
                                                         InMemoryBackfillJobRepository jobs,
                                                         Map<String, MetadataValidationDecision> decisions) {
        return service(documents, jobs, MetadataExtractionResultRepositoryPort.noop(), decisions);
    }

    private static KernelMetadataBackfillService service(InMemoryDocumentRepository documents,
                                                         InMemoryBackfillJobRepository jobs,
                                                         MetadataExtractionResultRepositoryPort results,
                                                         Map<String, MetadataValidationDecision> decisions) {
        KernelIngestionEngine engine = mock(KernelIngestionEngine.class);
        when(engine.execute(any(PipelineDefinition.class), any(IngestionContext.class))).thenAnswer(invocation -> {
            IngestionContext context = invocation.getArgument(1);
            MetadataValidationDecision decision = decisions.getOrDefault(
                    context.getTaskId(), MetadataValidationDecision.ACCEPT);
            context.setMetadataValidationResult(new MetadataValidationResult(decision, List.of(), Map.of(), Map.of()));
            context.setChunks(List.of(new VectorChunk()));
            return context;
        });
        return new KernelMetadataBackfillService(
                documents,
                new InMemoryObjectStorage(),
                pipelineRepository(),
                engine,
                jobs,
                results,
                null);
    }

    private static KernelMetadataBackfillService serviceWithFailure(InMemoryDocumentRepository documents,
                                                                    InMemoryBackfillJobRepository jobs,
                                                                    String failedDocId) {
        return serviceWithFailure(documents, jobs, failedDocId, MetadataQuarantinePort.noop());
    }

    private static KernelMetadataBackfillService serviceWithFailure(InMemoryDocumentRepository documents,
                                                                    InMemoryBackfillJobRepository jobs,
                                                                    String failedDocId,
                                                                    MetadataQuarantinePort quarantinePort) {
        KernelIngestionEngine engine = mock(KernelIngestionEngine.class);
        when(engine.execute(any(PipelineDefinition.class), any(IngestionContext.class))).thenAnswer(invocation -> {
            IngestionContext context = invocation.getArgument(1);
            if (failedDocId.equals(context.getTaskId())) {
                throw new IllegalStateException("boom");
            }
            context.setMetadataValidationResult(new MetadataValidationResult(
                    MetadataValidationDecision.ACCEPT, List.of(), Map.of(), Map.of()));
            context.setChunks(List.of(new VectorChunk()));
            return context;
        });
        return new KernelMetadataBackfillService(
                documents,
                new InMemoryObjectStorage(),
                pipelineRepository(),
                engine,
                jobs,
                MetadataExtractionResultRepositoryPort.noop(),
                quarantinePort,
                null);
    }

    private static PipelineDefinitionRepositoryPort pipelineRepository() {
        return pipelineId -> Optional.of(PipelineDefinition.builder()
                .id(pipelineId)
                .name("metadata-backfill")
                .nodes(List.of())
                .build());
    }

    private static KnowledgeDocumentDetail document(String docId, boolean enabled, String pipelineId) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(docId);
        detail.setKbId("kb-1");
        detail.setDocName(docId + ".txt");
        detail.setCollectionName("collection-1");
        detail.setEnabled(enabled);
        detail.setFileUrl("memory://" + docId);
        detail.setFileType("text/plain");
        detail.setPipelineId(pipelineId);
        return detail;
    }

    private static final class InMemoryBackfillJobRepository implements MetadataBackfillJobRepositoryPort {

        private final Map<String, MetadataBackfillJobRecord> records = new LinkedHashMap<>();

        @Override
        public String create(MetadataBackfillJobRecord job) {
            records.put(job.jobId(), job);
            return job.jobId();
        }

        @Override
        public Optional<MetadataBackfillJobRecord> findById(String jobId) {
            return Optional.ofNullable(records.get(jobId));
        }

        @Override
        public MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
            List<MetadataBackfillJobRecord> matched = records.values().stream()
                    .filter(record -> query.tenantId().isBlank() || query.tenantId().equals(record.tenantId()))
                    .filter(record -> query.knowledgeBaseId().isBlank()
                            || query.knowledgeBaseId().equals(record.knowledgeBaseId()))
                    .filter(record -> query.status() == null || query.status().equals(record.status()))
                    .toList();
            int from = (int) Math.min(query.offset(), matched.size());
            int to = (int) Math.min(from + query.size(), matched.size());
            long pages = matched.isEmpty() ? 0 : (matched.size() + query.size() - 1) / query.size();
            return new MetadataBackfillJobPage(matched.subList(from, to), matched.size(), query.size(),
                    query.current(), pages);
        }

        @Override
        public void save(MetadataBackfillJobRecord job) {
            records.put(job.jobId(), job);
        }
    }

    private static final class InMemoryExtractionResultRepository implements MetadataExtractionResultRepositoryPort {

        private final List<MetadataExtractionRecord> records = new ArrayList<>();

        void accept(String tenantId, String kbId, String docId, int schemaVersion, String extractorVersion) {
            records.add(new MetadataExtractionRecord(tenantId, kbId, docId, docId, schemaVersion,
                    extractorVersion, MetadataValidationDecision.ACCEPT, Map.of(), Map.of(), List.of(), List.of()));
        }

        @Override
        public void save(MetadataExtractionRecord record) {
            records.add(record);
        }

        @Override
        public boolean hasAcceptedResult(String tenantId, String knowledgeBaseId, String documentId,
                                         int schemaVersion, String extractorVersion) {
            return records.stream().anyMatch(record -> tenantId.equals(record.tenantId())
                    && knowledgeBaseId.equals(record.knowledgeBaseId())
                    && documentId.equals(record.documentId())
                    && schemaVersion == record.schemaVersion()
                    && extractorVersion.equals(record.extractorVersion())
                    && MetadataValidationDecision.ACCEPT.equals(record.status()));
        }
    }

    private static final class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final Map<String, KnowledgeDocumentDetail> documents = new LinkedHashMap<>();
        private final List<String> runningDocuments = new ArrayList<>();
        private final List<String> successDocuments = new ArrayList<>();
        private final List<String> failedDocuments = new ArrayList<>();

        void add(KnowledgeDocumentDetail document) {
            documents.put(document.getId(), document);
        }

        @Override
        public KnowledgeDocumentPage page(String kbId, long current, long size, String status, String keyword) {
            List<KnowledgeDocumentDetail> matched = documents.values().stream()
                    .filter(document -> kbId.equals(document.getKbId()))
                    .toList();
            int from = (int) Math.min((current - 1) * size, matched.size());
            int to = (int) Math.min(from + size, matched.size());
            long pages = (long) Math.ceil(matched.size() / (double) size);
            return new KnowledgeDocumentPage(matched.subList(from, to), matched.size(), size, current, pages);
        }

        @Override
        public boolean markRunning(String docId, String operator) {
            runningDocuments.add(docId);
            return true;
        }

        @Override
        public void markSuccess(String docId, int chunkCount, String operator) {
            successDocuments.add(docId);
        }

        @Override
        public void markFailed(String docId, String operator, String errorMessage) {
            failedDocuments.add(docId);
        }

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(String docId) {
            return Optional.empty();
        }
    }

    private static final class FailingPageDocumentRepository implements KnowledgeDocumentRepositoryPort {

        @Override
        public KnowledgeDocumentPage page(String kbId, long current, long size, String status, String keyword) {
            throw new IllegalStateException("page unavailable");
        }

        @Override
        public boolean markRunning(String docId, String operator) {
            return false;
        }

        @Override
        public void markSuccess(String docId, int chunkCount, String operator) {
        }

        @Override
        public void markFailed(String docId, String operator, String errorMessage) {
        }

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(String docId) {
            return Optional.empty();
        }
    }

    private static final class InMemoryObjectStorage implements ObjectStoragePort {

        @Override
        public StoredObject upload(String bucketName, InputStream content, long size,
                                   String originalFilename, String contentType) {
            return null;
        }

        @Override
        public StoredObject reliableUpload(String bucketName, InputStream content, long size,
                                           String originalFilename, String contentType) {
            return null;
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream("metadata backfill".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }
}
