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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldExists;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldIn;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldNe;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldRange;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import okhttp3.OkHttpClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Elasticsearch BM25 的关键词检索适配器。
 *
 * <p>动态 metadata 只消费 {@link KeywordSearchRequest#compiledFilter()} 的 AST，
 * 禁止在 adapter 内重新解析用户原始过滤 Map。
 */
public class ElasticsearchKeywordSearchAdapter implements KeywordSearchPort {

    private static final String META_TENANT_ID = "tenant_id";
    private static final String META_KB_ID = "kb_id";
    private static final String META_DOC_ID = "doc_id";
    private static final String META_CHUNK_INDEX = "chunk_index";
    private static final String META_ACL_SUBJECTS = "acl_subjects";
    private static final String FIELD_ACL_SUBJECT_IDS = "acl_subject_ids";

    private final ObjectMapper objectMapper;
    private final ElasticsearchKeywordProperties properties;
    private final ElasticsearchKeywordHttpClient httpClient;

    public ElasticsearchKeywordSearchAdapter(OkHttpClient httpClient,
                                             ObjectMapper objectMapper,
                                             ElasticsearchKeywordProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.httpClient = new ElasticsearchKeywordHttpClient(httpClient, properties);
    }

    @Override
    public List<RetrievedChunk> search(KeywordSearchRequest request) {
        KeywordSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (safeRequest.query().isBlank()) {
            return List.of();
        }
        String response = httpClient.postJson("_search", toJson(searchBody(safeRequest)));
        return parseResponse(response);
    }

    Map<String, Object> searchBody(KeywordSearchRequest request) {
        Map<String, Object> bool = new LinkedHashMap<>();
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", request.query().trim());
        multiMatch.put("fields", properties.searchFields());
        // analyzer 与 minimum_should_match 属于 ES 查询调优项，只在 adapter 内消费配置。
        if (hasText(properties.analyzer())) {
            multiMatch.put("analyzer", properties.analyzer());
        }
        if (hasText(properties.minimumShouldMatch())) {
            multiMatch.put("minimum_should_match", properties.minimumShouldMatch());
        }
        bool.put("must", List.of(Map.of("multi_match", multiMatch)));
        List<Object> filters = new ArrayList<>();
        appendSystemFilters(filters, request.compiledFilter().sourceFilter().system());
        appendMetadataFilter(filters, request.compiledFilter().expression());
        bool.put("filter", filters);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", request.topK());
        body.put("query", Map.of("bool", bool));
        body.put("_source", List.of("chunk_id", "kb_id", "doc_id", "chunk_index", "content",
                "metadata", "tenant_id", "collection_name", FIELD_ACL_SUBJECT_IDS,
                "file_type", "source_type", "created_at", "updated_at", "enabled"));
        Map<String, Object> highlight = highlight();
        if (!highlight.isEmpty()) {
            body.put("highlight", highlight);
        }
        return body;
    }

    private Map<String, Object> highlight() {
        List<String> fields = highlightFields();
        if (fields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> highlightFields = new LinkedHashMap<>();
        for (String field : fields) {
            highlightFields.put(field, Map.of("fragment_size", 160, "number_of_fragments", 3));
        }
        return Map.of(
                "pre_tags", List.of("<em>"),
                "post_tags", List.of("</em>"),
                "fields", highlightFields);
    }

    private List<String> highlightFields() {
        return properties.searchFields().stream()
                .map(this::stripBoost)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String stripBoost(String field) {
        String safeField = Objects.requireNonNullElse(field, "").trim();
        int boostIndex = safeField.indexOf('^');
        return boostIndex > 0 ? safeField.substring(0, boostIndex) : safeField;
    }

    private void appendSystemFilters(List<Object> filters, SystemRetrievalFilter filter) {
        if (filter == null) {
            filters.add(term("enabled", true));
            return;
        }
        if (filter.enabledOnly()) {
            filters.add(term("enabled", true));
        }
        if (!filter.tenantId().isBlank()) {
            filters.add(term("tenant_id", filter.tenantId()));
        }
        appendTerms(filters, "kb_id", filter.knowledgeBaseIds());
        appendTerms(filters, "doc_id", filter.documentIds());
        appendTerms(filters, "collection_name", filter.collectionNames());
        appendTerms(filters, FIELD_ACL_SUBJECT_IDS, filter.aclSubjectIds());
        appendTerms(filters, "file_type", filter.fileTypes());
        appendTerms(filters, "source_type", filter.sourceTypes());
        appendRange(filters, "created_at", filter.createdFrom(), filter.createdTo());
        appendRange(filters, "updated_at", filter.updatedFrom(), filter.updatedTo());
    }

    private void appendMetadataFilter(List<Object> filters, MetadataFilterExpr expression) {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterAnd and) {
            for (MetadataFilterExpr child : and.children()) {
                appendMetadataFilter(filters, child);
            }
            return;
        }
        if (expression instanceof FieldEq eq) {
            filters.add(term(searchField(eq.field()), normalizeValue(eq.value())));
        } else if (expression instanceof FieldNe ne) {
            filters.add(Map.of("bool", Map.of("must_not", List.of(term(searchField(ne.field()), ne.value())))));
        } else if (expression instanceof FieldIn in) {
            filters.add(Map.of("terms", Map.of(searchField(in.field()), in.values())));
        } else if (expression instanceof FieldRange range) {
            appendRange(filters, searchField(range.field()), range.from(), range.to());
        } else if (expression instanceof FieldContains contains) {
            filters.add(Map.of("match", Map.of(searchField(contains.field()), normalizeValue(contains.value()))));
        } else if (expression instanceof FieldExists exists) {
            filters.add(Map.of("exists", Map.of("field", searchField(exists.field()))));
        }
    }

    private void appendTerms(List<Object> filters, String field, List<String> values) {
        if (values != null && !values.isEmpty()) {
            filters.add(Map.of("terms", Map.of(field, values)));
        }
    }

    private void appendRange(List<Object> filters, String field, Object from, Object to) {
        Map<String, Object> range = new LinkedHashMap<>();
        if (from != null) {
            range.put("gte", normalizeValue(from));
        }
        if (to != null) {
            range.put("lte", normalizeValue(to));
        }
        if (!range.isEmpty()) {
            filters.add(Map.of("range", Map.of(field, range)));
        }
    }

    private Map<String, Object> term(String field, Object value) {
        return Map.of("term", Map.of(field, normalizeValue(value)));
    }

    private String searchField(MetadataFieldDescriptor field) {
        String mapped = field.backendMapping().searchFieldName();
        if (mapped != null && !mapped.isBlank() && !mapped.equals(field.fieldKey())) {
            return mapped;
        }
        // 默认 BackendFieldMapping 只保存逻辑字段名；ES 文档里的动态业务字段统一放在 metadata 对象下。
        return "metadata." + field.fieldKey();
    }

    private Object normalizeValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<RetrievedChunk> parseResponse(String response) {
        try {
            JsonNode hits = objectMapper.readTree(response).path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                return List.of();
            }
            List<RetrievedChunk> results = new ArrayList<>();
            for (JsonNode hit : hits) {
                results.add(toRetrievedChunk(hit));
            }
            return results;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("invalid Elasticsearch search response", ex);
        }
    }

    private RetrievedChunk toRetrievedChunk(JsonNode hit) throws JsonProcessingException {
        JsonNode source = hit.path("_source");
        Map<String, Object> metadata = metadata(source.path("metadata"));
        String kbId = text(source, "kb_id");
        String docId = text(source, "doc_id");
        Integer chunkIndex = source.hasNonNull("chunk_index") ? source.path("chunk_index").asInt() : null;
        metadata.putIfAbsent(META_KB_ID, kbId);
        metadata.putIfAbsent(META_DOC_ID, docId);
        metadata.putIfAbsent(META_CHUNK_INDEX, chunkIndex);
        // 顶层字段是检索过滤入口，回填到 metadata 便于后续兜底过滤和审计链路复用。
        putIfPresent(metadata, META_ACL_SUBJECTS, value(source, FIELD_ACL_SUBJECT_IDS));
        putIfPresent(metadata, "file_type", value(source, "file_type"));
        putIfPresent(metadata, "source_type", value(source, "source_type"));
        putIfPresent(metadata, "created_at", value(source, "created_at"));
        putIfPresent(metadata, "updated_at", value(source, "updated_at"));
        metadata.putIfAbsent("enabled", source.path("enabled").asBoolean(true));
        Map<String, Object> keywordHighlights = highlights(hit.path("highlight"));
        if (!keywordHighlights.isEmpty()) {
            // 高亮片段是 ES 通道的展示型结果，放入 metadata 透传，不参与过滤编译。
            metadata.put("keywordHighlights", keywordHighlights);
        }
        return RetrievedChunk.builder()
                .id(firstText(source, "chunk_id", hit.path("_id").asText("")))
                .text(text(source, "content"))
                .score(hit.hasNonNull("_score") ? (float) hit.path("_score").asDouble() : null)
                .tenantId(firstText(source, "tenant_id", Objects.toString(metadata.get(META_TENANT_ID), null)))
                .kbId(kbId)
                .docId(docId)
                .collectionName(text(source, "collection_name"))
                .chunkIndex(chunkIndex)
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> metadata(JsonNode node) throws JsonProcessingException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
        }));
    }

    private Map<String, Object> highlights(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> highlights = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            JsonNode fragments = entry.getValue();
            if (fragments != null && fragments.isArray() && !fragments.isEmpty()) {
                List<String> values = new ArrayList<>();
                fragments.forEach(fragment -> values.add(fragment.asText()));
                highlights.put(entry.getKey(), values);
            }
        });
        return highlights;
    }

    private String firstText(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Object value(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !metadata.containsKey(key)) {
            metadata.put(key, value);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize Elasticsearch request", ex);
        }
    }
}
