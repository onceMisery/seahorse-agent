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

package com.miracle.ai.seahorse.agent.adapters.vector.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldExists;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldIn;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldRange;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Milvus 向量库 adapter。
 *
 * <p>该实现保持 legacy Milvus 表结构约定，collection 内字段固定为
 * {@code id/content/metadata/embedding}，从而保证默认 RAG 检索和入库行为不发生结构性变化。
 */
public class MilvusVectorAdapter implements VectorSearchPort, VectorIndexPort, VectorCollectionAdminPort {

    private static final Gson GSON = new Gson();
    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_METADATA = "metadata";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String META_COLLECTION_NAME = "collection_name";
    private static final String META_TENANT_ID = "tenant_id";
    private static final String META_KB_ID = "kb_id";
    private static final String META_DOC_ID = "doc_id";
    private static final String META_CHUNK_INDEX = "chunk_index";
    private static final String META_ENABLED = "enabled";
    private static final int CONTENT_MAX_LENGTH = 65535;

    private final MilvusClientV2 milvusClient;
    private final MilvusVectorProperties properties;

    public MilvusVectorAdapter(MilvusClientV2 milvusClient, MilvusVectorProperties properties) {
        this.milvusClient = Objects.requireNonNull(milvusClient, "milvusClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public List<RetrievedChunk> search(VectorSearchRequest request) {
        VectorSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (safeRequest.vector().isEmpty()) {
            return List.of();
        }
        SearchResp response = milvusClient.search(searchRequest(safeRequest));
        List<List<SearchResp.SearchResult>> results = response.getSearchResults();
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.get(0).stream()
                .map(this::retrievedChunk)
                .toList();
    }

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        List<VectorChunk> safeChunks = requireChunks(chunks);
        List<JsonObject> rows = new ArrayList<>(safeChunks.size());
        for (VectorChunk chunk : safeChunks) {
            rows.add(row(collectionName, docId, chunk));
        }
        milvusClient.insert(InsertReq.builder()
                .collectionName(resolveCollection(collectionName))
                .data(rows)
                .build());
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        VectorChunk safeChunk = Objects.requireNonNull(chunk, "chunk must not be null");
        milvusClient.upsert(UpsertReq.builder()
                .collectionName(resolveCollection(collectionName))
                .data(List.of(row(collectionName, docId, safeChunk)))
                .build());
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        milvusClient.delete(DeleteReq.builder()
                .collectionName(resolveCollection(collectionName))
                .filter(FIELD_METADATA + "[\"" + META_DOC_ID + "\"] == \"" + requireText(docId, "docId") + "\"")
                .build());
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        milvusClient.delete(DeleteReq.builder()
                .collectionName(resolveCollection(collectionName))
                .filter(FIELD_ID + " == \"" + requireText(chunkId, "chunkId") + "\"")
                .build());
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String idList = chunkIds.stream()
                .filter(Objects::nonNull)
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        if (idList.isBlank()) {
            return;
        }
        milvusClient.delete(DeleteReq.builder()
                .collectionName(resolveCollection(collectionName))
                .filter(FIELD_ID + " in [" + idList + "]")
                .build());
    }

    @Override
    public boolean collectionExists(String collectionName) {
        return Boolean.TRUE.equals(milvusClient.hasCollection(HasCollectionReq.builder()
                .collectionName(resolveCollection(collectionName))
                .build()));
    }

    @Override
    public void ensureCollection(String collectionName) {
        String collection = resolveCollection(collectionName);
        if (collectionExists(collection)) {
            return;
        }
        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .collectionSchema(collectionSchema())
                .primaryFieldName(FIELD_ID)
                .vectorFieldName(FIELD_EMBEDDING)
                .metricType(properties.metricType())
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .indexParams(List.of(indexParam()))
                .build());
    }

    private SearchReq searchRequest(VectorSearchRequest request) {
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(resolveCollection(request.collectionName()))
                .annsField(FIELD_EMBEDDING)
                .data(List.of(new FloatVec(vectorArray(request.vector()))))
                .topK(topK(request.topK()))
                .searchParams(Map.of("metric_type", properties.metricType(), "ef", 128))
                .outputFields(List.of(FIELD_ID, FIELD_CONTENT, FIELD_METADATA));
        String filter = metadataFilter(request);
        if (!filter.isBlank()) {
            builder.filter(filter);
        }
        return builder.build();
    }

    private RetrievedChunk retrievedChunk(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        Map<String, Object> metadata = metadata(entity.get(FIELD_METADATA));
        return RetrievedChunk.builder()
                .id(Objects.toString(entity.get(FIELD_ID), ""))
                .text(Objects.toString(entity.get(FIELD_CONTENT), ""))
                .score(result.getScore())
                .tenantId(string(metadata, META_TENANT_ID))
                .kbId(string(metadata, META_KB_ID))
                .docId(string(metadata, META_DOC_ID))
                .collectionName(string(metadata, META_COLLECTION_NAME))
                .chunkIndex(integer(metadata, META_CHUNK_INDEX))
                .metadata(metadata)
                .build();
    }

    private CreateCollectionReq.CollectionSchema collectionSchema() {
        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_ID)
                .dataType(DataType.VarChar)
                .maxLength(128)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(CONTENT_MAX_LENGTH)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_METADATA)
                .dataType(DataType.JSON)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_EMBEDDING)
                .dataType(DataType.FloatVector)
                .dimension(properties.dimension())
                .build());
        return CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fields)
                .build();
    }

    private IndexParam indexParam() {
        return IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.valueOf(properties.metricType()))
                .indexName(FIELD_EMBEDDING)
                .extraParams(Map.of("M", "48", "efConstruction", "200", "mmap.enabled", "false"))
                .build();
    }

    private JsonObject row(String collectionName, String docId, VectorChunk chunk) {
        float[] vector = requireVector(chunk.getEmbedding());
        JsonObject row = new JsonObject();
        row.addProperty(FIELD_ID, chunkId(chunk));
        row.addProperty(FIELD_CONTENT, truncate(chunk.getContent()));
        row.add(FIELD_METADATA, metadata(collectionName, docId, chunk));
        row.add(FIELD_EMBEDDING, jsonArray(vector));
        return row;
    }

    private JsonObject metadata(String collectionName, String docId, VectorChunk chunk) {
        JsonObject metadata = new JsonObject();
        if (chunk.getMetadata() != null) {
            chunk.getMetadata().forEach((key, value) -> metadata.add(key, GSON.toJsonTree(value)));
        }
        metadata.addProperty(META_COLLECTION_NAME, resolveCollection(collectionName));
        metadata.addProperty(META_DOC_ID, requireText(docId, "docId"));
        metadata.addProperty(META_CHUNK_INDEX, chunk.getIndex());
        return metadata;
    }

    private JsonArray jsonArray(float[] vector) {
        JsonArray array = new JsonArray(vector.length);
        for (float value : vector) {
            array.add(value);
        }
        return array;
    }

    private List<VectorChunk> requireChunks(List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }
        return chunks;
    }

    private float[] requireVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        if (vector.length != properties.dimension()) {
            throw new IllegalArgumentException("embedding dimension mismatch, expected " + properties.dimension());
        }
        return vector;
    }

    private float[] vectorArray(List<Float> vector) {
        float[] array = new float[vector.size()];
        for (int index = 0; index < vector.size(); index++) {
            array[index] = vector.get(index);
        }
        return array;
    }

    private int topK(int topK) {
        return topK <= 0 ? 5 : topK;
    }

    private String chunkId(VectorChunk chunk) {
        String chunkId = chunk.getChunkId();
        if (chunkId == null || chunkId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return chunkId;
    }

    private String truncate(String value) {
        String content = Objects.requireNonNullElse(value, "");
        if (content.length() <= CONTENT_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, CONTENT_MAX_LENGTH);
    }

    private String resolveCollection(String collectionName) {
        String collection = Objects.requireNonNullElse(collectionName, "").trim();
        if (!collection.isBlank()) {
            return collection;
        }
        return requireText(properties.defaultCollection(), "defaultCollection");
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private String metadataFilter(VectorSearchRequest request) {
        List<String> clauses = new ArrayList<>();
        // 字段路径只允许来自系统字段或 Schema 编译后的 AST，避免用户输入直接拼接 Milvus 表达式。
        SystemRetrievalFilter system = request.compiledFilter().sourceFilter().system();
        appendEq(clauses, META_TENANT_ID, system.tenantId());
        appendIn(clauses, META_KB_ID, system.knowledgeBaseIds());
        appendIn(clauses, META_DOC_ID, system.documentIds());
        appendIn(clauses, "file_type", system.fileTypes());
        appendIn(clauses, "source_type", system.sourceTypes());
        if (system.enabledOnly()) {
            clauses.add("(" + metadataPath(META_ENABLED) + " == true || " + metadataPath(META_ENABLED) + " == \"true\")");
        }
        appendExpression(clauses, request.compiledFilter().expression());
        return String.join(" && ", clauses);
    }

    private void appendExpression(List<String> clauses, MetadataFilterExpr expression) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterAnd filterAnd) {
            filterAnd.children().forEach(child -> appendExpression(clauses, child));
        } else if (expression instanceof FieldEq fieldEq) {
            appendEq(clauses, fieldKey(fieldEq.field().backendMapping().canonicalName()), fieldEq.value());
        } else if (expression instanceof FieldIn fieldIn) {
            appendIn(clauses, fieldKey(fieldIn.field().backendMapping().canonicalName()), fieldIn.values());
        } else if (expression instanceof FieldRange fieldRange) {
            String key = fieldKey(fieldRange.field().backendMapping().canonicalName());
            if (fieldRange.from() != null) {
                clauses.add(metadataPath(key) + " >= " + literal(fieldRange.from()));
            }
            if (fieldRange.to() != null) {
                clauses.add(metadataPath(key) + " <= " + literal(fieldRange.to()));
            }
        } else if (expression instanceof FieldContains fieldContains) {
            appendEq(clauses, fieldKey(fieldContains.field().backendMapping().canonicalName()), fieldContains.value());
        } else if (expression instanceof FieldExists fieldExists) {
            clauses.add(metadataPath(fieldKey(fieldExists.field().backendMapping().canonicalName())) + " != null");
        }
    }

    private void appendEq(List<String> clauses, String key, Object value) {
        if (value == null || Objects.toString(value, "").isBlank()) {
            return;
        }
        clauses.add(metadataPath(fieldKey(key)) + " == " + literal(value));
    }

    private void appendIn(List<String> clauses, String key, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String valueList = values.stream().map(this::literal).collect(Collectors.joining(", "));
        clauses.add(metadataPath(fieldKey(key)) + " in [" + valueList + "]");
    }

    private String metadataPath(String key) {
        return FIELD_METADATA + "[\"" + fieldKey(key) + "\"]";
    }

    private String fieldKey(String key) {
        String safeKey = Objects.requireNonNullElse(key, "").trim();
        if (!safeKey.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid metadata field key: " + key);
        }
        return safeKey;
    }

    private String literal(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return Objects.toString(value);
        }
        return "\"" + Objects.toString(value, "").replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            map.forEach((key, item) -> metadata.put(Objects.toString(key), item));
            return metadata;
        }
        if (value instanceof JsonObject jsonObject) {
            return GSON.fromJson(jsonObject, Map.class);
        }
        if (value instanceof JsonElement jsonElement) {
            return GSON.fromJson(jsonElement, Map.class);
        }
        return Map.of();
    }

    private String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : Objects.toString(value);
    }

    private Integer integer(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(Objects.toString(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
