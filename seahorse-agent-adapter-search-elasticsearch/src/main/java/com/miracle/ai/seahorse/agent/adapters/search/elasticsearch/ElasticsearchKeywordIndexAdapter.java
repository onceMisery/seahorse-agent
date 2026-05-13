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

package com.miracle.ai.seahorse.agent.adapters.search.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import okhttp3.OkHttpClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Elasticsearch 关键词索引写入适配器。
 *
 * <p>入库链路通过 {@link KeywordIndexPort} 写入 chunk 快照；如果启用 outbox，
 * 消费端也复用同一适配器，避免 kernel 直接依赖搜索中间件。
 */
public class ElasticsearchKeywordIndexAdapter implements KeywordIndexPort {

    private static final String META_TENANT_ID = "tenant_id";
    private static final String META_COLLECTION_NAME = "collection_name";

    private final ObjectMapper objectMapper;
    private final ElasticsearchKeywordProperties properties;
    private final ElasticsearchKeywordHttpClient httpClient;

    public ElasticsearchKeywordIndexAdapter(OkHttpClient httpClient,
                                            ObjectMapper objectMapper,
                                            ElasticsearchKeywordProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = new ElasticsearchKeywordHttpClient(httpClient, properties);
    }

    @Override
    public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
        if (!hasText(kbId) || !hasText(docId) || chunks == null || chunks.isEmpty()) {
            return;
        }
        String body = bulkBody(kbId, docId, chunks);
        if (!body.isBlank()) {
            String response = httpClient.postNdjson("_bulk", body);
            // Elasticsearch bulk 接口可能 HTTP 200 但局部失败，必须显式检查 errors 标记。
            if (response.contains("\"errors\":true")) {
                throw new IllegalStateException("Elasticsearch bulk index failed: " + response);
            }
        }
    }

    @Override
    public void deleteDocumentChunks(String kbId, String docId) {
        if (!hasText(kbId) || !hasText(docId)) {
            return;
        }
        httpClient.postJson("_delete_by_query", toJson(deleteBody(kbId, docId)));
    }

    String bulkBody(String kbId, String docId, List<VectorChunk> chunks) {
        StringBuilder body = new StringBuilder();
        for (VectorChunk chunk : chunks) {
            if (chunk == null || !hasText(chunk.getChunkId())) {
                continue;
            }
            Map<String, Object> action = Map.of("index", Map.of(
                    "_index", properties.indexName(),
                    "_id", chunk.getChunkId()
            ));
            body.append(toJson(action)).append('\n');
            body.append(toJson(document(kbId, docId, chunk))).append('\n');
        }
        return body.toString();
    }

    Map<String, Object> deleteBody(String kbId, String docId) {
        return Map.of("query", Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("kb_id", kbId)),
                Map.of("term", Map.of("doc_id", docId))
        ))));
    }

    private Map<String, Object> document(String kbId, String docId, VectorChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(chunk.getMetadata(), Map.of()));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("chunk_id", chunk.getChunkId());
        document.put("kb_id", kbId);
        document.put("doc_id", docId);
        document.put("chunk_index", chunk.getIndex());
        document.put("content", Objects.requireNonNullElse(chunk.getContent(), ""));
        document.put("metadata", metadata);
        document.put("tenant_id", metadata.get(META_TENANT_ID));
        document.put("collection_name", metadata.get(META_COLLECTION_NAME));
        document.put("enabled", true);
        return document;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize Elasticsearch request", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
