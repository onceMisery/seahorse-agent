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
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshResult;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionStart;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedule;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshScheduleUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生文档刷新服务。
 */
public class KernelDocumentRefreshService implements DocumentRefreshInboundPort {

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_SKIPPED = "skipped";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_RUNNING = "running";
    private static final String MESSAGE_OK = "OK";
    private static final int DEFAULT_LIMIT = 20;
    private static final Duration DOCUMENT_LOCK_LEASE = Duration.ofMinutes(30);

    private final DocumentRefreshSchedulePort schedulePort;
    private final DocumentRefreshStateRepositoryPort stateRepositoryPort;
    private final KnowledgeDocumentRepositoryPort documentRepositoryPort;
    private final DocumentFetcherPort documentFetcherPort;
    private final ObjectStoragePort objectStoragePort;
    private final KnowledgeDocumentInboundPort documentInboundPort;
    private final PipelineDefinitionRepositoryPort pipelineRepositoryPort;
    private final SchedulerPort schedulerPort;
    private final DistributedLockPort lockPort;

    public KernelDocumentRefreshService(DocumentRefreshServicePorts servicePorts) {
        DocumentRefreshServicePorts safePorts = Objects.requireNonNull(servicePorts,
                "servicePorts must not be null");
        this.schedulePort = Objects.requireNonNull(safePorts.schedulePort(), "schedulePort must not be null");
        this.stateRepositoryPort = Objects.requireNonNull(safePorts.stateRepositoryPort(),
                "stateRepositoryPort must not be null");
        this.documentRepositoryPort = Objects.requireNonNull(safePorts.documentRepositoryPort(),
                "documentRepositoryPort must not be null");
        this.documentFetcherPort = Objects.requireNonNull(safePorts.documentFetcherPort(),
                "documentFetcherPort must not be null");
        this.objectStoragePort = Objects.requireNonNull(safePorts.objectStoragePort(),
                "objectStoragePort must not be null");
        this.documentInboundPort = Objects.requireNonNull(safePorts.documentInboundPort(),
                "documentInboundPort must not be null");
        this.pipelineRepositoryPort = Objects.requireNonNull(safePorts.pipelineRepositoryPort(),
                "pipelineRepositoryPort must not be null");
        this.schedulerPort = Objects.requireNonNull(safePorts.schedulerPort(), "schedulerPort must not be null");
        this.lockPort = Objects.requireNonNullElse(safePorts.lockPort(), DistributedLockPort.noop());
    }

    @Override
    public DocumentRefreshResult refreshDocument(String docId, String operator) {
        String safeDocId = requireText(docId, "docId");
        String lockName = "document-refresh:" + safeDocId;
        if (!lockPort.tryLock(lockName, Duration.ZERO, DOCUMENT_LOCK_LEASE)) {
            return new DocumentRefreshResult(safeDocId, STATUS_SKIPPED, "document refresh lock busy", null);
        }
        try {
            return doRefreshDocument(safeDocId, operator);
        } finally {
            lockPort.unlock(lockName);
        }
    }

    private DocumentRefreshResult doRefreshDocument(String docId, String operator) {
        KnowledgeDocumentDetail document = requireRefreshableDocument(docId);
        DocumentRefreshSchedule schedule = schedulePort.findByDocumentId(document.getId())
                .orElseGet(() -> defaultSchedule(document));
        Instant startTime = Instant.now();
        String executionId = stateRepositoryPort.start(new DocumentRefreshExecutionStart(
                String.valueOf(schedule.id()), String.valueOf(document.getId()), String.valueOf(document.getKbId()), startTime));
        RefreshOutcome outcome = executeRefresh(document, schedule, operator);
        Instant endTime = Instant.now();
        Instant nextRunTime = nextRun(schedule, startTime);
        stateRepositoryPort.finish(new DocumentRefreshExecutionFinish(
                executionId,
                String.valueOf(schedule.id()),
                String.valueOf(document.getId()),
                String.valueOf(document.getKbId()),
                outcome.status(),
                outcome.message(),
                startTime,
                endTime,
                outcome.fileName(),
                outcome.fileSize(),
                outcome.contentHash(),
                null,
                null));
        schedulePort.updateState(new DocumentRefreshScheduleUpdate(
                schedule.id(),
                outcome.status(),
                outcome.message(),
                startTime,
                nextRunTime,
                outcome.contentHash() == null ? schedule.lastContentHash() : outcome.contentHash(),
                schedule.lastEtag(),
                schedule.lastModified()));
        return new DocumentRefreshResult(String.valueOf(document.getId()), outcome.status(), outcome.message(), outcome.contentHash());
    }

    @Override
    public List<DocumentRefreshResult> refreshDueSchedules(Instant now, int limit, String operator) {
        Instant safeNow = now == null ? Instant.now() : now;
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        return schedulePort.findDueSchedules(safeNow, safeLimit).stream()
                .map(schedule -> refreshDocument(String.valueOf(schedule.docId()), operator))
                .toList();
    }

    public void syncSchedule(KnowledgeDocumentDetail document, String cronExpr, Integer enabled) {
        if (document == null) {
            return;
        }
        boolean scheduleEnabled = enabled != null ? enabled == 1 : document.getScheduleEnabled() != null
                && document.getScheduleEnabled() == 1;
        String cron = hasText(cronExpr) ? cronExpr.trim() : document.getScheduleCron();
        if (!scheduleEnabled || !hasText(cron)) {
            schedulePort.disableByDocumentId(document.getId(), "schedule disabled");
            return;
        }
        schedulePort.upsert(new DocumentRefreshSchedule(
                null,
                document.getId(),
                document.getKbId(),
                cron,
                true,
                schedulerPort.nextRun(cron, Instant.now()),
                null,
                null,
                null));
    }

    public static String contentHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content == null ? new byte[0] : content.clone()));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }

    private RefreshOutcome executeRefresh(KnowledgeDocumentDetail document,
                                          DocumentRefreshSchedule schedule,
                                          String operator) {
        try {
            if (STATUS_RUNNING.equalsIgnoreCase(document.getStatus())) {
                return RefreshOutcome.skipped("document is running");
            }
            DocumentFetchResult fetchResult = fetch(document);
            String hash = contentHash(fetchResult.content());
            if (hasText(schedule.lastContentHash()) && Objects.equals(schedule.lastContentHash(), hash)) {
                return RefreshOutcome.skipped("content unchanged", hash,
                        fetchResult.fileName(), (long) fetchResult.content().length);
            }
            if (!documentRepositoryPort.markRunning(document.getId(), operator)) {
                return RefreshOutcome.skipped("document is running");
            }
            StoredObject stored = objectStoragePort.upload(
                    requireText(document.getCollectionName(), "collectionName"),
                    new ByteArrayInputStream(fetchResult.content()),
                    fetchResult.content().length,
                    resolveFileName(fetchResult, document),
                    contentType(fetchResult));
            KnowledgeDocumentFileRef fileRef = new KnowledgeDocumentFileRef(
                    stored.url(), stored.detectedType(), stored.size());
            if (!documentRepositoryPort.replaceFileForRefresh(document.getId(), fileRef, operator)) {
                throw new IllegalStateException("document metadata switch failed: " + document.getId());
            }
            documentInboundPort.executeChunk(document.getId(), requirePipeline(document), operator);
            return RefreshOutcome.success(hash, stored.originalFilename(), stored.size());
        } catch (Exception ex) {
            return RefreshOutcome.failed(ex.getMessage());
        }
    }

    private DocumentFetchResult fetch(KnowledgeDocumentDetail document) {
        if (!documentFetcherPort.supports(document.getSourceType())) {
            throw new IllegalArgumentException("unsupported document source type: " + document.getSourceType());
        }
        return documentFetcherPort.fetch(new DocumentFetchRequest(
                document.getSourceType(),
                document.getSourceLocation(),
                document.getDocName(),
                Map.of()));
    }

    private PipelineDefinition requirePipeline(KnowledgeDocumentDetail document) {
        return pipelineRepositoryPort.findById(requireText(document.getPipelineId(), "pipelineId"))
                .orElseThrow(() -> new IllegalArgumentException("pipeline not found: " + document.getPipelineId()));
    }

    private KnowledgeDocumentDetail requireRefreshableDocument(String docId) {
        KnowledgeDocumentDetail document = documentRepositoryPort.findDetailById(Long.parseLong(requireText(docId, "docId")))
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + docId));
        if (!Boolean.TRUE.equals(document.getEnabled())) {
            throw new IllegalStateException("document disabled: " + docId);
        }
        requireText(document.getSourceType(), "sourceType");
        requireText(document.getSourceLocation(), "sourceLocation");
        return document;
    }

    private DocumentRefreshSchedule defaultSchedule(KnowledgeDocumentDetail document) {
        return new DocumentRefreshSchedule(
                null,
                document.getId(),
                document.getKbId(),
                Objects.requireNonNullElse(document.getScheduleCron(), ""),
                document.getScheduleEnabled() != null && document.getScheduleEnabled() == 1,
                null,
                null,
                null,
                null);
    }

    private Instant nextRun(DocumentRefreshSchedule schedule, Instant from) {
        if (!schedule.enabled() || !hasText(schedule.cronExpr())) {
            return null;
        }
        return schedulerPort.nextRun(schedule.cronExpr(), from);
    }

    private String resolveFileName(DocumentFetchResult fetchResult, KnowledgeDocumentDetail document) {
        if (hasText(fetchResult.fileName())) {
            return fetchResult.fileName().trim();
        }
        return requireText(document.getDocName(), "docName");
    }

    private String contentType(DocumentFetchResult fetchResult) {
        return hasText(fetchResult.mimeType()) ? fetchResult.mimeType().trim() : "application/octet-stream";
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RefreshOutcome(
            String status,
            String message,
            String contentHash,
            String fileName,
            Long fileSize
    ) {

        private static RefreshOutcome success(String contentHash, String fileName, Long fileSize) {
            return new RefreshOutcome(STATUS_SUCCESS, MESSAGE_OK, contentHash, fileName, fileSize);
        }

        private static RefreshOutcome skipped(String message) {
            return new RefreshOutcome(STATUS_SKIPPED, message, null, null, null);
        }

        private static RefreshOutcome skipped(String message, String contentHash, String fileName, Long fileSize) {
            return new RefreshOutcome(STATUS_SKIPPED, message, contentHash, fileName, fileSize);
        }

        private static RefreshOutcome failed(String message) {
            return new RefreshOutcome(STATUS_FAILED, Objects.requireNonNullElse(message, "refresh failed"),
                    null, null, null);
        }
    }
}
