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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AuditRedactionPolicy {

    public static final String REDACTED_VALUE = "[REDACTED]";

    private static final String SECRET_REF_KEY = "secretref";
    private static final String REDACTED_PAYLOAD = "{\"redacted\":\"" + REDACTED_VALUE + "\"}";
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "secret",
            "token",
            "password",
            "apikey",
            "authorization");

    private final ObjectMapper objectMapper;

    public AuditRedactionPolicy() {
        this(new ObjectMapper());
    }

    AuditRedactionPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String redact(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return AuditEvent.EMPTY_PAYLOAD;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            redact(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return REDACTED_PAYLOAD;
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (sensitive(field.getKey())) {
                    objectNode.put(field.getKey(), REDACTED_VALUE);
                } else {
                    redact(field.getValue());
                }
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    private boolean sensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (SECRET_REF_KEY.equals(normalized)) {
            return false;
        }
        return SENSITIVE_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}
