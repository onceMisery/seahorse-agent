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

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.CreateKnowledgeChunkCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeChunkCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeChunkValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentChunkContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.UpdateKnowledgeChunkValues;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;

import java.util.List;
import java.util.Objects;

/**
 * Seahorse 原生知识库 Chunk 管理服务。
 */
public class KernelKnowledgeChunkService implements KnowledgeChunkInboundPort {

    private static final String STATUS_RUNNING = "running";
    private static final int ENABLED_VALUE = 1;
    private static final int MAX_BATCH_SIZE = 500;

    private final KnowledgeChunkRepositoryPort chunkRepositoryPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final VectorIndexPort vectorIndexPort;

    public KernelKnowledgeChunkService(KnowledgeChunkRepositoryPort chunkRepositoryPort,
                                       EmbeddingModelPort embeddingModelPort,
                                       VectorIndexPort vectorIndexPort) {
        this.chunkRepositoryPort = Objects.requireNonNull(chunkRepositoryPort, "chunkRepositoryPort must not be null");
        this.embeddingModelPort = Objects.requireNonNull(embeddingModelPort, "embeddingModelPort must not be null");
        this.vectorIndexPort = Objects.requireNonNull(vectorIndexPort, "vectorIndexPort must not be null");
    }

    @Override
    public KnowledgeChunkPage page(String docId, KnowledgeChunkPageCommand command) {
        requireContext(docId);
        KnowledgeChunkPageCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        return chunkRepositoryPort.page(docId, safeCommand.current(), safeCommand.size(), safeCommand.enabled());
    }

    @Override
    public KnowledgeChunkRecord create(String docId, CreateKnowledgeChunkCommand command) {
        KnowledgeDocumentChunkContext context = requireEditableContext(docId);
        if (context.enabled() != ENABLED_VALUE) {
            throw new IllegalStateException("文档未启用，暂不支持新增 Chunk");
        }
        CreateKnowledgeChunkCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        KnowledgeChunkRecord record = chunkRepositoryPort.create(docId, new CreateKnowledgeChunkValues(
                safeCommand.chunkId(), requireText(safeCommand.content(), "content"),
                safeCommand.index(), safeCommand.operator()));
        indexChunk(context, record);
        return record;
    }

    @Override
    public void update(String docId, String chunkId, UpdateKnowledgeChunkCommand command) {
        KnowledgeDocumentChunkContext context = requireEditableContext(docId);
        UpdateKnowledgeChunkCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        KnowledgeChunkRecord current = requireChunk(docId, chunkId);
        String content = requireText(safeCommand.content(), "content");
        if (content.equals(current.getContent())) {
            return;
        }
        if (!chunkRepositoryPort.update(docId, chunkId, new UpdateKnowledgeChunkValues(content, safeCommand.operator()))) {
            throw new IllegalArgumentException("Chunk 不存在：" + chunkId);
        }
        KnowledgeChunkRecord updated = requireChunk(docId, chunkId);
        vectorIndexPort.updateChunk(context.collectionName(), docId, toVectorChunk(context, updated));
    }

    @Override
    public void delete(String docId, String chunkId) {
        KnowledgeDocumentChunkContext context = requireEditableContext(docId);
        requireChunk(docId, chunkId);
        if (!chunkRepositoryPort.delete(docId, chunkId)) {
            throw new IllegalArgumentException("Chunk 不存在：" + chunkId);
        }
        vectorIndexPort.deleteChunkById(context.collectionName(), chunkId);
    }

    @Override
    public void enable(String docId, String chunkId, boolean enabled, String operator) {
        KnowledgeDocumentChunkContext context = requireEditableContext(docId);
        validateDocumentEnabled(context, enabled);
        KnowledgeChunkRecord current = requireChunk(docId, chunkId);
        if (isEnabled(current) == enabled) {
            return;
        }
        chunkRepositoryPort.updateEnabled(docId, List.of(chunkId), enabled, operator);
        if (enabled) {
            indexChunk(context, requireChunk(docId, chunkId));
        } else {
            vectorIndexPort.deleteChunkById(context.collectionName(), chunkId);
        }
    }

    @Override
    public void batchEnable(String docId, List<String> chunkIds, boolean enabled, String operator) {
        KnowledgeDocumentChunkContext context = requireEditableContext(docId);
        validateDocumentEnabled(context, enabled);
        List<String> requestedChunkIds = chunkIds == null ? List.of() : chunkIds;
        List<String> safeChunkIds = requestedChunkIds.stream()
                .filter(this::hasText)
                .toList();
        if (safeChunkIds.isEmpty()) {
            throw new IllegalArgumentException("请指定需要操作的 Chunk");
        }
        if (safeChunkIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次批量操作 Chunk 数量不能超过 " + MAX_BATCH_SIZE);
        }
        List<KnowledgeChunkRecord> foundChunks = chunkRepositoryPort.findChunksByIds(docId, safeChunkIds);
        if (foundChunks.size() != safeChunkIds.size()) {
            throw new IllegalArgumentException("存在无效的 Chunk ID");
        }
        List<KnowledgeChunkRecord> needUpdate = foundChunks.stream()
                .filter(chunk -> isEnabled(chunk) != enabled)
                .toList();
        if (needUpdate.isEmpty()) {
            return;
        }
        List<String> needUpdateIds = needUpdate.stream().map(KnowledgeChunkRecord::getId).toList();
        chunkRepositoryPort.updateEnabled(docId, needUpdateIds, enabled, operator);
        if (enabled) {
            List<KnowledgeChunkRecord> updatedChunks = chunkRepositoryPort.findChunksByIds(docId, needUpdateIds);
            vectorIndexPort.indexDocumentChunks(context.collectionName(), docId,
                    updatedChunks.stream().map(chunk -> toVectorChunk(context, chunk)).toList());
        } else {
            vectorIndexPort.deleteChunksByIds(context.collectionName(), needUpdateIds);
        }
    }

    private KnowledgeDocumentChunkContext requireEditableContext(String docId) {
        KnowledgeDocumentChunkContext context = requireContext(docId);
        if (STATUS_RUNNING.equalsIgnoreCase(context.status())) {
            throw new IllegalStateException("文档正在分块处理中，暂不支持修改 Chunk");
        }
        return context;
    }

    private KnowledgeDocumentChunkContext requireContext(String docId) {
        return chunkRepositoryPort.findDocumentContext(requireText(docId, "docId"))
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + docId));
    }

    private KnowledgeChunkRecord requireChunk(String docId, String chunkId) {
        return chunkRepositoryPort.findChunk(requireText(docId, "docId"), requireText(chunkId, "chunkId"))
                .orElseThrow(() -> new IllegalArgumentException("Chunk 不存在：" + chunkId));
    }

    private void validateDocumentEnabled(KnowledgeDocumentChunkContext context, boolean enableChunk) {
        if (enableChunk && context.enabled() != ENABLED_VALUE) {
            throw new IllegalStateException("文档未启用，无法启用 Chunk，请先启用文档");
        }
    }

    private void indexChunk(KnowledgeDocumentChunkContext context, KnowledgeChunkRecord record) {
        vectorIndexPort.indexDocumentChunks(context.collectionName(), context.docId(),
                List.of(toVectorChunk(context, record)));
    }

    private VectorChunk toVectorChunk(KnowledgeDocumentChunkContext context, KnowledgeChunkRecord record) {
        VectorChunk chunk = new VectorChunk();
        chunk.setChunkId(record.getId());
        chunk.setContent(record.getContent());
        chunk.setIndex(record.getChunkIndex());
        chunk.setEmbedding(toArray(embeddingModelPort.embed(context.embeddingModel(), record.getContent())));
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

    private boolean isEnabled(KnowledgeChunkRecord record) {
        return Integer.valueOf(ENABLED_VALUE).equals(record.getEnabled());
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
}
