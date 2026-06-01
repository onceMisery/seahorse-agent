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

import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.CreateKnowledgeBaseCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBasePageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.UpdateKnowledgeBaseCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.ChunkStrategy;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeBaseValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生知识库管理服务。
 */
public class KernelKnowledgeBaseService implements KnowledgeBaseInboundPort {

    private static final String DEFAULT_OPERATOR = "";

    private final KnowledgeBaseRepositoryPort repositoryPort;
    private final VectorCollectionAdminPort vectorCollectionAdminPort;
    private final ObjectStoragePort objectStoragePort;

    public KernelKnowledgeBaseService(KnowledgeBaseRepositoryPort repositoryPort,
                                      VectorCollectionAdminPort vectorCollectionAdminPort,
                                      ObjectStoragePort objectStoragePort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.vectorCollectionAdminPort = Objects.requireNonNull(vectorCollectionAdminPort,
                "vectorCollectionAdminPort must not be null");
        this.objectStoragePort = Objects.requireNonNull(objectStoragePort, "objectStoragePort must not be null");
    }

    @Override
    public Long create(CreateKnowledgeBaseCommand command) {
        CreateKnowledgeBaseCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String name = requireText(safeCommand.name(), "name");
        String collectionName = requireText(safeCommand.collectionName(), "collectionName");
        if (repositoryPort.nameExists(normalizeName(name), null)) {
            throw new IllegalStateException("知识库名称已存在：" + name);
        }
        objectStoragePort.ensureBucket(collectionName);
        vectorCollectionAdminPort.ensureCollection(collectionName);
        return repositoryPort.create(new CreateKnowledgeBaseValues(
                name, safeCommand.embeddingModel(), collectionName, operator(safeCommand.operator())));
    }

    @Override
    public void update(Long kbId, UpdateKnowledgeBaseCommand command) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId must not be null");
        }
        UpdateKnowledgeBaseCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        KnowledgeBaseRecord current = queryById(kbId);
        if (hasText(safeCommand.name()) && repositoryPort.nameExists(normalizeName(safeCommand.name()), kbId)) {
            throw new IllegalStateException("知识库名称已存在：" + safeCommand.name());
        }
        if (hasText(safeCommand.embeddingModel())
                && !safeCommand.embeddingModel().equals(current.getEmbeddingModel())
                && repositoryPort.hasVectorizedDocuments(kbId)) {
            throw new IllegalStateException("知识库已存在向量化文档，不允许修改嵌入模型");
        }
        boolean updated = repositoryPort.update(kbId, new KnowledgeBaseUpdateValues(
                safeCommand.name(), safeCommand.embeddingModel(), operator(safeCommand.operator())));
        if (!updated) {
            throw new IllegalArgumentException("知识库不存在：" + kbId);
        }
    }

    @Override
    public void delete(Long kbId, String operator) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId must not be null");
        }
        queryById(kbId);
        if (repositoryPort.hasDocuments(kbId)) {
            throw new IllegalStateException("当前知识库下还有文档，请删除文档");
        }
        if (!repositoryPort.delete(kbId, operator(operator))) {
            throw new IllegalArgumentException("知识库不存在：" + kbId);
        }
    }

    @Override
    public KnowledgeBaseRecord queryById(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId must not be null");
        }
        return repositoryPort.findById(kbId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在：" + kbId));
    }

    @Override
    public KnowledgeBasePage page(KnowledgeBasePageCommand command) {
        KnowledgeBasePageCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        return repositoryPort.page(safeCommand.current(), safeCommand.size(), safeCommand.name());
    }

    @Override
    public List<ChunkStrategy> listChunkStrategies() {
        return List.of(
                new ChunkStrategy("fixed_size", "固定大小", Map.of("chunkSize", 512, "overlapSize", 128)),
                new ChunkStrategy("structure_aware", "语义感知（Markdown友好）",
                        Map.of("targetChars", 1400, "overlapChars", 0, "maxChars", 1800, "minChars", 600)));
    }

    private String normalizeName(String name) {
        return requireText(name, "name").replaceAll("\\s+", "");
    }

    private String operator(String operator) {
        return Objects.requireNonNullElse(operator, DEFAULT_OPERATOR);
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
