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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Elasticsearch Metadata Schema 索引结构同步适配器。
 *
 * <p>只根据已注册 Schema 生成 mapping，避免 Elasticsearch 在运行期自动扩张动态字段。
 */
public class ElasticsearchMetadataSchemaIndexAdapter implements MetadataSchemaIndexSyncPort {

    private static final String METADATA_OBJECT = "metadata";
    private static final String KEYWORD_SUB_FIELD = "keyword";

    private final ObjectMapper objectMapper;
    private final ElasticsearchKeywordHttpClient httpClient;

    public ElasticsearchMetadataSchemaIndexAdapter(OkHttpClient httpClient,
                                                   ObjectMapper objectMapper,
                                                   ElasticsearchKeywordProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = new ElasticsearchKeywordHttpClient(httpClient, properties);
    }

    @Override
    public void syncField(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        if (!shouldSync(safeField)) {
            return;
        }
        httpClient.putJson("_mapping", toJson(mappingBody(safeField)));
    }

    Map<String, Object> mappingBody(MetadataSchemaFieldRecord field) {
        FieldPath fieldPath = fieldPath(field);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dynamic", "strict");
        body.put("properties", nestedProperties(fieldPath.segments(), leafMapping(field, fieldPath.keywordSubField())));
        return body;
    }

    private boolean shouldSync(MetadataSchemaFieldRecord field) {
        return field.indexed()
                && (MetadataIndexPolicy.SEARCH_KEYWORD.equals(field.indexPolicy())
                || MetadataIndexPolicy.SEARCH_TEXT.equals(field.indexPolicy()));
    }

    private Map<String, Object> nestedProperties(List<String> segments, Map<String, Object> leafMapping) {
        Map<String, Object> rootProperties = new LinkedHashMap<>();
        Map<String, Object> currentProperties = rootProperties;
        for (int i = 0; i < segments.size() - 1; i++) {
            Map<String, Object> objectMapping = new LinkedHashMap<>();
            objectMapping.put("type", "object");
            objectMapping.put("dynamic", "strict");
            Map<String, Object> childProperties = new LinkedHashMap<>();
            objectMapping.put("properties", childProperties);
            currentProperties.put(segments.get(i), objectMapping);
            currentProperties = childProperties;
        }
        currentProperties.put(segments.get(segments.size() - 1), leafMapping);
        return rootProperties;
    }

    private Map<String, Object> leafMapping(MetadataSchemaFieldRecord field, boolean keywordSubField) {
        if (MetadataIndexPolicy.SEARCH_TEXT.equals(field.indexPolicy())) {
            return textMapping();
        }
        if (keywordSubField) {
            return textWithKeywordMapping();
        }
        return exactMapping(field.valueType());
    }

    private Map<String, Object> exactMapping(MetadataValueType valueType) {
        return switch (valueType) {
            case NUMBER, NUMBER_ARRAY -> Map.of("type", "double");
            case BOOLEAN -> Map.of("type", "boolean");
            case DATE_TIME -> Map.of("type", "date");
            case STRING, STRING_ARRAY, ENUM -> Map.of("type", "keyword", "ignore_above", 256);
        };
    }

    private Map<String, Object> textMapping() {
        return Map.of("type", "text");
    }

    private Map<String, Object> textWithKeywordMapping() {
        return Map.of(
                "type", "text",
                "fields", Map.of(KEYWORD_SUB_FIELD, Map.of("type", "keyword", "ignore_above", 256))
        );
    }

    private FieldPath fieldPath(MetadataSchemaFieldRecord field) {
        String searchField = field.backendMapping().searchFieldName();
        if (searchField == null || searchField.isBlank() || searchField.equals(field.fieldKey())) {
            searchField = METADATA_OBJECT + "." + field.fieldKey();
        }
        List<String> segments = sanitizedSegments(searchField);
        boolean keywordSubField = segments.size() > 1
                && KEYWORD_SUB_FIELD.equals(segments.get(segments.size() - 1));
        if (keywordSubField) {
            segments = List.copyOf(segments.subList(0, segments.size() - 1));
        }
        if (segments.isEmpty()) {
            segments = List.of(METADATA_OBJECT, sanitize(field.fieldKey()));
        }
        return new FieldPath(segments, keywordSubField);
    }

    private List<String> sanitizedSegments(String fieldPath) {
        List<String> segments = new ArrayList<>();
        for (String segment : fieldPath.split("\\.")) {
            if (!segment.isBlank()) {
                // 字段路径只能来自 Schema；这里仍做最小清洗，避免非法字符进入 mapping key。
                segments.add(sanitize(segment));
            }
        }
        return segments;
    }

    private String sanitize(String segment) {
        String sanitized = Objects.requireNonNullElse(segment, "").trim()
                .replaceAll("[^A-Za-z0-9_]", "_");
        return sanitized.isBlank() ? "field" : sanitized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize Elasticsearch mapping request", ex);
        }
    }

    private record FieldPath(List<String> segments, boolean keywordSubField) {
    }
}
