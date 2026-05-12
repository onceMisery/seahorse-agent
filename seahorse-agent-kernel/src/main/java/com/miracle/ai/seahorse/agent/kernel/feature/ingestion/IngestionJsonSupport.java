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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class IngestionJsonSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_TYPE = new TypeReference<>() {
    };

    private IngestionJsonSupport() {
    }

    static List<String> parseStringList(String raw) {
        JsonNode node = parseJson(raw);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return OBJECT_MAPPER.convertValue(node, STRING_LIST_TYPE);
    }

    static Map<String, Object> parseObject(String raw) {
        JsonNode node = parseJson(raw);
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(node, OBJECT_TYPE);
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = extractJsonBody(stripMarkdownCodeFence(raw));
        try {
            return OBJECT_MAPPER.readTree(trimmed);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    static String stripMarkdownCodeFence(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return value;
        }
        return value.substring(firstLineEnd + 1, lastFence).trim();
    }

    private static String extractJsonBody(String raw) {
        int objStart = raw.indexOf('{');
        int arrStart = raw.indexOf('[');
        int start;
        if (objStart < 0) {
            start = arrStart;
        } else if (arrStart < 0) {
            start = objStart;
        } else {
            start = Math.min(objStart, arrStart);
        }
        if (start < 0) {
            return raw;
        }
        int objEnd = raw.lastIndexOf('}');
        int arrEnd = raw.lastIndexOf(']');
        int end = Math.max(objEnd, arrEnd);
        if (end < 0 || end <= start) {
            return raw.substring(start);
        }
        return raw.substring(start, end + 1);
    }
}
