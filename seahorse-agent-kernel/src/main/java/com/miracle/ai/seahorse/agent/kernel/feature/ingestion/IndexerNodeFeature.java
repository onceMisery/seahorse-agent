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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;

import java.util.List;
import java.util.Objects;

/**
 * Seahorse 原生索引节点 Feature。
 *
 * <p>该节点负责校验 chunk embedding、确保向量集合存在，并将 chunk 元数据与向量索引分别写入
 * repository/vector 端口。
 */
public class IndexerNodeFeature implements IngestionNodeFeature {

    private static final String NODE_TYPE = "indexer";
    private static final String KEY_COLLECTION_NAME = "collectionName";
    private static final String KEY_KB_ID = "kbId";
    private static final String KEY_DOC_ID = "docId";

    private final VectorCollectionAdminPort collectionAdminPort;
    private final VectorIndexPort vectorIndexPort;
    private final KnowledgeChunkRepositoryPort chunkRepositoryPort;

    public IndexerNodeFeature(VectorCollectionAdminPort collectionAdminPort,
                              VectorIndexPort vectorIndexPort,
                              KnowledgeChunkRepositoryPort chunkRepositoryPort) {
        this.collectionAdminPort = Objects.requireNonNull(collectionAdminPort, "collectionAdminPort must not be null");
        this.vectorIndexPort = Objects.requireNonNull(vectorIndexPort, "vectorIndexPort must not be null");
        this.chunkRepositoryPort = Objects.requireNonNull(chunkRepositoryPort, "chunkRepositoryPort must not be null");
    }

    @Override
    public String name() {
        return NODE_TYPE;
    }

    @Override
    public String nodeType() {
        return NODE_TYPE;
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        List<VectorChunk> chunks = safeContext.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return NodeResult.fail(new IllegalArgumentException("没有可索引的分块"));
        }
        try {
            IndexerRequest request = resolveRequest(safeContext, config);
            validateEmbeddings(chunks);
            collectionAdminPort.ensureCollection(request.collectionName());
            if (!safeContext.isSkipIndexerWrite()) {
                chunkRepositoryPort.replaceDocumentChunks(request.kbId(), request.docId(), chunks);
                vectorIndexPort.indexDocumentChunks(request.collectionName(), request.docId(), chunks);
            }
            return NodeResult.ok("已准备 " + chunks.size() + " 个分块索引");
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private IndexerRequest resolveRequest(IngestionContext context, NodeConfig config) {
        JsonNode settings = config == null ? null : config.getSettings();
        String collectionName = firstText(setting(settings, KEY_COLLECTION_NAME), metadata(context, KEY_COLLECTION_NAME));
        String kbId = firstText(setting(settings, KEY_KB_ID), metadata(context, KEY_KB_ID));
        String docId = firstText(setting(settings, KEY_DOC_ID), context.getTaskId());
        return new IndexerRequest(
                requireText(collectionName, "collectionName"),
                requireText(kbId, "kbId"),
                requireText(docId, "docId"));
    }

    private void validateEmbeddings(List<VectorChunk> chunks) {
        int expectedDimension = 0;
        for (VectorChunk chunk : chunks) {
            float[] embedding = requireEmbedding(chunk);
            expectedDimension = resolveExpectedDimension(expectedDimension, embedding);
        }
    }

    private float[] requireEmbedding(VectorChunk chunk) {
        VectorChunk safeChunk = Objects.requireNonNull(chunk, "chunk must not be null");
        float[] embedding = safeChunk.getEmbedding();
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("向量结果缺失");
        }
        if (!hasText(safeChunk.getChunkId())) {
            throw new IllegalArgumentException("chunkId 不能为空");
        }
        return embedding;
    }

    private int resolveExpectedDimension(int expectedDimension, float[] embedding) {
        if (expectedDimension == 0) {
            return embedding.length;
        }
        if (expectedDimension != embedding.length) {
            throw new IllegalArgumentException("向量维度不一致");
        }
        return expectedDimension;
    }

    private String setting(JsonNode settings, String key) {
        if (settings == null || settings.isNull()) {
            return "";
        }
        JsonNode node = settings.get(key);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String metadata(IngestionContext context, String key) {
        if (context.getMetadata() == null) {
            return "";
        }
        Object value = context.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
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

    private record IndexerRequest(String collectionName, String kbId, String docId) {
    }
}
