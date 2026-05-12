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
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UploadKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkLogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentFileRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentProcessRef;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedule;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 原生知识库文档应用服务。
 *
 * <p>该服务承接旧 KnowledgeDocumentService 的主流程外壳：上传文件、创建文档记录、派发分块任务、
 * 执行入库流水线并回写状态。具体解析、分块、Embedding 与写索引由可插拔入库节点完成。
 */
public class KernelKnowledgeDocumentService implements KnowledgeDocumentInboundPort {

    public static final String DEFAULT_CHUNK_TOPIC = "persistent://seahorse-agent/ai/knowledge-document-chunk";
    private static final String BIZ_DESC_CHUNK = "文档分块";
    private static final String STATUS_RUNNING_CONFLICT = "文档分块操作正在进行中，请稍后再试";
    private static final String STATUS_RUNNING = "running";
    private static final int DEFAULT_SEARCH_LIMIT = 8;
    private static final int MAX_SEARCH_LIMIT = 20;

    private final KnowledgeBaseQueryPort knowledgeBaseQueryPort;
    private final KnowledgeDocumentRepositoryPort documentRepositoryPort;
    private final ObjectStoragePort objectStoragePort;
    private final MessageQueuePort messageQueuePort;
    private final KernelIngestionEngine ingestionEngine;
    private final KnowledgeDocumentVectorPorts vectorPorts;
    private final DocumentRefreshSchedulePort refreshSchedulePort;
    private final SchedulerPort schedulerPort;
    private final String chunkTopic;

    public KernelKnowledgeDocumentService(KnowledgeDocumentServicePorts servicePorts,
                                          KnowledgeDocumentVectorPorts vectorPorts,
                                          String chunkTopic) {
        this(servicePorts, vectorPorts, chunkTopic, DocumentRefreshSchedulePort.noop(), SchedulerPort.none());
    }

    public KernelKnowledgeDocumentService(KnowledgeDocumentServicePorts servicePorts,
                                          KnowledgeDocumentVectorPorts vectorPorts,
                                          String chunkTopic,
                                          DocumentRefreshSchedulePort refreshSchedulePort,
                                          SchedulerPort schedulerPort) {
        KnowledgeDocumentServicePorts safePorts = Objects.requireNonNull(servicePorts,
                "servicePorts must not be null");
        this.knowledgeBaseQueryPort = safePorts.knowledgeBaseQueryPort();
        this.documentRepositoryPort = safePorts.documentRepositoryPort();
        this.objectStoragePort = safePorts.objectStoragePort();
        this.messageQueuePort = safePorts.messageQueuePort();
        this.ingestionEngine = safePorts.ingestionEngine();
        this.vectorPorts = Objects.requireNonNull(vectorPorts, "vectorPorts must not be null");
        this.refreshSchedulePort = Objects.requireNonNull(refreshSchedulePort,
                "refreshSchedulePort must not be null");
        this.schedulerPort = Objects.requireNonNull(schedulerPort, "schedulerPort must not be null");
        this.chunkTopic = hasText(chunkTopic) ? chunkTopic : DEFAULT_CHUNK_TOPIC;
    }

    @Override
    public KnowledgeDocumentRecord upload(UploadKnowledgeDocumentCommand command) {
        UploadKnowledgeDocumentCommand safeCommand = Objects.requireNonNull(command, "upload command must not be null");
        KnowledgeBaseRef knowledgeBase = requireKnowledgeBase(safeCommand.kbId());
        StoredObject storedObject = uploadToStorage(knowledgeBase.collectionName(), safeCommand);
        return documentRepositoryPort.createPendingDocument(new CreateKnowledgeDocumentCommand(
                knowledgeBase.id(),
                storedObject.originalFilename(),
                new KnowledgeDocumentFileRef(storedObject.url(), storedObject.detectedType(), storedObject.size()),
                new KnowledgeDocumentProcessRef("pending",
                        safeCommand.options().processMode(), safeCommand.options().pipelineId()),
                safeCommand.operator()));
    }

    @Override
    public void startChunk(String docId, String operator) {
        KnowledgeDocumentRecord document = requireDocument(docId);
        boolean marked = documentRepositoryPort.markRunning(docId, operator);
        if (!marked) {
            throw new IllegalStateException(STATUS_RUNNING_CONFLICT);
        }
        KnowledgeDocumentChunkEvent event = new KnowledgeDocumentChunkEvent(
                docId, document.kbId(), operator, document.process().pipelineId());
        messageQueuePort.publishReliable(chunkTopic, docId, BIZ_DESC_CHUNK, event);
    }

    @Override
    public void executeChunk(String docId, PipelineDefinition pipeline, String operator) {
        KnowledgeDocumentRecord document = requireDocument(docId);
        try (InputStream inputStream = objectStoragePort.openStream(document.file().fileUrl())) {
            IngestionContext result = executePipeline(document, pipeline, inputStream.readAllBytes());
            if (result.getError() != null) {
                throw new IllegalStateException(result.getError());
            }
            int chunkCount = result.getChunks() == null ? 0 : result.getChunks().size();
            documentRepositoryPort.markSuccess(docId, chunkCount, operator);
        } catch (Exception ex) {
            documentRepositoryPort.markFailed(docId, operator, ex.getMessage());
            throw new IllegalStateException("文档入库失败：" + docId, ex);
        }
    }

    @Override
    public KnowledgeDocumentDetail queryById(String docId) {
        return requireDocumentDetail(docId);
    }

    @Override
    public KnowledgeDocumentPage page(String kbId, KnowledgeDocumentPageCommand command) {
        KnowledgeDocumentPageCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        return documentRepositoryPort.page(
                requireText(kbId, "kbId"),
                safeCommand.current(),
                safeCommand.size(),
                safeCommand.status(),
                safeCommand.keyword());
    }

    @Override
    public List<KnowledgeDocumentSummary> search(String keyword, int limit) {
        if (!hasText(keyword)) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? DEFAULT_SEARCH_LIMIT : Math.min(limit, MAX_SEARCH_LIMIT);
        return knowledgeBaseQueryPort.searchDocuments(keyword.trim(), safeLimit);
    }

    @Override
    public void update(String docId, UpdateKnowledgeDocumentCommand command) {
        KnowledgeDocumentDetail current = requireEditableDocument(docId, "文档正在分块中，无法修改");
        UpdateKnowledgeDocumentCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String docName = requireText(safeCommand.getDocName(), "docName");
        KnowledgeDocumentUpdateValues values = toUpdateValues(safeCommand, docName);
        if (!documentRepositoryPort.update(current.getId(), values)) {
            throw new IllegalArgumentException("文档不存在：" + docId);
        }
        syncRefreshSchedule(current, safeCommand);
    }

    @Override
    public void enable(String docId, boolean enabled, String operator) {
        KnowledgeDocumentDetail current = requireEditableDocument(docId, "文档正在分块中，无法修改");
        if (Boolean.valueOf(enabled).equals(current.getEnabled())) {
            return;
        }
        if (enabled) {
            reindexEnabledChunks(current);
        } else {
            vectorPorts.vectorIndexPort().deleteDocumentVectors(current.getCollectionName(), current.getId());
        }
        if (!documentRepositoryPort.updateEnabled(current.getId(), enabled, operator)) {
            throw new IllegalArgumentException("文档不存在：" + docId);
        }
    }

    @Override
    public void delete(String docId, String operator) {
        KnowledgeDocumentDetail current = requireEditableDocument(docId, "文档正在分块中，无法删除");
        if (!documentRepositoryPort.delete(current.getId(), operator)) {
            throw new IllegalArgumentException("文档不存在：" + docId);
        }
        vectorPorts.vectorIndexPort().deleteDocumentVectors(current.getCollectionName(), current.getId());
        if (hasText(current.getFileUrl())) {
            objectStoragePort.deleteByUrl(current.getFileUrl());
        }
    }

    @Override
    public KnowledgeDocumentChunkLogPage chunkLogs(String docId, long current, long size) {
        requireDocumentDetail(docId);
        return documentRepositoryPort.chunkLogs(docId, current, size);
    }

    private IngestionContext executePipeline(KnowledgeDocumentRecord document,
                                             PipelineDefinition pipeline,
                                             byte[] fileBytes) {
        KnowledgeBaseRef knowledgeBase = requireKnowledgeBase(document.kbId());
        IngestionContext context = IngestionContext.builder()
                .taskId(document.id())
                .pipelineId(document.process().pipelineId())
                .rawBytes(fileBytes)
                .mimeType(document.file().fileType())
                .metadata(java.util.Map.of(
                        "kbId", document.kbId(),
                        "docId", document.id(),
                        "fileName", document.docName(),
                        "collectionName", knowledgeBase.collectionName()))
                .build();
        return ingestionEngine.execute(Objects.requireNonNull(pipeline, "pipeline must not be null"), context);
    }

    private KnowledgeBaseRef requireKnowledgeBase(String kbId) {
        List<KnowledgeBaseRef> refs = knowledgeBaseQueryPort.listSearchableKnowledgeBases();
        return refs.stream()
                .filter(ref -> Objects.equals(ref.id(), kbId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或未配置向量集合：" + kbId));
    }

    private KnowledgeDocumentRecord requireDocument(String docId) {
        if (!hasText(docId)) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        return documentRepositoryPort.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + docId));
    }

    private KnowledgeDocumentDetail requireDocumentDetail(String docId) {
        if (!hasText(docId)) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        return documentRepositoryPort.findDetailById(docId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + docId));
    }

    private KnowledgeDocumentDetail requireEditableDocument(String docId, String runningMessage) {
        KnowledgeDocumentDetail detail = requireDocumentDetail(docId);
        if (STATUS_RUNNING.equalsIgnoreCase(detail.getStatus())) {
            throw new IllegalStateException(runningMessage);
        }
        return detail;
    }

    private KnowledgeDocumentUpdateValues toUpdateValues(UpdateKnowledgeDocumentCommand command, String docName) {
        KnowledgeDocumentUpdateValues values = new KnowledgeDocumentUpdateValues();
        values.setDocName(docName.trim());
        values.setProcessMode(blankToNull(command.getProcessMode()));
        values.setChunkStrategy(blankToNull(command.getChunkStrategy()));
        values.setChunkConfig(blankToNull(command.getChunkConfig()));
        values.setPipelineId(blankToNull(command.getPipelineId()));
        values.setSourceLocation(blankToNull(command.getSourceLocation()));
        values.setScheduleEnabled(command.getScheduleEnabled());
        values.setScheduleCron(blankToNull(command.getScheduleCron()));
        values.setOperator(Objects.requireNonNullElse(command.getOperator(), ""));
        return values;
    }

    private void syncRefreshSchedule(KnowledgeDocumentDetail current, UpdateKnowledgeDocumentCommand command) {
        Integer enabled = command.getScheduleEnabled() == null
                ? current.getScheduleEnabled()
                : command.getScheduleEnabled();
        String cron = hasText(command.getScheduleCron()) ? command.getScheduleCron().trim() : current.getScheduleCron();
        if (enabled == null || enabled != 1 || !hasText(cron)) {
            refreshSchedulePort.disableByDocumentId(current.getId(), "schedule disabled");
            return;
        }
        refreshSchedulePort.upsert(new DocumentRefreshSchedule(
                null,
                current.getId(),
                current.getKbId(),
                cron,
                true,
                schedulerPort.nextRun(cron, Instant.now()),
                null,
                null,
                null));
    }

    private void reindexEnabledChunks(KnowledgeDocumentDetail document) {
        List<KnowledgeChunkRecord> chunks = documentRepositoryPort.listEnabledChunks(document.getId());
        if (chunks.isEmpty()) {
            return;
        }
        vectorPorts.vectorIndexPort().indexDocumentChunks(document.getCollectionName(), document.getId(),
                chunks.stream().map(chunk -> toVectorChunk(document, chunk)).toList());
    }

    private VectorChunk toVectorChunk(KnowledgeDocumentDetail document, KnowledgeChunkRecord record) {
        VectorChunk chunk = new VectorChunk();
        chunk.setChunkId(record.getId());
        chunk.setContent(record.getContent());
        chunk.setIndex(record.getChunkIndex());
        chunk.setEmbedding(toArray(vectorPorts.embeddingModelPort().embed(
                document.getEmbeddingModel(), record.getContent())));
        return chunk;
    }

    private float[] toArray(List<Float> values) {
        List<Float> safeValues = Objects.requireNonNullElse(values, List.of());
        float[] result = new float[safeValues.size()];
        for (int index = 0; index < safeValues.size(); index++) {
            result[index] = safeValues.get(index);
        }
        return result;
    }

    private StoredObject uploadToStorage(String collectionName, UploadKnowledgeDocumentCommand command) {
        return objectStoragePort.upload(
                collectionName,
                command.file().content(),
                command.file().size(),
                command.file().originalFilename(),
                command.file().contentType());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
