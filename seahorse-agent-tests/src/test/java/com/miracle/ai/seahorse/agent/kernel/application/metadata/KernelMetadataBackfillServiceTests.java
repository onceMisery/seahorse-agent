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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchemaMissingException;
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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillOperationsOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KernelMetadataBackfillServiceTests {

    @Test
    void shouldRunPagedBackfillAndUpdateCheckpoint() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        documents.add(document(2L, true, "pipe-1"));
        documents.add(document(3L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of(
                "1", MetadataValidationDecision.ACCEPT,
                "2", MetadataValidationDecision.REVIEW_REQUIRED,
                "3", MetadataValidationDecision.QUARANTINE));

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 2, "admin", Map.of()));
        MetadataBackfillRunResult firstBatch = service.runNextBatch(job.jobId());

        assertThat(firstBatch.status()).isEqualTo(MetadataBackfillJobStatus.PENDING);
        assertThat(firstBatch.currentPage()).isEqualTo(2);
        assertThat(firstBatch.processedDocuments()).isEqualTo(2);
        assertThat(firstBatch.reviewDocuments()).isEqualTo(1);
        assertThat(firstBatch.checkpoint()).containsEntry("currentPage", 2L);
        assertThat(documents.successDocuments).containsExactly(1L, 2L);

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
        documents.add(document(1L, true, "pipe-1"));
        documents.add(document(2L, true, "pipe-1"));
        documents.add(document(3L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = serviceWithFailure(documents, jobs, "2");

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(3);
        assertThat(result.succeededDocuments()).isEqualTo(2);
        assertThat(result.failedDocuments()).isEqualTo(1);
        assertThat(result.failures()).singleElement().asString().contains("2").contains("boom");
        assertThat(documents.failedDocuments).containsExactly(2L);
        assertThat(documents.successDocuments).containsExactly(1L, 3L);
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
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
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
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        List<MetadataQuarantineItem> quarantines = new ArrayList<>();
        KernelMetadataBackfillService service = serviceWithFailure(
                documents, jobs, "1", quarantines::add);

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
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
    void shouldPauseBackfillWhenMetadataSchemaMissing() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        List<MetadataQuarantineItem> quarantines = new ArrayList<>();
        KernelIngestionEngine engine = mock(KernelIngestionEngine.class);
        when(engine.execute(any(PipelineDefinition.class), any(IngestionContext.class))).thenAnswer(invocation -> {
            IngestionContext context = invocation.getArgument(1);
            context.setError(new MetadataSchemaMissingException("tenant-1", "1"));
            return context;
        });
        KernelMetadataBackfillService service = new KernelMetadataBackfillService(
                documents,
                new InMemoryObjectStorage(),
                pipelineRepository(),
                engine,
                jobs,
                MetadataExtractionResultRepositoryPort.noop(),
                quarantines::add,
                null);

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.PAUSED);
        assertThat(result.processedDocuments()).isZero();
        assertThat(result.failures()).singleElement().asString().contains("metadata schema missing");
        assertThat(result.checkpoint()).containsEntry("pauseReason", "SCHEMA_MISSING");
        assertThat(documents.failedDocuments).containsExactly(1L);
        assertThat(quarantines).isEmpty();
    }

    @Test
    void shouldHonorPauseAndResume() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
        service.pause(job.jobId(), "ops");

        MetadataBackfillRunResult paused = service.runNextBatch(job.jobId());
        assertThat(paused.status()).isEqualTo(MetadataBackfillJobStatus.PAUSED);
        assertThat(documents.runningDocuments).isEmpty();

        service.resume(job.jobId(), "ops");
        MetadataBackfillRunResult resumed = service.runNextBatch(job.jobId());

        assertThat(resumed.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(resumed.succeededDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly(1L);
    }

    @Test
    void shouldRecordBackfillLifecycleAndBatchObservationEvents() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMetadataBackfillService service = new KernelMetadataBackfillService(
                documents,
                new InMemoryObjectStorage(),
                pipelineRepository(),
                acceptingEngine(),
                jobs,
                MetadataExtractionResultRepositoryPort.noop(),
                MetadataQuarantinePort.noop(),
                observationPort);

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 3, "extractorVersion", "extractor-v2", "reExtract", true)));
        service.pause(job.jobId(), "ops");
        service.resume(job.jobId(), "ops");
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());
        MetadataBackfillJobRecord cancelledJob = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
        service.cancel(cancelledJob.jobId(), "ops");

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(observationPort.events)
                .extracting(ObservationEvent::name)
                .contains(
                        "metadata.backfill.job.created",
                        "metadata.backfill.job.paused",
                        "metadata.backfill.job.resumed",
                        "metadata.backfill.batch.success",
                        "metadata.backfill.job.cancelled");
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("metadata.backfill.job.paused"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("operation", "PAUSE")
                        .containsEntry("previousStatus", "PENDING")
                        .containsEntry("status", "PAUSED"));
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("metadata.backfill.job.resumed"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("operation", "RESUME")
                        .containsEntry("previousStatus", "PAUSED")
                        .containsEntry("status", "PENDING"));
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("metadata.backfill.batch.success"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("tenantId", "tenant-1")
                        .containsEntry("knowledgeBaseId", "1")
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("processedDocuments", "1")
                        .containsEntry("succeededDocuments", "1")
                        .containsEntry("schemaVersion", "3")
                        .containsEntry("extractorVersion", "extractor-v2")
                        .containsEntry("reExtract", "true"));
    }

    @Test
    void shouldPreserveSchemaCompensationMetadataInCheckpointAndObservation() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMetadataBackfillService service = new KernelMetadataBackfillService(
                documents,
                new InMemoryObjectStorage(),
                pipelineRepository(),
                acceptingEngine(),
                jobs,
                MetadataExtractionResultRepositoryPort.noop(),
                MetadataQuarantinePort.noop(),
                observationPort);

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "", 10, "schema-ops",
                Map.of(
                        "schemaVersion", 6,
                        "schemaCompensation", true,
                        "schemaCompensationReason", "SCHEMA_CHANGE",
                        "schemaTriggerAction", "UPDATE",
                        "schemaTriggerFieldKey", "department",
                        "forceRerun", true,
                        "overwriteApproved", true)));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());
        MetadataBackfillJobRecord stored = jobs.findById(job.jobId()).orElseThrow();

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(stored.checkpoint())
                .containsEntry("schemaCompensation", true)
                .containsEntry("schemaCompensationReason", "SCHEMA_CHANGE")
                .containsEntry("schemaTriggerAction", "UPDATE")
                .containsEntry("schemaTriggerFieldKey", "department");
        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("metadata.backfill.batch.success"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("schemaCompensation", "true")
                        .containsEntry("schemaCompensationReason", "SCHEMA_CHANGE")
                        .containsEntry("schemaTriggerAction", "UPDATE")
                        .containsEntry("schemaTriggerFieldSpecified", "true")
                        .doesNotContainKey("schemaTriggerFieldKey"));
    }

    @Test
    void shouldPageBackfillJobsForManagement() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin", Map.of()));
        MetadataBackfillJobPage page = service.pageJobs(new MetadataBackfillJobQuery(
                "tenant-1", "1", MetadataBackfillJobStatus.PENDING, 1, 10));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(MetadataBackfillJobRecord::jobId).containsExactly(job.jobId());
    }

    @Test
    void shouldDelegateBackfillOperationsOverviewToRepository() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        jobs.overview = new MetadataBackfillOperationsOverview(
                "tenant-1", "1",
                2, 8, 6, 1, 1, 2, 1,
                3, 1, 2, 1, 1, 2,
                List.of(), List.of(), List.of(), null, null, Instant.EPOCH);
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillOperationsOverview overview = service.overview("tenant-1", "1");

        assertThat(overview.totalJobs()).isEqualTo(2);
        assertThat(overview.pendingReviewItems()).isEqualTo(3);
        assertThat(overview.pendingSchemaCompensationDocuments()).isEqualTo(2);
    }

    @Test
    void shouldPageBackfillJobsByGovernanceFilters() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());
        Instant now = Instant.parse("2026-05-15T00:00:00Z");
        jobs.create(new MetadataBackfillJobRecord(
                "job-reextract", "tenant-1", 1L, "pipe-1", MetadataBackfillJobStatus.PAUSED,
                1, 50, 1, 0, 1, 0, 0, 0,
                Map.of("documentIds", List.of("9"), "pauseReason", "SCHEMA_MISSING", "reExtract", true),
                List.of("9: metadata schema missing"), "auditor", now, now));
        jobs.create(new MetadataBackfillJobRecord(
                "job-normal", "tenant-1", 1L, "pipe-1", MetadataBackfillJobStatus.COMPLETED,
                1, 50, 1, 1, 0, 0, 0, 0, Map.of("currentPage", 1),
                List.of(), "admin", now, now));

        MetadataBackfillJobPage page = service.pageJobs(new MetadataBackfillJobQuery(
                "tenant-1", "1", null, "pipe-1", "auditor", "9",
                "SCHEMA_MISSING", "schema", true, true, 1, 10));

        assertThat(page.records()).extracting(MetadataBackfillJobRecord::jobId).containsExactly("job-reextract");
    }

    @Test
    void shouldSkipAcceptedDocumentWithSameSchemaAndExtractorVersion() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        documents.add(document(2L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        InMemoryExtractionResultRepository results = new InMemoryExtractionResultRepository();
        results.accept("tenant-1", 1L, 1L, 3, "extractor-v2");
        KernelMetadataBackfillService service = service(documents, jobs, results, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 3, "extractorVersion", "extractor-v2")));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(2);
        assertThat(result.skippedDocuments()).isEqualTo(1);
        assertThat(result.succeededDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly(2L);
    }

    @Test
    void shouldRerunAcceptedDocumentWhenOverwriteApprovedEnabled() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        InMemoryExtractionResultRepository results = new InMemoryExtractionResultRepository();
        results.accept("tenant-1", 1L, 1L, 3, "extractor-v2");
        KernelMetadataBackfillService service = service(documents, jobs, results, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 3, "extractorVersion", "extractor-v2", "overwriteApproved", true)));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.skippedDocuments()).isZero();
        assertThat(result.succeededDocuments()).isEqualTo(1);
        assertThat(result.checkpoint()).containsEntry("overwriteApproved", true);
        assertThat(documents.runningDocuments).containsExactly(1L);
    }

    @Test
    void shouldPropagateLlmExtractionVersionsToBackfillContext() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
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

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin",
                Map.of(
                        "extractorVersion", "extractor-v2",
                        "llmExtractorVersion", "llm-v3",
                        "llmPromptVersion", "prompt-v3")));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(job.checkpoint())
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("llmExtractorVersion", "llm-v3")
                .containsEntry("llmPromptVersion", "prompt-v3");
        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(contextMetadata).hasSize(1);
        assertThat(contextMetadata.get(0))
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("llmExtractorVersion", "llm-v3")
                .containsEntry("llmPromptVersion", "prompt-v3");
    }

    @Test
    void shouldCreateSingleDocumentBackfillJobForReviewReExtract() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        documents.add(document(2L, true, "pipe-1"));
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
                "tenant-1", 1L, 2L, "review-1", "extractor-v2", "pipe-1",
                "llm-v2", "prompt-v2", "auditor"));
        MetadataBackfillJobRecord created = jobs.findById(jobId).orElseThrow();
        MetadataBackfillRunResult result = service.runNextBatch(jobId);

        assertThat(created.checkpoint())
                .containsEntry("sourceReviewItemId", "review-1")
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("llmExtractorVersion", "llm-v2")
                .containsEntry("llmPromptVersion", "prompt-v2")
                .containsEntry("forceRerun", true)
                .containsEntry("overwriteApproved", true)
                .containsEntry("reExtract", true);
        assertThat(created.checkpoint().get("documentIds")).isEqualTo(List.of("2"));
        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly(2L);
        assertThat(contextMetadata).hasSize(1);
        assertThat(contextMetadata.get(0))
                .containsEntry("sourceReviewItemId", "review-1")
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("llmExtractorVersion", "llm-v2")
                .containsEntry("llmPromptVersion", "prompt-v2")
                .containsEntry("reExtract", true);
    }

    @Test
    void shouldResumeCurrentPageAfterCheckpointDocument() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        documents.add(document(2L, true, "pipe-1"));
        documents.add(document(3L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        KernelMetadataBackfillService service = service(documents, jobs, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 3, "admin", Map.of()));
        Map<String, Object> checkpoint = new LinkedHashMap<>(job.checkpoint());
        checkpoint.put("currentPage", 1L);
        checkpoint.put("lastDocumentId", "2");
        jobs.save(new MetadataBackfillJobRecord(
                job.jobId(), job.tenantId(), job.knowledgeBaseId(), job.pipelineId(),
                MetadataBackfillJobStatus.RUNNING, 1L, job.batchSize(), 2, 2,
                0, 0, 0, 0, checkpoint, List.of(), job.operator(),
                job.createTime(), job.updateTime()));

        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.status()).isEqualTo(MetadataBackfillJobStatus.COMPLETED);
        assertThat(result.processedDocuments()).isEqualTo(3);
        assertThat(result.succeededDocuments()).isEqualTo(3);
        assertThat(documents.runningDocuments).containsExactly(3L);
    }

    @Test
    void shouldRerunDocumentWhenSchemaVersionChanges() {
        InMemoryDocumentRepository documents = new InMemoryDocumentRepository();
        documents.add(document(1L, true, "pipe-1"));
        InMemoryBackfillJobRepository jobs = new InMemoryBackfillJobRepository();
        InMemoryExtractionResultRepository results = new InMemoryExtractionResultRepository();
        results.accept("tenant-1", 1L, 1L, 3, "extractor-v2");
        KernelMetadataBackfillService service = service(documents, jobs, results, Map.of());

        MetadataBackfillJobRecord job = service.createJob(new MetadataBackfillCommand(
                "tenant-1", "1", "pipe-1", 10, "admin",
                Map.of("schemaVersion", 4, "extractorVersion", "extractor-v2")));
        MetadataBackfillRunResult result = service.runNextBatch(job.jobId());

        assertThat(result.skippedDocuments()).isZero();
        assertThat(result.succeededDocuments()).isEqualTo(1);
        assertThat(documents.runningDocuments).containsExactly(1L);
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

    private static KernelIngestionEngine acceptingEngine() {
        KernelIngestionEngine engine = mock(KernelIngestionEngine.class);
        when(engine.execute(any(PipelineDefinition.class), any(IngestionContext.class))).thenAnswer(invocation -> {
            IngestionContext context = invocation.getArgument(1);
            context.setMetadataValidationResult(new MetadataValidationResult(
                    MetadataValidationDecision.ACCEPT, List.of(), Map.of(), Map.of()));
            context.setChunks(List.of(new VectorChunk()));
            return context;
        });
        return engine;
    }

    private static PipelineDefinitionRepositoryPort pipelineRepository() {
        return pipelineId -> Optional.of(PipelineDefinition.builder()
                .id(pipelineId)
                .name("metadata-backfill")
                .nodes(List.of())
                .build());
    }

    private static KnowledgeDocumentDetail document(Long docId, boolean enabled, String pipelineId) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(docId);
        detail.setKbId(1L);
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
        private MetadataBackfillOperationsOverview overview;

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
                    .filter(record -> query.knowledgeBaseId() == null
                            || query.knowledgeBaseId().equals(String.valueOf(record.knowledgeBaseId())))
                    .filter(record -> query.status() == null || query.status().equals(record.status()))
                    .filter(record -> query.pipelineId().isBlank() || query.pipelineId().equals(record.pipelineId()))
                    .filter(record -> query.operator().isBlank() || query.operator().equals(record.operator()))
                    .filter(record -> query.documentId().isBlank()
                            || checkpointText(record).contains(query.documentId()))
                    .filter(record -> query.pauseReason().isBlank()
                            || query.pauseReason().equals(record.checkpoint().get("pauseReason")))
                    .filter(record -> query.failureKeyword().isBlank()
                            || failuresText(record).contains(query.failureKeyword()))
                    .filter(record -> query.hasFailures() == null
                            || query.hasFailures().equals(hasFailures(record)))
                    .filter(record -> query.reExtract() == null
                            || query.reExtract().equals(Boolean.TRUE.equals(record.checkpoint().get("reExtract"))))
                    .toList();
            int from = (int) Math.min(query.offset(), matched.size());
            int to = (int) Math.min(from + query.size(), matched.size());
            long pages = matched.isEmpty() ? 0 : (matched.size() + query.size() - 1) / query.size();
            return new MetadataBackfillJobPage(matched.subList(from, to), matched.size(), query.size(),
                    query.current(), pages);
        }

        @Override
        public MetadataBackfillOperationsOverview overview(String tenantId, String knowledgeBaseId) {
            if (overview != null) {
                return overview;
            }
            return MetadataBackfillOperationsOverview.empty(tenantId, knowledgeBaseId);
        }

        @Override
        public void save(MetadataBackfillJobRecord job) {
            records.put(job.jobId(), job);
        }

        private String checkpointText(MetadataBackfillJobRecord record) {
            return Objects.toString(record.checkpoint(), "");
        }

        private String failuresText(MetadataBackfillJobRecord record) {
            return Objects.toString(record.failures(), "");
        }

        private boolean hasFailures(MetadataBackfillJobRecord record) {
            return MetadataBackfillJobStatus.FAILED.equals(record.status())
                    || record.failedDocuments() > 0
                    || !record.failures().isEmpty();
        }
    }

    private static final class InMemoryExtractionResultRepository implements MetadataExtractionResultRepositoryPort {

        private final List<MetadataExtractionRecord> records = new ArrayList<>();

        void accept(String tenantId, Long kbId, Long docId, int schemaVersion, String extractorVersion) {
            records.add(new MetadataExtractionRecord(tenantId, String.valueOf(kbId), String.valueOf(docId),
                    String.valueOf(docId), schemaVersion,
                    extractorVersion, MetadataValidationDecision.ACCEPT, Map.of(), Map.of(), List.of(), List.of()));
        }

        @Override
        public void save(MetadataExtractionRecord record) {
            records.add(record);
        }

        @Override
        public boolean hasAcceptedResult(String tenantId, Long knowledgeBaseId, Long documentId,
                                         int schemaVersion, String extractorVersion) {
            return records.stream().anyMatch(record -> tenantId.equals(record.tenantId())
                    && String.valueOf(knowledgeBaseId).equals(record.knowledgeBaseId())
                    && String.valueOf(documentId).equals(record.documentId())
                    && schemaVersion == record.schemaVersion()
                    && extractorVersion.equals(record.extractorVersion())
                    && MetadataValidationDecision.ACCEPT.equals(record.status()));
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }

    private static final class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final Map<Long, KnowledgeDocumentDetail> documents = new LinkedHashMap<>();
        private final List<Long> runningDocuments = new ArrayList<>();
        private final List<Long> successDocuments = new ArrayList<>();
        private final List<Long> failedDocuments = new ArrayList<>();

        void add(KnowledgeDocumentDetail document) {
            documents.put(document.getId(), document);
        }

        @Override
        public KnowledgeDocumentPage page(Long kbId, long current, long size, String status, String keyword) {
            List<KnowledgeDocumentDetail> matched = documents.values().stream()
                    .filter(document -> kbId.equals(document.getKbId()))
                    .toList();
            int from = (int) Math.min((current - 1) * size, matched.size());
            int to = (int) Math.min(from + size, matched.size());
            long pages = (long) Math.ceil(matched.size() / (double) size);
            return new KnowledgeDocumentPage(matched.subList(from, to), matched.size(), size, current, pages);
        }

        @Override
        public boolean markRunning(Long docId, String operator) {
            runningDocuments.add(docId);
            return true;
        }

        @Override
        public void markSuccess(Long docId, int chunkCount, String operator) {
            successDocuments.add(docId);
        }

        @Override
        public void markFailed(Long docId, String operator, String errorMessage) {
            failedDocuments.add(docId);
        }

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(Long docId) {
            return Optional.empty();
        }
    }

    private static final class FailingPageDocumentRepository implements KnowledgeDocumentRepositoryPort {

        @Override
        public KnowledgeDocumentPage page(Long kbId, long current, long size, String status, String keyword) {
            throw new IllegalStateException("page unavailable");
        }

        @Override
        public boolean markRunning(Long docId, String operator) {
            return false;
        }

        @Override
        public void markSuccess(Long docId, int chunkCount, String operator) {
        }

        @Override
        public void markFailed(Long docId, String operator, String errorMessage) {
        }

        @Override
        public KnowledgeDocumentRecord createPendingDocument(CreateKnowledgeDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeDocumentRecord> findById(Long docId) {
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
