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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AgentCheckpointViewSanitizer {

    private final ObjectMapper objectMapper;

    AgentCheckpointViewSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    AgentCheckpoint checkpointForView(AgentCheckpoint checkpoint) {
        return new AgentCheckpoint(
                checkpoint.checkpointId(),
                checkpoint.runId(),
                checkpoint.stepId(),
                checkpoint.sequenceNo(),
                checkpoint.checkpointType(),
                checkpoint.stateJson(),
                checkpoint.messageHistoryJson(),
                checkpoint.contextPackId(),
                pendingToolCallJsonForView(checkpoint.pendingToolCallJson()),
                checkpoint.createdAt());
    }

    String pendingToolCallJsonForView(String pendingToolCallJson) {
        if (!hasText(pendingToolCallJson)) {
            return pendingToolCallJson;
        }
        try {
            JsonNode root = objectMapper.readTree(pendingToolCallJson);
            if (!root.isObject()) {
                return null;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                if (!"resourceRefs".equals(entry.getKey())) {
                    payload.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });
            JsonNode resourceRefs = root.path("resourceRefs");
            payload.put("resourceRefKeys", safeResourceRefKeys(resourceRefs));
            payload.put("resourceRefCount", resourceRefs.isObject() ? resourceRefs.size() : 0);
            payload.put("resourceRefHash", sha256(canonicalJson(resourceRefs)));
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<String> safeResourceRefKeys(JsonNode resourceRefs) {
        if (resourceRefs == null || !resourceRefs.isObject()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        resourceRefs.fieldNames().forEachRemaining(key -> {
            String trimmed = key == null ? "" : key.trim();
            if (isSafePreviewKey(trimmed)) {
                keys.add(trimmed);
            }
        });
        return keys.stream().sorted().toList();
    }

    private boolean isSafePreviewKey(String key) {
        if (!hasText(key) || key.length() > 64) {
            return false;
        }
        String lower = key.toLowerCase();
        if (lower.contains("secret") || lower.contains("token") || lower.contains("password")) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            boolean safe = (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-'
                    || ch == '.';
            if (!safe) {
                return false;
            }
        }
        return true;
    }

    private String canonicalJson(JsonNode node) throws JsonProcessingException {
        if (node == null || !node.isObject()) {
            return "{}";
        }
        Map<String, String> canonical = new LinkedHashMap<>();
        node.fieldNames().forEachRemaining(key -> canonical.put(key, text(node, key)));
        Map<String, String> sorted = new LinkedHashMap<>();
        canonical.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return objectMapper.writeValueAsString(sorted);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
