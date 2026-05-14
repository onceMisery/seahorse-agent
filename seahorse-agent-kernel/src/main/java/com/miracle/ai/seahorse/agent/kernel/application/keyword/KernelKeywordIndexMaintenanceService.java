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

package com.miracle.ai.seahorse.agent.kernel.application.keyword;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexRebuildResult;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 默认关键词索引运维服务。
 *
 * <p>ES 等独立搜索后端不能只凭 kbId/docId 自行重建正文索引；这里统一从文档仓储拉取启用分片快照，
 * 再交给 {@link KeywordIndexPort} 写入，确保所有后端看到同一份受治理的数据。
 */
public class KernelKeywordIndexMaintenanceService implements KeywordIndexMaintenanceInboundPort {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 500;
    private static final String SCOPE_DOCUMENT = "document";
    private static final String SCOPE_KNOWLEDGE_BASE = "knowledge_base";
    private static final String OBSERVATION_REBUILD = "keyword.index.rebuild";
    private static final String EVENT_REBUILD_SUCCESS = "keyword.index.rebuild.success";
    private static final String EVENT_REBUILD_FAILURE = "keyword.index.rebuild.failure";

    private final KnowledgeDocumentRepositoryPort documentRepositoryPort;
    private final KeywordIndexPort keywordIndexPort;
    private final ObservationPort observationPort;

    public KernelKeywordIndexMaintenanceService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                                KeywordIndexPort keywordIndexPort) {
        this(documentRepositoryPort, keywordIndexPort, null);
    }

    public KernelKeywordIndexMaintenanceService(KnowledgeDocumentRepositoryPort documentRepositoryPort,
                                                KeywordIndexPort keywordIndexPort,
                                                ObservationPort observationPort) {
        this.documentRepositoryPort = Objects.requireNonNull(documentRepositoryPort,
                "documentRepositoryPort must not be null");
        this.keywordIndexPort = Objects.requireNonNullElse(keywordIndexPort, KeywordIndexPort.noop());
        this.observationPort = observationPort;
    }

    @Override
    public KeywordIndexRebuildResult rebuildDocument(String docId) {
        RebuildAccumulator accumulator = new RebuildAccumulator(SCOPE_DOCUMENT, requireText(docId, "docId"));
        ObservationScope observationScope = startObservation(SCOPE_DOCUMENT);
        try {
            rebuildOneDocument(requireDocument(docId), accumulator);
            KeywordIndexRebuildResult result = accumulator.toResult();
            recordRebuildResult(observationScope, result);
            return result;
        } finally {
            closeObservation(observationScope);
        }
    }

    @Override
    public KeywordIndexRebuildResult rebuildKnowledgeBase(String kbId, int batchSize) {
        String safeKbId = requireText(kbId, "kbId");
        int safeBatchSize = normalizeBatchSize(batchSize);
        RebuildAccumulator accumulator = new RebuildAccumulator(SCOPE_KNOWLEDGE_BASE, safeKbId);
        ObservationScope observationScope = startObservation(SCOPE_KNOWLEDGE_BASE);
        try {
            long current = 1;
            while (true) {
                KnowledgeDocumentPage page = documentRepositoryPort.page(safeKbId, current, safeBatchSize, null, null);
                if (page.records().isEmpty()) {
                    break;
                }
                for (KnowledgeDocumentDetail document : page.records()) {
                    rebuildOneDocument(document, accumulator);
                }
                if (page.pages() > 0 && current >= page.pages()) {
                    break;
                }
                current++;
            }
            KeywordIndexRebuildResult result = accumulator.toResult();
            recordRebuildResult(observationScope, result);
            return result;
        } finally {
            closeObservation(observationScope);
        }
    }

    private void rebuildOneDocument(KnowledgeDocumentDetail document, RebuildAccumulator accumulator) {
        if (document == null || !hasText(document.getId()) || !hasText(document.getKbId())) {
            accumulator.skippedDocuments++;
            return;
        }
        accumulator.processedDocuments++;
        try {
            // 先删除再写入，避免历史残留 chunk 在搜索后端中继续可见。
            keywordIndexPort.deleteDocumentChunks(document.getKbId(), document.getId());
            accumulator.deletedDocuments++;
            if (Boolean.FALSE.equals(document.getEnabled())) {
                accumulator.skippedDocuments++;
                return;
            }
            List<KnowledgeChunkRecord> chunks = documentRepositoryPort.listEnabledChunks(document.getId());
            if (chunks.isEmpty()) {
                accumulator.skippedDocuments++;
                return;
            }
            List<VectorChunk> vectorChunks = chunks.stream()
                    .filter(Objects::nonNull)
                    .map(chunk -> toVectorChunk(document, chunk))
                    .toList();
            keywordIndexPort.indexDocumentChunks(document.getKbId(), document.getId(), vectorChunks);
            accumulator.indexedDocuments++;
            accumulator.indexedChunks += vectorChunks.size();
        } catch (RuntimeException ex) {
            accumulator.failedDocuments++;
            accumulator.failures.add(document.getId() + ": " + Objects.requireNonNullElse(ex.getMessage(), ""));
        }
    }

    private KnowledgeDocumentDetail requireDocument(String docId) {
        String safeDocId = requireText(docId, "docId");
        return documentRepositoryPort.findDetailById(safeDocId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + safeDocId));
    }

    private VectorChunk toVectorChunk(KnowledgeDocumentDetail document, KnowledgeChunkRecord record) {
        VectorChunk chunk = new VectorChunk();
        chunk.setChunkId(record.getId());
        chunk.setIndex(record.getChunkIndex());
        chunk.setContent(Objects.requireNonNullElse(record.getContent(), ""));
        chunk.setMetadata(systemMetadata(document, record));
        return chunk;
    }

    private Map<String, Object> systemMetadata(KnowledgeDocumentDetail document, KnowledgeChunkRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(record.getMetadata(), Map.of()));
        // 补偿重建先保留已治理的业务 metadata，再用文档快照覆盖系统字段，避免旧值继续进入搜索后端。
        metadata.put("kb_id", document.getKbId());
        metadata.put("doc_id", document.getId());
        metadata.put("doc_name", document.getDocName());
        metadata.put("collection_name", document.getCollectionName());
        metadata.put("chunk_index", record.getChunkIndex());
        metadata.put("enabled", record.getEnabled() == null || record.getEnabled() == 1);
        putIfPresent(metadata, "file_type", document.getFileType());
        putIfPresent(metadata, "source_type", document.getSourceType());
        putIfPresent(metadata, "created_at", document.getCreateTime());
        putIfPresent(metadata, "updated_at", document.getUpdateTime());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            return;
        }
        metadata.put(key, value);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ObservationScope startObservation(String scope) {
        if (observationPort == null) {
            return null;
        }
        try {
            return observationPort.start(new ObservationCommand(
                    OBSERVATION_REBUILD, "", Map.of("scope", scope)));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void recordRebuildResult(ObservationScope scope, KeywordIndexRebuildResult result) {
        String eventName = result.success() ? EVENT_REBUILD_SUCCESS : EVENT_REBUILD_FAILURE;
        Map<String, String> attributes = Map.of(
                "scope", result.scope(),
                "status", result.success() ? "success" : "failure");
        recordObservationEvent(scope, eventName, attributes);
    }

    private void recordObservationEvent(ObservationScope scope, String name, Map<String, String> attributes) {
        if (scope == null) {
            return;
        }
        try {
            scope.recordEvent(new ObservationEvent(name, null, attributes));
        } catch (RuntimeException ex) {
            // 观测失败不能影响索引补偿主流程。
        }
    }

    private void closeObservation(ObservationScope scope) {
        if (scope == null) {
            return;
        }
        try {
            scope.close();
        } catch (RuntimeException ex) {
            // 观测资源关闭失败不应覆盖重建结果。
        }
    }

    private static final class RebuildAccumulator {

        private final String scope;
        private final String targetId;
        private int processedDocuments;
        private int indexedDocuments;
        private int indexedChunks;
        private int deletedDocuments;
        private int skippedDocuments;
        private int failedDocuments;
        private final List<String> failures = new ArrayList<>();

        private RebuildAccumulator(String scope, String targetId) {
            this.scope = scope;
            this.targetId = targetId;
        }

        private KeywordIndexRebuildResult toResult() {
            return new KeywordIndexRebuildResult(scope, targetId, processedDocuments, indexedDocuments,
                    indexedChunks, deletedDocuments, skippedDocuments, failedDocuments, failures);
        }
    }
}
