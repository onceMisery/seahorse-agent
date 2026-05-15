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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private static final String META_COLLECTION_NAME = "collection_name";
    private static final String META_TENANT_ID = "tenant_id";
    private static final String META_KB_ID = "kb_id";
    private static final String META_DOC_ID = "doc_id";
    private static final String META_CHUNK_ID = "chunk_id";
    private static final String META_CHUNK_INDEX = "chunk_index";
    private static final Set<String> VECTOR_SYSTEM_METADATA_KEYS = Set.of(
            META_TENANT_ID, META_KB_ID, META_DOC_ID, META_CHUNK_ID, META_CHUNK_INDEX, META_COLLECTION_NAME,
            "enabled", "acl_subjects", "security_level", "file_type", "source_type", "created_at", "updated_at");

    private final VectorCollectionAdminPort collectionAdminPort;
    private final VectorIndexPort vectorIndexPort;
    private final KnowledgeChunkRepositoryPort chunkRepositoryPort;
    private final KeywordIndexPort keywordIndexPort;

    public IndexerNodeFeature(VectorCollectionAdminPort collectionAdminPort,
                              VectorIndexPort vectorIndexPort,
                              KnowledgeChunkRepositoryPort chunkRepositoryPort) {
        this(collectionAdminPort, vectorIndexPort, chunkRepositoryPort, KeywordIndexPort.noop());
    }

    public IndexerNodeFeature(VectorCollectionAdminPort collectionAdminPort,
                              VectorIndexPort vectorIndexPort,
                              KnowledgeChunkRepositoryPort chunkRepositoryPort,
                              KeywordIndexPort keywordIndexPort) {
        this.collectionAdminPort = Objects.requireNonNull(collectionAdminPort, "collectionAdminPort must not be null");
        this.vectorIndexPort = Objects.requireNonNull(vectorIndexPort, "vectorIndexPort must not be null");
        this.chunkRepositoryPort = Objects.requireNonNull(chunkRepositoryPort, "chunkRepositoryPort must not be null");
        this.keywordIndexPort = Objects.requireNonNullElse(keywordIndexPort, KeywordIndexPort.noop());
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
                List<VectorChunk> indexedChunks = chunksWithSystemMetadata(safeContext, request, chunks);
                chunkRepositoryPort.replaceDocumentChunks(request.kbId(), request.docId(), indexedChunks);
                vectorIndexPort.indexDocumentChunks(request.collectionName(), request.docId(),
                        vectorIndexChunks(safeContext, request, indexedChunks));
                // 关键词索引保留完整治理后 Chunk；向量索引使用过滤副本，二者共享同一份系统字段快照。
                keywordIndexPort.indexDocumentChunks(request.kbId(), request.docId(), indexedChunks);
            }
            return NodeResult.ok("已准备 " + chunks.size() + " 个分块索引");
        } catch (Exception ex) {
            return NodeResult.fail(ex);
        }
    }

    private List<VectorChunk> chunksWithSystemMetadata(IngestionContext context,
                                                       IndexerRequest request,
                                                       List<VectorChunk> chunks) {
        return chunks.stream()
                .map(chunk -> VectorChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .index(chunk.getIndex())
                        .content(chunk.getContent())
                        .embedding(chunk.getEmbedding())
                        .metadata(systemMetadata(context, request, chunk))
                        .build())
                .toList();
    }

    private Map<String, Object> systemMetadata(IngestionContext context, IndexerRequest request, VectorChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(chunk.getMetadata(), Map.of()));
        putIfPresent(metadata, META_TENANT_ID, firstValue(metadata.get(META_TENANT_ID), tenantId(context)));
        putIfPresent(metadata, META_COLLECTION_NAME, firstValue(metadata.get(META_COLLECTION_NAME), request.collectionName()));
        putIfPresent(metadata, META_KB_ID, request.kbId());
        putIfPresent(metadata, META_DOC_ID, request.docId());
        putIfPresent(metadata, META_CHUNK_ID, chunk.getChunkId());
        putIfPresent(metadata, META_CHUNK_INDEX, chunk.getIndex());
        putIfPresent(metadata, "enabled", firstValue(metadata.get("enabled"), true));
        return metadata;
    }

    private List<VectorChunk> vectorIndexChunks(IngestionContext context, IndexerRequest request, List<VectorChunk> chunks) {
        return chunks.stream()
                .map(chunk -> VectorChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .index(chunk.getIndex())
                        .content(chunk.getContent())
                        .embedding(chunk.getEmbedding())
                        .metadata(vectorMetadata(context.getMetadataSchema(), request, chunk))
                        .build())
                .toList();
    }

    private Map<String, Object> vectorMetadata(MetadataSchema schema, IndexerRequest request, VectorChunk chunk) {
        Map<String, Object> source = Objects.requireNonNullElse(chunk.getMetadata(), Map.of());
        Map<String, Object> metadata = new LinkedHashMap<>();
        // 向量库只携带可下推过滤或权限兜底需要的字段，避免未声明动态 metadata 直接进入检索后端。
        VECTOR_SYSTEM_METADATA_KEYS.forEach(key -> putIfPresent(metadata, key, source.get(key)));
        putIfPresent(metadata, META_COLLECTION_NAME, firstValue(source.get(META_COLLECTION_NAME), request.collectionName()));
        putIfPresent(metadata, META_KB_ID, firstValue(source.get(META_KB_ID), request.kbId()));
        putIfPresent(metadata, META_DOC_ID, firstValue(source.get(META_DOC_ID), request.docId()));
        putIfPresent(metadata, META_CHUNK_ID, firstValue(source.get(META_CHUNK_ID), chunk.getChunkId()));
        putIfPresent(metadata, META_CHUNK_INDEX, firstValue(source.get(META_CHUNK_INDEX), chunk.getIndex()));
        if (schema != null && !schema.empty()) {
            for (MetadataFieldDescriptor field : schema.fields()) {
                if (field.backendMapping().pushdownToVector()) {
                    String key = canonicalKey(field);
                    putIfPresent(metadata, key, source.get(key));
                }
            }
        }
        return metadata;
    }

    private String canonicalKey(MetadataFieldDescriptor field) {
        String mapped = field.backendMapping().canonicalName();
        return hasText(mapped) ? mapped.trim() : field.fieldKey();
    }

    private String tenantId(IngestionContext context) {
        MetadataSchema schema = context.getMetadataSchema();
        if (schema != null && hasText(schema.tenantId())) {
            return schema.tenantId();
        }
        return firstText(metadataText(context, META_TENANT_ID), metadataText(context, "tenantId"));
    }

    private Object firstValue(Object first, Object second) {
        return present(first) ? first : second;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : Objects.requireNonNullElse(second, "").trim();
    }

    private String metadataText(IngestionContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (!hasText(key) || !present(value)) {
            return;
        }
        metadata.put(key, value);
    }

    private boolean present(Object value) {
        return value != null && !(value instanceof String text && text.isBlank());
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
