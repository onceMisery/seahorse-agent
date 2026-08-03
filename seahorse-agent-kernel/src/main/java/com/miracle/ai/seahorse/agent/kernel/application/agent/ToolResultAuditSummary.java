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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Package-private audit result-shape collaborator for {@link LocalToolGatewayPort}.
 *
 * <p>Owns the pure result-shape analysis used to build tool audit result summaries:
 * text line shape, JSON top-level shape, and recursive JSON value shape. Kept under
 * the complexity budget and separate from argument summarization.</p>
 */
final class ToolResultAuditSummary {

    private static final int SUMMARY_MAX_LENGTH = 1000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    ToolResultAuditSummary() {
    }

    String summarizeResult(ToolInvocationResult result, String auditError) {
        if (result == null) {
            return null;
        }
        if (!result.success()) {
            return summarizeFailedResult(result, auditError);
        }
        String content = Objects.requireNonNullElse(result.content(), "");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contentPresent", result.content() != null);
        summary.put("contentLength", content.length());
        String contentJsonType = result.content() == null ? "none" : resultContentJsonType(content);
        summary.put("contentJsonType", contentJsonType);
        TextShape textShape = resultContentTextShape(content, contentJsonType);
        if (textShape != null) {
            summary.put("contentTextLineCount", textShape.lineCount());
            summary.put("contentTextMaxLineLength", textShape.maxLineLength());
        }
        JsonTopLevelShape jsonTopLevelShape = resultContentJsonTopLevelShape(content);
        if (jsonTopLevelShape != null) {
            summary.put("contentJsonTopLevelFieldCount", jsonTopLevelShape.fieldCount());
            summary.put("contentJsonTopLevelElementCount", jsonTopLevelShape.elementCount());
        }
        JsonValueShape jsonValueShape = resultContentJsonValueShape(content);
        if (jsonValueShape != null) {
            summary.put("contentJsonValueCount", jsonValueShape.count());
            summary.put("contentJsonValueTotalLength", jsonValueShape.totalLength());
            summary.put("contentJsonValueMaxLength", jsonValueShape.maxLength());
        }
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("contentPresent=true"
                    + ", contentLength=" + content.length()
                    + ", contentJsonType=" + resultContentJsonType(content));
        }
    }

    private TextShape resultContentTextShape(String content, String contentJsonType) {
        if (!"text".equals(contentJsonType)) {
            return null;
        }
        String[] lines = content.split("\\R", -1);
        int maxLineLength = 0;
        for (String line : lines) {
            maxLineLength = Math.max(maxLineLength, line.length());
        }
        return new TextShape(lines.length, maxLineLength);
    }

    private record TextShape(int lineCount, int maxLineLength) {
    }

    private JsonTopLevelShape resultContentJsonTopLevelShape(String content) {
        if (!hasText(content)) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            if (node.isObject()) {
                return new JsonTopLevelShape(node.size(), 0);
            }
            if (node.isArray()) {
                return new JsonTopLevelShape(0, node.size());
            }
            return new JsonTopLevelShape(0, 0);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private record JsonTopLevelShape(int fieldCount, int elementCount) {
    }

    private String summarizeFailedResult(ToolInvocationResult result, String auditError) {
        String error = Objects.requireNonNullElse(auditError, "");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contentPresent", false);
        summary.put("errorPresent", hasText(error));
        summary.put("errorLength", error.length());
        summary.put("approvalIdPresent", hasText(result.approvalId()));
        try {
            return truncate(OBJECT_MAPPER.writeValueAsString(summary));
        } catch (JsonProcessingException ex) {
            return truncate("contentPresent=false"
                    + ", errorPresent=" + hasText(error)
                    + ", errorLength=" + error.length()
                    + ", approvalIdPresent=" + hasText(result.approvalId()));
        }
    }

    private JsonValueShape resultContentJsonValueShape(String content) {
        if (!hasText(content)) {
            return null;
        }
        try {
            return jsonValueShape(OBJECT_MAPPER.readTree(content));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private JsonValueShape jsonValueShape(JsonNode node) {
        if (node == null || node.isNull()) {
            return new JsonValueShape(1, 4, 4);
        }
        if (node.isObject()) {
            int count = 0;
            int totalLength = 0;
            int maxLength = 0;
            for (JsonNode child : node) {
                JsonValueShape shape = jsonValueShape(child);
                count += shape.count();
                totalLength += shape.totalLength();
                maxLength = Math.max(maxLength, shape.maxLength());
            }
            return new JsonValueShape(count, totalLength, maxLength);
        }
        if (node.isArray()) {
            int count = 0;
            int totalLength = 0;
            int maxLength = 0;
            for (JsonNode child : node) {
                JsonValueShape shape = jsonValueShape(child);
                count += shape.count();
                totalLength += shape.totalLength();
                maxLength = Math.max(maxLength, shape.maxLength());
            }
            return new JsonValueShape(count, totalLength, maxLength);
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return new JsonValueShape(1, value.length(), value.length());
    }

    private record JsonValueShape(int count, int totalLength, int maxLength) {
    }

    private String resultContentJsonType(String content) {
        if (!hasText(content)) {
            return "empty";
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(content);
            if (node.isObject()) {
                return "object";
            }
            if (node.isArray()) {
                return "array";
            }
            if (node.isTextual()) {
                return "string";
            }
            if (node.isNumber()) {
                return "number";
            }
            if (node.isBoolean()) {
                return "boolean";
            }
            if (node.isNull()) {
                return "null";
            }
            return "json";
        } catch (JsonProcessingException ex) {
            return "text";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
