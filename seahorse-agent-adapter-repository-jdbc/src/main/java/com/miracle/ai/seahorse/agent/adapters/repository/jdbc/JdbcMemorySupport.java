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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class JdbcMemorySupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JdbcMemorySupport() {
    }

    static String nextId() {
        return SnowflakeIds.nextIdString();
    }

    static Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    static Timestamp timestamp(Instant instant) {
        return Timestamp.from(Objects.requireNonNullElse(instant, Instant.now()));
    }

    static Map<String, Object> parseJson(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            try {
                String unwrapped = objectMapper.readValue(json, String.class);
                return objectMapper.readValue(unwrapped, MAP_TYPE);
            } catch (Exception ignoredAgain) {
                return Map.of("raw", json);
            }
        }
    }

    static String writeJson(ObjectMapper objectMapper, Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(values, Map.of()));
        } catch (Exception ex) {
            throw new IllegalArgumentException("memory metadata json serialization failed", ex);
        }
    }

    static Map<String, Object> metadata(ObjectMapper objectMapper, String json, Map<String, Object> additional) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>(parseJson(objectMapper, json));
        Objects.requireNonNullElse(additional, Map.<String, Object>of())
                .forEach((key, value) -> {
                    if (key != null && value != null) {
                        values.put(key, value);
                    }
                });
        return values;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
