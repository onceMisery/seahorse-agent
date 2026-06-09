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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;
import java.util.regex.Pattern;

@FunctionalInterface
public interface ToolOutputRedactionPort {

    String REDACTED_VALUE = "[REDACTED]";
    Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9][A-Za-z0-9_-]*");

    ToolInvocationResult redact(ToolInvocationRequest request, ToolInvocationResult result);

    static ToolOutputRedactionPort noop() {
        return (request, result) -> result;
    }

    static ToolOutputRedactionPort basicSecretPatterns() {
        ObjectMapper objectMapper = new ObjectMapper();
        return (request, result) -> {
            if (result == null || !result.success() || result.content() == null) {
                return result;
            }
            String redacted = redactBase64JsonFields(objectMapper, result.content());
            redacted = OPENAI_KEY_PATTERN.matcher(redacted).replaceAll(REDACTED_VALUE);
            if (Objects.equals(redacted, result.content())) {
                return result;
            }
            return ToolInvocationResult.ok(redacted);
        };
    }

    private static String redactBase64JsonFields(ObjectMapper objectMapper, String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            boolean changed = redactBase64JsonFields(root);
            return changed ? objectMapper.writeValueAsString(root) : content;
        } catch (JsonProcessingException ex) {
            return content;
        }
    }

    private static boolean redactBase64JsonFields(JsonNode node) {
        if (node == null) {
            return false;
        }
        boolean changed = false;
        if (node instanceof ObjectNode objectNode) {
            changed |= redactField(objectNode, "b64Json");
            changed |= redactField(objectNode, "b64_json");
            var fields = objectNode.fields();
            while (fields.hasNext()) {
                changed |= redactBase64JsonFields(fields.next().getValue());
            }
            return changed;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                changed |= redactBase64JsonFields(child);
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
