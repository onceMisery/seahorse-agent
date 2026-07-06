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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.regex.Pattern;

@FunctionalInterface
public interface ToolOutputRedactionPort {

    String REDACTED_VALUE = "[REDACTED]";
    Pattern SECRET_FIELD_PATTERN = Pattern.compile(
            "(?i).*(access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret|password|session[_-]?id"
                    + "|authorization|set[_-]?cookie|secret[_-]?key|private[_-]?key).*");

    ToolInvocationResult redact(ToolInvocationRequest request, ToolInvocationResult result);

    static ToolOutputRedactionPort noop() {
        return (request, result) -> result;
    }

    static ToolOutputRedactionPort basicSecretPatterns() {
        ObjectMapper objectMapper = new ObjectMapper();
        return (request, result) -> {
            if (result == null) {
                return result;
            }
            if (!result.success()) {
                String redactedError = redactSecretPatterns(result.error());
                return Objects.equals(redactedError, result.error())
                        ? result
                        : ToolInvocationResult.failed(redactedError, result.approvalId());
            }
            if (result.content() == null) {
                return result;
            }
            String redacted = redactJsonSecretFields(objectMapper, result.content());
            redacted = redactSecretPatterns(redacted);
            if (Objects.equals(redacted, result.content())) {
                return result;
            }
            return ToolInvocationResult.ok(redacted);
        };
    }

    private static String redactSecretPatterns(String value) {
        return CredentialTextRedactor.redact(value);
    }

    private static String redactJsonSecretFields(ObjectMapper objectMapper, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            boolean changed = redactSecretJsonFields(root);
            return changed ? objectMapper.writeValueAsString(root) : content;
        } catch (JsonProcessingException ex) {
            return content;
        }
    }

    private static boolean redactSecretJsonFields(JsonNode node) {
        if (node == null) {
            return false;
        }
        boolean changed = false;
        if (node instanceof ObjectNode objectNode) {
            changed |= redactField(objectNode, "b64Json");
            changed |= redactField(objectNode, "b64_json");
            var fields = objectNode.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (SECRET_FIELD_PATTERN.matcher(field.getKey()).matches()) {
                    changed |= redactField(objectNode, field.getKey());
                } else {
                    changed |= redactSecretJsonFields(field.getValue());
                }
            }
            return changed;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                changed |= redactSecretJsonFields(child);
            }
        }
        return changed;
    }

    private static boolean redactField(ObjectNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || REDACTED_VALUE.equals(value.asText())) {
            return false;
        }
        node.put(fieldName, REDACTED_VALUE);
        return true;
    }
}
