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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一承接元数据治理 JDBC 适配器里的 JSON 解析和后端字段映射规则，
 * 避免主适配器同时承担仓储编排与 JSON 协议细节。
 */
public final class JdbcMetadataJsonSupport {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JdbcMetadataJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public BackendFieldMapping backendMapping(String json, String fieldKey) {
        Map<String, Object> values = readMap(json);
        if (values.isEmpty()) {
            return BackendFieldMapping.defaults(fieldKey);
        }
        return new BackendFieldMapping(
                text(values.get("canonicalName"), fieldKey),
                text(values.get("milvusPath"), ""),
                text(values.get("pgJsonPath"), ""),
                text(values.get("searchFieldName"), fieldKey),
                bool(values.get("pushdownToVector")),
                bool(values.get("pushdownToKeyword")),
                bool(values.get("guardOnly")),
                values);
    }

    public Set<MetadataOperator> operators(String json) {
        List<String> values = readList(json);
        if (values.isEmpty()) {
            return Set.of(MetadataOperator.EQ, MetadataOperator.IN);
        }
        return values.stream()
                .map(value -> enumValue(MetadataOperator.class, value, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<String> trustedSources(String json) {
        return Set.copyOf(readList(json));
    }

    public List<String> readList(String json) {
        if (blank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> readMap(String json) {
        if (blank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public List<Map<String, Object>> readMapList(String json) {
        if (blank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, MAP_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> mutableMap(String json) {
        return new LinkedHashMap<>(readMap(json));
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(value, Map.of()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize metadata json failed", ex);
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(Objects.toString(value, "false"));
    }

    private String text(Object value, String defaultValue) {
        return Objects.toString(value, defaultValue);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }
}
