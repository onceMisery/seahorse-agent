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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 元数据历史回填 kernel 编排服务。
 *
 * <p>回填会重新执行文档入库流水线，让 MetadataExtractor/Normalizer/Validator 产生统一的
 * 抽取结果、Review 和 Quarantine 记录；服务自身只负责分页、断点、幂等重跑和失败隔离。
 */
public class KernelMetadataBackfillService implements MetadataBackfillInboundPort, MetadataReviewReExtractPort {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 500;
    private static final String DEFAULT_OPERATOR = "metadata-backfill";
    private static final String OBSERVATION_BACKFILL = "metadata.backfill.batch";
    private static final String EVENT_BACKFILL_SUCCESS = "metadata.backfill.batch.success";
    private static final String EVENT_BACKFILL_FAILURE = "metadata.backfill.batch.failure";
    private static final String PAUSE_REASON_SCHEMA_MISSING = "SCHEMA_MISSING";

    private final KnowledgeDocumentRepositoryPort documentRepositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;
    private final KernelIngestionEngine ingestionEngine;
    private final MetadataBackfillJobRepositoryPort jobRepositoryPort;
    private final MetadataExtractionResultRepositoryPort extractionResultRepositoryPort;
    private final MetadataQuarantinePort quarantinePort;
    private final ObservationPort observationPort;

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort) {
        this(documentRepositoryPort, objectStoragePort, pipelineRepositoryPort, ingestionEngine, jobRepositoryPort,
                MetadataExtractionResultRepositoryPort.noop(), MetadataQuarantinePort.noop(), null);
    }

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort,
                                         ObservationPort observationPort) {
        this(documentRepositoryPort, objectStoragePort, pipelineRepositoryPort, ingestionEngine, jobRepositoryPort,
                MetadataExtractionResultRepositoryPort.noop(), MetadataQuarantinePort.noop(), observationPort);
    }

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort,
                                         MetadataExtractionResultRepositoryPort extractionResultRepositoryPort,
                                         ObservationPort observationPort) {
        this(documentRepositoryPort, objectStoragePort, pipelineRepositoryPort, ingestionEngine, jobRepositoryPort,
                extractionResultRepositoryPort, MetadataQuarantinePort.noop(), observationPort);
    }

    public KernelMetadataBackfillService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                         ObjectStoragePort objectStoragePort,
                                         PipelineDefinitionRepositoryPort pipelineRepositoryPort,
                                         KernelIngestionEngine ingestionEngine,
                                         MetadataBackfillJobRepositoryPort jobRepositoryPort,
                                         MetadataExtractionResultRepositoryPort extractionResultRepositoryPort,
                                         MetadataQuarantinePort quarantinePort,
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
        this.quarantinePort = Objects.requireNonNullElseGet(quarantinePort, MetadataQuarantinePort::noop);
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
        } catch (RuntimeException ex) {
            MetadataBackfillJobRecord failed = failBatch(running, ex);
            jobRepositoryPort.save(failed);
            recordBackfillResult(observationScope, failed);
            return toResult(failed);
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

    @Override
    public String requestReExtract(MetadataReviewReExtractRequest request) {
        MetadataReviewReExtractRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        requireText(safeRequest.documentId(), "documentId");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentIds", List.of(safeRequest.documentId()));
        metadata.put("sourceReviewItemId", safeRequest.reviewItemId());
        metadata.put("extractorVersion", requireText(safeRequest.extractorVersion(), "extractorVersion"));
        if (hasText(safeRequest.llmExtractorVersion())) {
            metadata.put("llmExtractorVersion", safeRequest.llmExtractorVersion());
        }
        if (hasText(safeRequest.llmPromptVersion())) {
            metadata.put("llmPromptVersion", safeRequest.llmPromptVersion());
        }
        metadata.put("forceRerun", true);
        metadata.put("overwriteApproved", true);
        metadata.put("reExtract", true);
        MetadataBackfillJobRecord job = createJob(new MetadataBackfillCommand(
                safeRequest.tenantId(),
                safeRequest.knowledgeBaseId(),
                safeRequest.pipelineId(),
                MAX_BATCH_SIZE,
                defaultText(safeRequest.operator(), DEFAULT_OPERATOR),
                metadata));
        return job.jobId();
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
        for (KnowledgeDocumentDetail document : recordsAfterCheckpoint(job, page.records())) {
            MetadataBackfillJobRecord latest = jobRepositoryPort.findById(job.jobId()).orElse(job);
            if (MetadataBackfillJobStatus.PAUSED.equals(latest.status())
                    || MetadataBackfillJobStatus.CANCELLED.equals(latest.status())) {
                return latest;
            }
            DocumentOutcome outcome;
            try {
                outcome = processDocument(job, document);
            } catch (MetadataSchemaMissingException ex) {
                MetadataBackfillJobRecord paused = pauseForSchemaMissing(job, accumulator, ex);
                jobRepositoryPort.save(paused);
                return paused;
            }
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

    private List<KnowledgeDocumentDetail> recordsAfterCheckpoint(MetadataBackfillJobRecord job,
                                                                 List<KnowledgeDocumentDetail> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<String, Object> checkpoint = Objects.requireNonNullElse(job.checkpoint(), Map.of());
        String lastDocumentId = textValue(checkpoint.get("lastDocumentId"), "");
        if (!hasText(lastDocumentId) || longValue(checkpoint.get("currentPage"), job.currentPage()) != job.currentPage()) {
            return targetDocuments(job, records);
        }
        for (int index = 0; index < records.size(); index++) {
            KnowledgeDocumentDetail document = records.get(index);
            if (document != null && lastDocumentId.equals(document.getId())) {
                // 同页恢复时跳过已经写入 checkpoint 的文档，避免进程中断后重复污染复核或隔离队列。
                return index >= records.size() - 1
                        ? List.of()
                        : targetDocuments(job, records.subList(index + 1, records.size()));
            }
        }
        return targetDocuments(job, records);
    }

    private List<KnowledgeDocumentDetail> targetDocuments(MetadataBackfillJobRecord job,
                                                          List<KnowledgeDocumentDetail> records) {
        Set<String> targetDocumentIds = targetDocumentIds(job.checkpoint());
        if (targetDocumentIds.isEmpty()) {
            return records;
        }
        // Review 触发的 RE_EXTRACT 通过 documentIds 限定单文档回填，避免误扫整库。
        return records.stream()
                .filter(document -> document != null && targetDocumentIds.contains(document.getId()))
                .toList();
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
            IllegalStateException ex = new IllegalStateException("pipelineId missing");
            documentRepositoryPort.markFailed(document.getId(), job.operator(), ex.getMessage());
            quarantineBackfillFailure(job, document, "EXTRACT", ex);
            return DocumentOutcome.failed("pipelineId missing");
        }
        if (!documentRepositoryPort.markRunning(document.getId(), job.operator())) {
            return DocumentOutcome.skipped("document is running");
        }
        String failureStage = "FETCH";
        try (InputStream inputStream = objectStoragePort.openStream(requireText(document.getFileUrl(), "fileUrl"))) {
            byte[] fileBytes = inputStream.readAllBytes();
            failureStage = "EXTRACT";
            IngestionContext context = buildContext(job, document, fileBytes);
            IngestionContext result = ingestionEngine.execute(requirePipeline(pipelineId), context);
            if (result.getError() != null) {
                MetadataSchemaMissingException schemaMissing = schemaMissing(result.getError());
                if (schemaMissing != null) {
                    throw schemaMissing;
                }
                throw new IllegalStateException(result.getError().getMessage(), result.getError());
            }
            int chunkCount = result.getChunks() == null ? 0 : result.getChunks().size();
            documentRepositoryPort.markSuccess(document.getId(), chunkCount, job.operator());
            return DocumentOutcome.success(decision(result));
        } catch (MetadataSchemaMissingException ex) {
            documentRepositoryPort.markFailed(document.getId(), job.operator(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            documentRepositoryPort.markFailed(document.getId(), job.operator(), ex.getMessage());
            quarantineBackfillFailure(job, document, failureStage, ex);
            return DocumentOutcome.failed(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private MetadataBackfillJobRecord pauseForSchemaMissing(MetadataBackfillJobRecord job,
                                                            BackfillAccumulator accumulator,
                                                            MetadataSchemaMissingException ex) {
        MetadataBackfillJobRecord latest = jobRepositoryPort.findById(job.jobId()).orElse(job);
        Map<String, Object> checkpoint = new LinkedHashMap<>(Objects.requireNonNullElse(latest.checkpoint(),
                Map.of()));
        checkpoint.put("pauseReason", PAUSE_REASON_SCHEMA_MISSING);
        checkpoint.put("pauseMessage", ex.getMessage());
        accumulator.addFailure("schema: " + ex.getMessage());
        // Schema 缺失是任务级阻塞：暂停等待治理配置补齐，不把当前文档写入 Review/Quarantine。
        return accumulator.toRecord(MetadataBackfillJobStatus.PAUSED, latest.currentPage(), checkpoint);
    }

    private MetadataSchemaMissingException schemaMissing(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof MetadataSchemaMissingException schemaMissing) {
                return schemaMissing;
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private void quarantineBackfillFailure(MetadataBackfillJobRecord job,
                                           KnowledgeDocumentDetail document,
                                           String stage,
                                           Exception ex) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("backfillJobId", job.jobId());
        snapshot.put("currentPage", job.currentPage());
        snapshot.put("pipelineId", defaultText(job.pipelineId(), document.getPipelineId()));
        snapshot.put("fileUrl", Objects.requireNonNullElse(document.getFileUrl(), ""));
        snapshot.put("errorType", ex.getClass().getName());
        snapshot.put("errorMessage", Objects.requireNonNullElse(ex.getMessage(), ""));
        try {
            quarantinePort.quarantine(new MetadataQuarantineItem(
                    job.tenantId(),
                    job.knowledgeBaseId(),
                    document.getId(),
                    job.jobId(),
                    stage,
                    "BACKFILL_DOCUMENT_FAILED",
                    firstText(ex.getMessage(), "元数据历史回填文档处理失败"),
                    snapshot));
        } catch (RuntimeException ignored) {
            // 隔离写入失败不能影响回填断点和文档失败状态。
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
        metadata.put("requireMetadataSchema", true);
        copyCheckpointMetadata(job.checkpoint(), metadata, "sourceReviewItemId");
        copyCheckpointMetadata(job.checkpoint(), metadata, "extractorVersion");
        // LLM 抽取版本必须跟随回填上下文进入 MetadataExtractor，保证 prompt/模型策略可审计、可重放。
        copyCheckpointMetadata(job.checkpoint(), metadata, "llmExtractorVersion");
        copyCheckpointMetadata(job.checkpoint(), metadata, "llmPromptVersion");
        copyCheckpointMetadata(job.checkpoint(), metadata, "reExtract");
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

    private MetadataBackfillJobRecord failBatch(MetadataBackfillJobRecord job, RuntimeException ex) {
        List<String> failures = new ArrayList<>(job.failures());
        failures.add("batch: " + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        // 批次级异常没有具体文档归属，直接终止任务并保留原 checkpoint，便于人工排查后重建任务。
        return new MetadataBackfillJobRecord(job.jobId(), job.tenantId(), job.knowledgeBaseId(), job.pipelineId(),
                MetadataBackfillJobStatus.FAILED, job.currentPage(), job.batchSize(), job.processedDocuments(),
                job.succeededDocuments(), job.failedDocuments(), job.skippedDocuments(), job.reviewDocuments(),
                job.quarantineDocuments(), job.checkpoint(), failures, job.operator(), job.createTime(), Instant.now());
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
                || booleanValue(job.checkpoint().get("force"))
                // overwriteApproved 对齐治理设计：默认保留已审核结果，显式开启时允许回填重跑覆盖。
                || booleanValue(job.checkpoint().get("overwriteApproved"));
    }

    private Map<String, Object> initialCheckpoint(Map<String, Object> metadata) {
        Map<String, Object> checkpoint = checkpoint(1L, "", metadata);
        checkpoint.put("schemaVersion", intValue(metadata.get("schemaVersion"), 1));
        checkpoint.put("extractorVersion", textValue(metadata.get("extractorVersion"), ""));
        copyCheckpointOption(metadata, checkpoint, "llmExtractorVersion");
        copyCheckpointOption(metadata, checkpoint, "llmPromptVersion");
        if (booleanValue(metadata.get("forceRerun")) || booleanValue(metadata.get("force"))) {
            checkpoint.put("forceRerun", true);
        }
        if (booleanValue(metadata.get("overwriteApproved"))) {
            checkpoint.put("overwriteApproved", true);
        }
        copyCheckpointOption(metadata, checkpoint, "documentIds");
        copyCheckpointOption(metadata, checkpoint, "sourceReviewItemId");
        copyCheckpointOption(metadata, checkpoint, "reExtract");
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
        copyCheckpointOption(previous, checkpoint, "llmExtractorVersion");
        copyCheckpointOption(previous, checkpoint, "llmPromptVersion");
        copyCheckpointOption(previous, checkpoint, "forceRerun");
        copyCheckpointOption(previous, checkpoint, "force");
        copyCheckpointOption(previous, checkpoint, "overwriteApproved");
        copyCheckpointOption(previous, checkpoint, "documentIds");
        copyCheckpointOption(previous, checkpoint, "sourceReviewItemId");
        copyCheckpointOption(previous, checkpoint, "reExtract");
        return checkpoint;
    }

    private void copyCheckpointMetadata(Map<String, Object> checkpoint, Map<String, Object> metadata, String key) {
        if (checkpoint != null && checkpoint.containsKey(key)) {
            metadata.put(key, checkpoint.get(key));
        }
    }

    private Set<String> targetDocumentIds(Map<String, Object> checkpoint) {
        if (checkpoint == null || !checkpoint.containsKey("documentIds")) {
            return Set.of();
        }
        Object value = checkpoint.get("documentIds");
        Set<String> ids = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addDocumentId(ids, item);
            }
        } else {
            String text = textValue(value, "");
            for (String item : text.split(",")) {
                addDocumentId(ids, item);
            }
        }
        return Set.copyOf(ids);
    }

    private void addDocumentId(Set<String> ids, Object value) {
        String text = textValue(value, "");
        if (hasText(text)) {
            ids.add(text.trim());
        }
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

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "");
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

    private long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Objects.toString(value, ""));
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
        boolean hasFailures = MetadataBackfillJobStatus.FAILED.equals(job.status())
                || job.failedDocuments() > 0
                || !job.failures().isEmpty();
        String eventName = hasFailures ? EVENT_BACKFILL_FAILURE : EVENT_BACKFILL_SUCCESS;
        recordObservationEvent(scope, eventName, Map.of(
                "status", job.status().name(),
                "hasFailures", String.valueOf(hasFailures)));
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

        private void addFailure(String message) {
            failures.add(Objects.requireNonNullElse(message, ""));
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
