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
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 元数据历史回填 kernel 编排服务。
 *
 * <p>回填会重新执行文档入库流水线，让 MetadataExtractor/Normalizer/Validator 产生统一的
 * 抽取结果、Review 和 Quarantine 记录；服务自身只负责分页、断点、幂等重跑和失败隔离。
 */
public class KernelMetadataBackfillService implements MetadataBackfillInboundPort {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 500;
    private static final String DEFAULT_OPERATOR = "metadata-backfill";
    private static final String OBSERVATION_BACKFILL = "metadata.backfill.batch";
    private static final String EVENT_BACKFILL_SUCCESS = "metadata.backfill.batch.success";
    private static final String EVENT_BACKFILL_FAILURE = "metadata.backfill.batch.failure";

    private final KnowledgeDocumentRepositoryPort documentRepositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;
    private final KernelIngestionEngine ingestionEngine;
    private final MetadataBackfillJobRepositoryPort jobRepositoryPort;
    private final MetadataExtractionResultRepositoryPort extractionResultRepositoryPort;
    private final ObservationPort observationPort;

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort) {
        this(documentRepositoryPort, objectStoragePort, pipelineRepositoryPort, ingestionEngine, jobRepositoryPort,
                MetadataExtractionResultRepositoryPort.noop(), null);
    }

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort,
                                         ObservationPort observationPort) {
        this(documentRepositoryPort, objectStoragePort, pipelineRepositoryPort, ingestionEngine, jobRepositoryPort,
                MetadataExtractionResultRepositoryPort.noop(), observationPort);
    }

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort,
                                         MetadataExtractionResultRepositoryPort extractionResultRepositoryPort,
                                         ObservationPort observationPort) {
        this.documentRepositoryPort = Objects.requireNonNull(documentRepositoryPort,
                "documentRepositoryPort must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
        this.pipelineRepositoryPort = Objects.requireNonNull(pipelineRepositoryPort,
                "pipelineRepositoryPort must not be null");
        this.ingestionEngine = Objects.requireNonNull(ingestionEngine, "ingestionEngine must not be null");
        this.jobRepositoryPort = Objects.requireNonNull(jobRepositoryPort, "jobRepositoryPort must not be null");
        this.extractionResultRepositoryPort = Objects.requireNonNullElseGet(extractionResultRepositoryPort,
                MetadataExtractionResultRepositoryPort::noop);
        this.observationPort = observationPort;
    }

    @Override
    public MetadataBackfillJobRecord createJob(MetadataBackfillCommand command) {
        MetadataBackfillCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String kbId = requireText(safeCommand.knowledgeBaseId(), "knowledgeBaseId");
        int batchSize = normalizeBatchSize(safeCommand.batchSize());
        Instant now = Instant.now();
        MetadataBackfillJobRecord job = new MetadataBackfillJobRecord(
                UUID.randomUUID().toString(),
                safeCommand.tenantId(),
                kbId,
                safeCommand.pipelineId(),
                MetadataBackfillJobStatus.PENDING,
                1L,
                batchSize,
                0,
                0,
                0,
                0,
                0,
                0,
                initialCheckpoint(safeCommand.metadata()),
                List.of(),
                defaultText(safeCommand.operator(), DEFAULT_OPERATOR),
                now,
                now);
        String jobId = jobRepositoryPort.create(job);
        return jobRepositoryPort.findById(jobId).orElse(job);
    }

    @Override
    public MetadataBackfillRunResult runNextBatch(String jobId) {
        MetadataBackfillJobRecord job = requireJob(jobId);
        if (terminalOrBlocked(job.status())) {
            return toResult(job);
        }
        ObservationScope observationScope = startObservation(job);
        MetadataBackfillJobRecord running = updateStatus(job, MetadataBackfillJobStatus.RUNNING);
        jobRepositoryPort.save(running);
        try {
            MetadataBackfillJobRecord result = executeBatch(running);
            recordBackfillResult(observationScope, result);
            return toResult(result);
        } finally {
            closeObservation(observationScope);
        }
    }

    @Override
    public MetadataBackfillJobRecord getJob(String jobId) {
        return requireJob(jobId);
    }

    @Override
    public MetadataBackfillJobPage pageJobs(MetadataBackfillJobQuery query) {
        MetadataBackfillJobQuery safeQuery = query == null
                ? new MetadataBackfillJobQuery("", "", null, 1, 20)
                : query;
        return jobRepositoryPort.page(safeQuery);
    }

    @Override
    public MetadataBackfillJobRecord pause(String jobId, String operator) {
        MetadataBackfillJobRecord job = requireJob(jobId);
        if (MetadataBackfillJobStatus.COMPLETED.equals(job.status())
                || MetadataBackfillJobStatus.CANCELLED.equals(job.status())) {
            return job;
        }
        MetadataBackfillJobRecord paused = updateStatus(job, MetadataBackfillJobStatus.PAUSED, operator);
        jobRepositoryPort.save(paused);
        return paused;
    }

    @Override
    public MetadataBackfillJobRecord resume(String jobId, String operator) {
        MetadataBackfillJobRecord job = requireJob(jobId);
        if (!MetadataBackfillJobStatus.PAUSED.equals(job.status())) {
            return job;
        }
        MetadataBackfillJobRecord resumed = updateStatus(job, MetadataBackfillJobStatus.PENDING, operator);
        jobRepositoryPort.save(resumed);
        return resumed;
    }

    @Override
    public MetadataBackfillJobRecord cancel(String jobId, String operator) {
        MetadataBackfillJobRecord job = requireJob(jobId);
        if (MetadataBackfillJobStatus.COMPLETED.equals(job.status())) {
            return job;
        }
        MetadataBackfillJobRecord cancelled = updateStatus(job, MetadataBackfillJobStatus.CANCELLED, operator);
        jobRepositoryPort.save(cancelled);
        return cancelled;
    }

    private MetadataBackfillJobRecord executeBatch(MetadataBackfillJobRecord job) {
        BackfillAccumulator accumulator = new BackfillAccumulator(job);
        KnowledgeDocumentPage page = documentRepositoryPort.page(
                job.knowledgeBaseId(), job.currentPage(), job.batchSize(), null, null);
        if (page.records().isEmpty()) {
            MetadataBackfillJobRecord completed = accumulator.toRecord(MetadataBackfillJobStatus.COMPLETED,
                    job.currentPage(), checkpoint(job.currentPage(), "", job.checkpoint()));
            jobRepositoryPort.save(completed);
            return completed;
        }
        for (KnowledgeDocumentDetail document : page.records()) {
            MetadataBackfillJobRecord latest = jobRepositoryPort.findById(job.jobId()).orElse(job);
            if (MetadataBackfillJobStatus.PAUSED.equals(latest.status())
                    || MetadataBackfillJobStatus.CANCELLED.equals(latest.status())) {
                return latest;
            }
            DocumentOutcome outcome = processDocument(job, document);
            accumulator.apply(document, outcome);
            // 每处理一个文档就推进断点，降低批量回填中断后的重复扫描范围。
            jobRepositoryPort.save(accumulator.toRecord(MetadataBackfillJobStatus.RUNNING, job.currentPage(),
                    checkpoint(job.currentPage(), document == null ? "" : document.getId(), job.checkpoint())));
        }
        long nextPage = job.currentPage() + 1;
        boolean completed = page.pages() > 0
                ? job.currentPage() >= page.pages()
                : page.records().size() < job.batchSize();
        MetadataBackfillJobStatus nextStatus = completed
                ? MetadataBackfillJobStatus.COMPLETED
                : MetadataBackfillJobStatus.PENDING;
        MetadataBackfillJobRecord result = accumulator.toRecord(nextStatus, completed ? job.currentPage() : nextPage,
                checkpoint(completed ? job.currentPage() : nextPage, "", job.checkpoint()));
        jobRepositoryPort.save(result);
        return result;
    }

    private DocumentOutcome processDocument(MetadataBackfillJobRecord job, KnowledgeDocumentDetail document) {
        if (document == null || !hasText(document.getId())) {
            return DocumentOutcome.skipped("document missing");
        }
        if (Boolean.FALSE.equals(document.getEnabled())) {
            return DocumentOutcome.skipped("document disabled");
        }
        // 同一 Schema 与抽取器版本已经有可信结果时直接跳过，避免历史回填重复污染复核队列。
        if (hasAcceptedResult(job, document)) {
            return DocumentOutcome.skipped("accepted metadata exists");
        }
        String pipelineId = defaultText(job.pipelineId(), document.getPipelineId());
        if (!hasText(pipelineId)) {
            return DocumentOutcome.failed("pipelineId missing");
        }
        if (!documentRepositoryPort.markRunning(document.getId(), job.operator())) {
            return DocumentOutcome.skipped("document is running");
        }
        try (InputStream inputStream = objectStoragePort.openStream(requireText(document.getFileUrl(), "fileUrl"))) {
            IngestionContext context = buildContext(job, document, inputStream.readAllBytes());
            IngestionContext result = ingestionEngine.execute(requirePipeline(pipelineId), context);
            if (result.getError() != null) {
                throw new IllegalStateException(result.getError().getMessage(), result.getError());
            }
            int chunkCount = result.getChunks() == null ? 0 : result.getChunks().size();
            documentRepositoryPort.markSuccess(document.getId(), chunkCount, job.operator());
            return DocumentOutcome.success(decision(result));
        } catch (Exception ex) {
            documentRepositoryPort.markFailed(document.getId(), job.operator(), ex.getMessage());
            return DocumentOutcome.failed(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private PipelineDefinition requirePipeline(String pipelineId) {
        String safePipelineId = requireText(pipelineId, "pipelineId");
        return pipelineRepositoryPort.findById(safePipelineId)
                .orElseThrow(() -> new IllegalArgumentException("pipeline not found: " + safePipelineId));
    }

    private IngestionContext buildContext(MetadataBackfillJobRecord job,
                                          KnowledgeDocumentDetail document,
                                          byte[] fileBytes) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", job.tenantId());
        metadata.put("kbId", document.getKbId());
        metadata.put("docId", document.getId());
        metadata.put("fileName", document.getDocName());
        metadata.put("collectionName", document.getCollectionName());
        metadata.put("backfillJobId", job.jobId());
        return IngestionContext.builder()
                .taskId(document.getId())
                .pipelineId(defaultText(job.pipelineId(), document.getPipelineId()))
                .rawBytes(fileBytes)
                .mimeType(document.getFileType())
                .metadata(metadata)
                .build();
    }

    private MetadataValidationDecision decision(IngestionContext context) {
        if (context.getMetadataValidationResult() == null) {
            return MetadataValidationDecision.ACCEPT;
        }
        return context.getMetadataValidationResult().decision();
    }

    private MetadataBackfillJobRecord requireJob(String jobId) {
        String safeJobId = requireText(jobId, "jobId");
        return jobRepositoryPort.findById(safeJobId)
                .orElseThrow(() -> new IllegalArgumentException("metadata backfill job not found: " + safeJobId));
    }

    private MetadataBackfillJobRecord updateStatus(MetadataBackfillJobRecord job, MetadataBackfillJobStatus status) {
        return updateStatus(job, status, job.operator());
    }

    private MetadataBackfillJobRecord updateStatus(MetadataBackfillJobRecord job,
                                                   MetadataBackfillJobStatus status,
                                                   String operator) {
        return new MetadataBackfillJobRecord(job.jobId(), job.tenantId(), job.knowledgeBaseId(), job.pipelineId(),
                status, job.currentPage(), job.batchSize(), job.processedDocuments(), job.succeededDocuments(),
                job.failedDocuments(), job.skippedDocuments(), job.reviewDocuments(), job.quarantineDocuments(),
                job.checkpoint(), job.failures(), defaultText(operator, job.operator()), job.createTime(), Instant.now());
    }

    private MetadataBackfillRunResult toResult(MetadataBackfillJobRecord job) {
        return new MetadataBackfillRunResult(job.jobId(), job.status(), job.currentPage(), job.batchSize(),
                job.processedDocuments(), job.succeededDocuments(), job.failedDocuments(), job.skippedDocuments(),
                job.reviewDocuments(), job.quarantineDocuments(), job.checkpoint(), job.failures());
    }

    private boolean terminalOrBlocked(MetadataBackfillJobStatus status) {
        return MetadataBackfillJobStatus.PAUSED.equals(status)
                || MetadataBackfillJobStatus.CANCELLED.equals(status)
                || MetadataBackfillJobStatus.COMPLETED.equals(status)
                || MetadataBackfillJobStatus.FAILED.equals(status);
    }

    private boolean hasAcceptedResult(MetadataBackfillJobRecord job, KnowledgeDocumentDetail document) {
        if (forceRerun(job)) {
            return false;
        }
        int schemaVersion = intValue(job.checkpoint().get("schemaVersion"), 0);
        if (schemaVersion <= 0) {
            return false;
        }
        return extractionResultRepositoryPort.hasAcceptedResult(
                job.tenantId(),
                job.knowledgeBaseId(),
                document.getId(),
                schemaVersion,
                textValue(job.checkpoint().get("extractorVersion"), ""));
    }

    private boolean forceRerun(MetadataBackfillJobRecord job) {
        return booleanValue(job.checkpoint().get("forceRerun"))
                || booleanValue(job.checkpoint().get("force"));
    }

    private Map<String, Object> initialCheckpoint(Map<String, Object> metadata) {
        Map<String, Object> checkpoint = checkpoint(1L, "", metadata);
        checkpoint.put("schemaVersion", intValue(metadata.get("schemaVersion"), 1));
        checkpoint.put("extractorVersion", textValue(metadata.get("extractorVersion"), ""));
        if (booleanValue(metadata.get("forceRerun")) || booleanValue(metadata.get("force"))) {
            checkpoint.put("forceRerun", true);
        }
        return checkpoint;
    }

    private Map<String, Object> checkpoint(long currentPage, String lastDocumentId) {
        return checkpoint(currentPage, lastDocumentId, Map.of());
    }

    private Map<String, Object> checkpoint(long currentPage, String lastDocumentId, Map<String, Object> previous) {
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("currentPage", currentPage);
        checkpoint.put("lastDocumentId", Objects.requireNonNullElse(lastDocumentId, ""));
        copyCheckpointOption(previous, checkpoint, "schemaVersion");
        copyCheckpointOption(previous, checkpoint, "extractorVersion");
        copyCheckpointOption(previous, checkpoint, "forceRerun");
        copyCheckpointOption(previous, checkpoint, "force");
        return checkpoint;
    }

    private void copyCheckpointOption(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source != null && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private int normalizeBatchSize(int batchSize) {
        if (batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String defaultText(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        return Objects.requireNonNullElse(second, "").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(Objects.toString(value, "false"));
    }

    private String textValue(Object value, String defaultValue) {
        String text = Objects.toString(value, "");
        return text.isBlank() ? defaultValue : text;
    }

    private ObservationScope startObservation(MetadataBackfillJobRecord job) {
        if (observationPort == null) {
            return null;
        }
        try {
            return observationPort.start(new ObservationCommand(OBSERVATION_BACKFILL, "",
                    Map.of("kb", job.knowledgeBaseId(), "status", job.status().name())));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void recordBackfillResult(ObservationScope scope, MetadataBackfillJobRecord job) {
        String eventName = job.failedDocuments() == 0 ? EVENT_BACKFILL_SUCCESS : EVENT_BACKFILL_FAILURE;
        recordObservationEvent(scope, eventName, Map.of(
                "status", job.status().name(),
                "hasFailures", String.valueOf(job.failedDocuments() > 0)));
    }

    private void recordObservationEvent(ObservationScope scope, String name, Map<String, String> attributes) {
        if (scope == null) {
            return;
        }
        try {
            scope.recordEvent(new ObservationEvent(name, null, attributes));
        } catch (RuntimeException ex) {
            // 观测失败不能影响回填断点推进。
        }
    }

    private void closeObservation(ObservationScope scope) {
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (RuntimeException ex) {
            // 关闭观测资源失败不覆盖回填批次结果。
        }
    }

    private static final class BackfillAccumulator {

        private final MetadataBackfillJobRecord base;
        private int processedDocuments;
        private int succeededDocuments;
        private int failedDocuments;
        private int skippedDocuments;
        private int reviewDocuments;
        private int quarantineDocuments;
        private final List<String> failures;

        private BackfillAccumulator(MetadataBackfillJobRecord base) {
            this.base = base;
            this.processedDocuments = base.processedDocuments();
            this.succeededDocuments = base.succeededDocuments();
            this.failedDocuments = base.failedDocuments();
            this.skippedDocuments = base.skippedDocuments();
            this.reviewDocuments = base.reviewDocuments();
            this.quarantineDocuments = base.quarantineDocuments();
            this.failures = new ArrayList<>(base.failures());
        }

        private void apply(KnowledgeDocumentDetail document, DocumentOutcome outcome) {
            processedDocuments++;
            if (outcome.skipped()) {
                skippedDocuments++;
                return;
            }
            if (!outcome.success()) {
                failedDocuments++;
                String docId = document == null ? "" : Objects.requireNonNullElse(document.getId(), "");
                failures.add(docId + ": " + outcome.message());
                return;
            }
            succeededDocuments++;
            if (MetadataValidationDecision.REVIEW_REQUIRED.equals(outcome.decision())) {
                reviewDocuments++;
            }
            if (MetadataValidationDecision.QUARANTINE.equals(outcome.decision())) {
                quarantineDocuments++;
            }
        }

        private MetadataBackfillJobRecord toRecord(MetadataBackfillJobStatus status,
                                                   long currentPage,
                                                   Map<String, Object> checkpoint) {
            return new MetadataBackfillJobRecord(base.jobId(), base.tenantId(), base.knowledgeBaseId(),
                    base.pipelineId(), status, currentPage, base.batchSize(), processedDocuments,
                    succeededDocuments, failedDocuments, skippedDocuments, reviewDocuments, quarantineDocuments,
                    checkpoint, failures, base.operator(), base.createTime(), Instant.now());
        }
    }

    private record DocumentOutcome(
            boolean success,
            boolean skipped,
            MetadataValidationDecision decision,
            String message
    ) {

        private static DocumentOutcome success(MetadataValidationDecision decision) {
            return new DocumentOutcome(true, false, decision, "");
        }

        private static DocumentOutcome skipped(String message) {
            return new DocumentOutcome(true, true, MetadataValidationDecision.ACCEPT,
                    Objects.requireNonNullElse(message, ""));
        }

        private static DocumentOutcome failed(String message) {
            return new DocumentOutcome(false, false, MetadataValidationDecision.ACCEPT,
                    Objects.requireNonNullElse(message, ""));
        }
    }
}
