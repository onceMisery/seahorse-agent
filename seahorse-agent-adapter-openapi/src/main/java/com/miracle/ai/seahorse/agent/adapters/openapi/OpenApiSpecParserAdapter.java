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

package com.miracle.ai.seahorse.agent.adapters.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecDocument;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParseRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParserPort;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class OpenApiSpecParserAdapter implements OpenApiSpecParserPort {

    private static final String OPENAPI_MAJOR_VERSION = "3.";
    private static final String EMPTY_JSON_OBJECT = "{}";

    private final ObjectMapper objectMapper;

    public OpenApiSpecParserAdapter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public OpenApiSpecDocument parse(OpenApiSpecParseRequest request) {
        OpenApiSpecParseRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String specJson = requireText(safeRequest.specJson(), "specJson must not be blank");
        try {
            JsonNode root = objectMapper.readTree(specJson);
            validateOpenApi(root);
            JsonNode info = root.path("info");
            return new OpenApiSpecDocument(
                    textOrNull(info.path("title")),
                    textOrNull(info.path("description")),
                    operations(root.path("paths")));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid OpenAPI JSON", ex);
        }
    }

    private void validateOpenApi(JsonNode root) {
        String version = textOrNull(root.path("openapi"));
        if (version == null || !version.startsWith(OPENAPI_MAJOR_VERSION)) {
            throw new IllegalArgumentException("Only OpenAPI 3.x JSON specs are supported");
        }
    }

    private List<OpenApiSpecOperation> operations(JsonNode paths) throws JsonProcessingException {
        if (!paths.isObject()) {
            return List.of();
        }
        List<OpenApiSpecOperation> operations = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathFields.next();
            if (!pathEntry.getValue().isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> methodFields = pathEntry.getValue().fields();
            while (methodFields.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodFields.next();
                OpenApiHttpMethod method = parseMethodOrNull(methodEntry.getKey());
                if (method == null || !methodEntry.getValue().isObject()) {
                    continue;
                }
                operations.add(operation(pathEntry.getKey(), method, (ObjectNode) methodEntry.getValue()));
            }
        }
        return operations;
    }

    private OpenApiSpecOperation operation(String path,
                                           OpenApiHttpMethod method,
                                           ObjectNode operation) throws JsonProcessingException {
        return new OpenApiSpecOperation(
                textOrNull(operation.path("operationId")),
                method,
                path,
                textOrNull(operation.path("summary")),
                textOrNull(operation.path("description")),
                inputSchemaJson(operation),
                outputSchemaJson(operation.path("responses")),
                resourceType(operation));
    }

    private String inputSchemaJson(ObjectNode operation) throws JsonProcessingException {
        ObjectNode schema = objectMapper.createObjectNode();
        JsonNode requestBodySchema = firstContentSchema(operation.path("requestBody").path("content"));
        if (!requestBodySchema.isMissingNode()) {
            schema.set("requestBody", requestBodySchema);
        }
        JsonNode parameters = operation.path("parameters");
        if (parameters.isArray() && !parameters.isEmpty()) {
            schema.set("parameters", parameters);
        }
        return schema.isEmpty() ? EMPTY_JSON_OBJECT : objectMapper.writeValueAsString(schema);
    }

    private String outputSchemaJson(JsonNode responses) throws JsonProcessingException {
        if (!responses.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = responses.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> response = fields.next();
            if (!isSuccessStatus(response.getKey())) {
                continue;
            }
            JsonNode schema = firstContentSchema(response.getValue().path("content"));
            if (!schema.isMissingNode()) {
                return objectMapper.writeValueAsString(schema);
            }
        }
        return null;
    }

    private JsonNode firstContentSchema(JsonNode content) {
        if (!content.isObject()) {
            return objectMapper.missingNode();
        }
        Iterator<JsonNode> values = content.elements();
        while (values.hasNext()) {
            JsonNode schema = values.next().path("schema");
            if (!schema.isMissingNode()) {
                return schema;
            }
        }
        return objectMapper.missingNode();
    }

    private String resourceType(ObjectNode operation) {
        String explicit = textOrNull(operation.path("x-resource-type"));
        if (explicit != null) {
            return normalizeResourceType(explicit);
        }
        JsonNode tags = operation.path("tags");
        if (tags.isArray() && !tags.isEmpty()) {
            String tag = textOrNull(tags.get(0));
            if (tag != null) {
                return normalizeResourceType(tag);
            }
        }
        return null;
    }

    private OpenApiHttpMethod parseMethodOrNull(String method) {
        try {
            return OpenApiHttpMethod.parse(method);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isSuccessStatus(String status) {
        return status != null && status.length() == 3 && status.charAt(0) == '2';
    }

    private String normalizeResourceType(String value) {
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
