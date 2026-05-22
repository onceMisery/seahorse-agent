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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorHit;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VectorSearchScoredMemoryVectorPort implements ScoredMemoryVectorPort {

    public static final String DEFAULT_COLLECTION_NAME = "memory_vectors";

    private static final String METADATA_MEMORY_ID = "memoryId";
    private static final String METADATA_MEMORY_ID_SNAKE = "memory_id";
    private static final String METADATA_USER_ID = "userId";
    private static final String METADATA_USER_ID_SNAKE = "user_id";
    private static final String METADATA_TENANT_ID = "tenantId";
    private static final String METADATA_TENANT_ID_SNAKE = "tenant_id";
    private static final String METADATA_GENERATION_ID = "generationId";
    private static final String METADATA_GENERATION_ID_SNAKE = "generation_id";
    private static final String METADATA_EMBEDDING_MODEL = "embeddingModel";
    private static final String METADATA_EMBEDDING_MODEL_SNAKE = "embedding_model";
    private static final String METADATA_CHUNK_ID = "chunkId";
    private static final String METADATA_COLLECTION_NAME = "collectionName";
    private static final String DEFAULT_TENANT_ID = "default";

    private final VectorSearchPort vectorSearchPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final String collectionName;
    private final String embeddingModel;

    public VectorSearchScoredMemoryVectorPort(VectorSearchPort vectorSearchPort,
                                             EmbeddingModelPort embeddingModelPort,
                                             String collectionName,
                                             String embeddingModel) {
        this.vectorSearchPort = Objects.requireNonNull(vectorSearchPort, "vectorSearchPort must not be null");
        this.embeddingModelPort = Objects.requireNonNullElseGet(embeddingModelPort, EmbeddingModelPort::noop);
        this.collectionName = defaultText(collectionName, DEFAULT_COLLECTION_NAME);
        this.embeddingModel = Objects.requireNonNullElse(embeddingModel, "").trim();
    }

    @Override
    public List<ScoredMemoryVectorHit> search(String userId, String tenantId, String query, int topK) {
        String normalizedUserId = Objects.requireNonNullElse(userId, "").trim();
        String normalizedTenantId = defaultText(tenantId, DEFAULT_TENANT_ID);
        String normalizedQuery = Objects.requireNonNullElse(query, "").trim();
        if (normalizedUserId.isBlank() || normalizedQuery.isBlank()) {
            return List.of();
        }
        List<Float> queryVector = embeddingModelPort.embed(embeddingModel, normalizedQuery);
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> chunks = vectorSearchPort.search(new VectorSearchRequest(
                collectionName,
                normalizedQuery,
                queryVector,
                topK,
                filters(normalizedUserId, normalizedTenantId),
                compiledFilter(normalizedUserId, normalizedTenantId)));
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<ScoredMemoryVectorHit> hits = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            toHit(chunk, normalizedUserId, normalizedTenantId).ifPresent(hits::add);
        }
        return List.copyOf(hits);
    }

    private java.util.Optional<ScoredMemoryVectorHit> toHit(RetrievedChunk chunk, String userId, String tenantId) {
        if (chunk == null) {
            return java.util.Optional.empty();
        }
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(chunk.getMetadata(), Map.of()));
        if (!sameUser(metadata, userId) || !sameTenant(metadata, tenantId)) {
            return java.util.Optional.empty();
        }
        String memoryId = firstText(text(metadata.get(METADATA_MEMORY_ID)),
                text(metadata.get(METADATA_MEMORY_ID_SNAKE)),
                chunk.getId());
        if (memoryId.isBlank()) {
            return java.util.Optional.empty();
        }
        metadata.putIfAbsent(METADATA_CHUNK_ID, Objects.requireNonNullElse(chunk.getId(), ""));
        if (hasText(chunk.getCollectionName())) {
            metadata.putIfAbsent(METADATA_COLLECTION_NAME, chunk.getCollectionName());
        }
        String generationId = firstText(
                text(metadata.get(METADATA_GENERATION_ID)),
                text(metadata.get(METADATA_GENERATION_ID_SNAKE)));
        String hitEmbeddingModel = firstText(
                text(metadata.get(METADATA_EMBEDDING_MODEL)),
                text(metadata.get(METADATA_EMBEDDING_MODEL_SNAKE)),
                embeddingModel);
        return java.util.Optional.of(new ScoredMemoryVectorHit(
                memoryId,
                score(chunk),
                generationId,
                hitEmbeddingModel,
                metadata));
    }

    private boolean sameUser(Map<String, Object> metadata, String userId) {
        String indexedUserId = firstText(text(metadata.get(METADATA_USER_ID)), text(metadata.get(METADATA_USER_ID_SNAKE)));
        return !indexedUserId.isBlank() && indexedUserId.equals(userId);
    }

    private boolean sameTenant(Map<String, Object> metadata, String tenantId) {
        String indexedTenantId = firstText(
                text(metadata.get(METADATA_TENANT_ID)),
                text(metadata.get(METADATA_TENANT_ID_SNAKE)));
        if (indexedTenantId.isBlank()) {
            return DEFAULT_TENANT_ID.equals(tenantId);
        }
        return indexedTenantId.equals(tenantId);
    }

    private Map<String, Object> filters(String userId, String tenantId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put(METADATA_USER_ID, userId);
        filters.put(METADATA_TENANT_ID, tenantId);
        return filters;
    }

    private CompiledMetadataFilter compiledFilter(String userId, String tenantId) {
        RetrievalFilter filter = new RetrievalFilter(
                SystemRetrievalFilter.builder()
                        .userId(userId)
                        .tenantId(tenantId)
                        .enabledOnly(true)
                        .build(),
                List.of());
        return new CompiledMetadataFilter(filter, new FilterAnd(List.of()), List.of(), List.of());
    }

    private double score(RetrievedChunk chunk) {
        return chunk.getScore() == null ? 0D : chunk.getScore().doubleValue();
    }

    private String defaultText(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : Objects.toString(value, "");
    }
}
