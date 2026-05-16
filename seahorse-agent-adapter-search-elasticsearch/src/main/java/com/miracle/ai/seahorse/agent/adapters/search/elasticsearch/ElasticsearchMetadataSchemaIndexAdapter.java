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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ElasticsearchMetadataSchemaIndexAdapter implements MetadataSchemaIndexSyncPort {

    private static final String METADATA_OBJECT = "metadata";
    private static final String KEYWORD_SUB_FIELD = "keyword";
    private static final String BACKEND = "elasticsearch";
    private static final String EVENT_SCHEMA_INDEX_SYNC_COMPLETED = "metadata.schema.index.sync.completed";
    private static final String EVENT_SCHEMA_INDEX_SYNC_FAILED = "metadata.schema.index.sync.failed";

    private final ObjectMapper objectMapper;
    private final ElasticsearchKeywordHttpClient httpClient;
    private final ObservationPort observationPort;
    private final MetadataSchemaIndexStatusPort indexStatusPort;

    public ElasticsearchMetadataSchemaIndexAdapter(OkHttpClient httpClient,
                                                   ObjectMapper objectMapper,
                                                   ElasticsearchKeywordProperties properties) {
        this(httpClient, objectMapper, properties, null);
    }

    public ElasticsearchMetadataSchemaIndexAdapter(OkHttpClient httpClient,
                                                   ObjectMapper objectMapper,
                                                   ElasticsearchKeywordProperties properties,
                                                   ObservationPort observationPort) {
        this(httpClient, objectMapper, properties, observationPort, null);
    }

    public ElasticsearchMetadataSchemaIndexAdapter(OkHttpClient httpClient,
                                                   ObjectMapper objectMapper,
                                                   ElasticsearchKeywordProperties properties,
                                                   ObservationPort observationPort,
                                                   MetadataSchemaIndexStatusPort indexStatusPort) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = new ElasticsearchKeywordHttpClient(httpClient, properties);
        this.observationPort = observationPort;
        this.indexStatusPort = indexStatusPort;
    }

    @Override
    public void syncField(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        observeIndexSync("CREATE", safeField, () -> syncFieldInternal(safeField));
    }

    @Override
    public void syncFieldChange(MetadataSchemaFieldRecord previousField, MetadataSchemaFieldRecord currentField) {
        MetadataSchemaFieldRecord safePreviousField =
                Objects.requireNonNull(previousField, "previousField must not be null");
        MetadataSchemaFieldRecord safeCurrentField =
                Objects.requireNonNull(currentField, "currentField must not be null");
        observeIndexSync("UPDATE", safeCurrentField,
                () -> syncFieldChangeInternal(safePreviousField, safeCurrentField));
    }

    @Override
    public void deleteField(MetadataSchemaFieldRecord field) {
        MetadataSchemaFieldRecord safeField = Objects.requireNonNull(field, "field must not be null");
        observeIndexSync("DELETE", safeField, () -> deleteFieldInternal(safeField));
    }

    Map<String, Object> mappingBody(MetadataSchemaFieldRecord field) {
        FieldPath fieldPath = fieldPath(field);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dynamic", "strict");
        body.put("properties", nestedProperties(fieldPath.segments(), leafMapping(field, fieldPath.keywordSubField())));
        return body;
    }

    private String syncFieldInternal(MetadataSchemaFieldRecord field) {
        if (!shouldSync(field)) {
            return "SKIPPED";
        }
        httpClient.putJson("_mapping", toJson(mappingBody(field)));
        return "APPLIED";
    }

    private String syncFieldChangeInternal(MetadataSchemaFieldRecord previousField,
                                           MetadataSchemaFieldRecord currentField) {
        boolean previousSyncable = shouldSync(previousField);
        boolean currentSyncable = shouldSync(currentField);
        if (!currentSyncable) {
            return previousSyncable ? "NO_CHANGE" : "SKIPPED";
        }
        if (!previousSyncable || !sameMappingDefinition(previousField, currentField)) {
            httpClient.putJson("_mapping", toJson(mappingBody(currentField)));
            return "APPLIED";
        }
        return "NO_CHANGE";
    }

    private String deleteFieldInternal(MetadataSchemaFieldRecord field) {
        if (!shouldSync(field)) {
            return "SKIPPED";
        }
        return "NO_CHANGE";
    }

    private void observeIndexSync(String action,
                                  MetadataSchemaFieldRecord field,
                                  IndexSyncOperation operation) {
        try {
            String outcome = operation.run();
            recordSyncStatus(action, field, outcome, null);
            recordObservationEvent(EVENT_SCHEMA_INDEX_SYNC_COMPLETED, action, field, outcome, null);
        } catch (RuntimeException ex) {
            recordSyncStatus(action, field, "FAILED", ex);
            recordObservationEvent(EVENT_SCHEMA_INDEX_SYNC_FAILED, action, field, "FAILED", ex);
        }
    }

    private void recordSyncStatus(String action,
                                  MetadataSchemaFieldRecord field,
                                  String outcome,
                                  RuntimeException error) {
        if (indexStatusPort == null) {
            return;
        }
        try {
            indexStatusPort.recordSyncResult(new MetadataSchemaIndexSyncStatusRecord(
                    field.id(),
                    field.tenantId(),
                    field.knowledgeBaseId(),
                    field.fieldKey(),
                    field.schemaVersion(),
                    BACKEND,
                    Objects.requireNonNullElse(action, ""),
                    Objects.requireNonNullElse(outcome, ""),
                    error == null ? "" : error.getClass().getSimpleName(),
                    error == null ? "" : Objects.requireNonNullElse(error.getMessage(), ""),
                    null));
        } catch (RuntimeException ignored) {
        }
    }

    private void recordObservationEvent(String eventName,
                                        String action,
                                        MetadataSchemaFieldRecord field,
                                        String outcome,
                                        RuntimeException error) {
        if (observationPort == null) {
            return;
        }
        try {
            LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
            attributes.put("backend", BACKEND);
            attributes.put("action", Objects.requireNonNullElse(action, ""));
            attributes.put("tenantId", field.tenantId());
            attributes.put("knowledgeBaseId", field.knowledgeBaseId());
            // 字段名和错误详情写入同步状态仓储；观测标签只保留低基数状态。
            attributes.put("schemaVersion", Integer.toString(field.schemaVersion()));
            attributes.put("valueType", field.valueType().name());
            attributes.put("indexed", Boolean.toString(field.indexed()));
            attributes.put("indexPolicy", Objects.requireNonNullElse(field.indexPolicy(), MetadataIndexPolicy.NONE).name());
            attributes.put("pushdownToKeyword", Boolean.toString(field.backendMapping().pushdownToKeyword()));
            attributes.put("pushdownToVector", Boolean.toString(field.backendMapping().pushdownToVector()));
            attributes.put("guardOnly", Boolean.toString(field.backendMapping().guardOnly()));
            attributes.put("outcome", Objects.requireNonNullElse(outcome, ""));
            if (error != null) {
                attributes.put("errorType", error.getClass().getSimpleName());
            }
            observationPort.recordEvent(new ObservationEvent(eventName, null, attributes));
        } catch (RuntimeException ignored) {
        }
    }

    private boolean shouldSync(MetadataSchemaFieldRecord field) {
        return field.indexed()
                && field.backendMapping().pushdownToKeyword()
                && !field.backendMapping().guardOnly()
                && (MetadataIndexPolicy.SEARCH_KEYWORD.equals(field.indexPolicy())
                || MetadataIndexPolicy.SEARCH_TEXT.equals(field.indexPolicy()));
    }

    private boolean sameMappingDefinition(MetadataSchemaFieldRecord previousField, MetadataSchemaFieldRecord currentField) {
        return mappingBody(previousField).equals(mappingBody(currentField));
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

    @FunctionalInterface
    private interface IndexSyncOperation {

        String run();
    }
}
