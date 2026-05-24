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

package com.miracle.ai.seahorse.agent.kernel.application.agent.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidatorPort;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * JSON Schema 输出 validator。
 *
 * <p>Slice 1a 仅支持子集校验：
 * <ul>
 *     <li>解析 content 为合法 JSON，失败返回 {@link OutputValidationDecision#BLOCK}；</li>
 *     <li>解析 schema 中的 {@code type}，object/array 与 content 不匹配返回 BLOCK；</li>
 *     <li>遍历 schema 中的 {@code required} 列表，每个缺失字段产生一条 BLOCK 级 issue；</li>
 *     <li>对 {@code properties.<name>.type} 做基础类型断言（string/number/integer/boolean/object/array），失配返回 BLOCK。</li>
 * </ul>
 *
 * <p>本 validator 不实现完整 JSON Schema Draft 规范；后续如需嵌套 schema、anyOf、enum 等高级特性，
 * 可引入 networknt/json-schema-validator 等成熟库再扩展。
 */
public final class JsonSchemaOutputValidator implements OutputValidatorPort {

    public static final String VALIDATOR_NAME = "json-schema";

    static final String CODE_JSON_PARSE_FAILED = "JSON_PARSE_FAILED";
    static final String CODE_JSON_SCHEMA_INVALID = "JSON_SCHEMA_INVALID";
    static final String CODE_JSON_ROOT_TYPE_MISMATCH = "JSON_ROOT_TYPE_MISMATCH";
    static final String CODE_JSON_REQUIRED_FIELD_MISSING = "JSON_REQUIRED_FIELD_MISSING";
    static final String CODE_JSON_FIELD_TYPE_MISMATCH = "JSON_FIELD_TYPE_MISMATCH";

    private static final String TYPE_OBJECT = "object";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_BOOLEAN = "boolean";

    private final ObjectMapper objectMapper;

    public JsonSchemaOutputValidator() {
        this(new ObjectMapper());
    }

    public JsonSchemaOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
    }

    @Override
    public String name() {
        return VALIDATOR_NAME;
    }

    @Override
    public boolean supports(OutputValidationRequest request) {
        if (request == null) {
            return false;
        }
        return request.artifactType() == OutputArtifactType.JSON
                && request.schemaJson() != null
                && !request.schemaJson().isBlank();
    }

    @Override
    public OutputValidationResult validate(OutputValidationRequest request) {
        JsonNode content;
        try {
            content = objectMapper.readTree(stripWhitespace(request.content()));
        } catch (Exception ex) {
            return OutputValidationResult.block(List.of(new OutputValidationIssue(
                    CODE_JSON_PARSE_FAILED,
                    "$",
                    "Final answer is not valid JSON: "
                            + (ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()),
                    OutputValidationDecision.BLOCK)));
        }

        JsonNode schema;
        try {
            schema = objectMapper.readTree(request.schemaJson());
        } catch (Exception ex) {
            return OutputValidationResult.block(List.of(new OutputValidationIssue(
                    CODE_JSON_SCHEMA_INVALID,
                    "$schema",
                    "Configured schemaJson is not parseable: "
                            + (ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()),
                    OutputValidationDecision.BLOCK)));
        }

        List<OutputValidationIssue> issues = new ArrayList<>();
        checkRootType(content, schema, issues);
        checkRequired(content, schema, issues);
        checkPropertyTypes(content, schema, issues);

        if (issues.isEmpty()) {
            return OutputValidationResult.pass(stripWhitespace(request.content()));
        }
        return OutputValidationResult.block(issues);
    }

    private void checkRootType(JsonNode content, JsonNode schema, List<OutputValidationIssue> issues) {
        String declaredType = textOrNull(schema.get("type"));
        if (declaredType == null) {
            return;
        }
        if (TYPE_OBJECT.equals(declaredType) && !content.isObject()) {
            issues.add(new OutputValidationIssue(
                    CODE_JSON_ROOT_TYPE_MISMATCH,
                    "$",
                    "Expected JSON object at root, got " + content.getNodeType(),
                    OutputValidationDecision.BLOCK));
        } else if (TYPE_ARRAY.equals(declaredType) && !content.isArray()) {
            issues.add(new OutputValidationIssue(
                    CODE_JSON_ROOT_TYPE_MISMATCH,
                    "$",
                    "Expected JSON array at root, got " + content.getNodeType(),
                    OutputValidationDecision.BLOCK));
        }
    }

    private void checkRequired(JsonNode content, JsonNode schema, List<OutputValidationIssue> issues) {
        JsonNode required = schema.get("required");
        if (required == null || !required.isArray() || !content.isObject()) {
            return;
        }
        for (JsonNode entry : required) {
            if (!entry.isTextual()) {
                continue;
            }
            String fieldName = entry.asText();
            if (!content.has(fieldName)) {
                issues.add(new OutputValidationIssue(
                        CODE_JSON_REQUIRED_FIELD_MISSING,
                        "$." + fieldName,
                        "Required JSON field is missing: " + fieldName,
                        OutputValidationDecision.BLOCK));
            }
        }
    }

    private void checkPropertyTypes(JsonNode content, JsonNode schema, List<OutputValidationIssue> issues) {
        JsonNode properties = schema.get("properties");
        if (properties == null || !properties.isObject() || !content.isObject()) {
            return;
        }
        Iterator<String> fieldNames = properties.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode propertySchema = properties.get(fieldName);
            JsonNode value = content.get(fieldName);
            if (value == null || value.isNull() || propertySchema == null) {
                continue;
            }
            String declaredType = textOrNull(propertySchema.get("type"));
            if (declaredType == null) {
                continue;
            }
            if (!matchesType(value, declaredType)) {
                issues.add(new OutputValidationIssue(
                        CODE_JSON_FIELD_TYPE_MISMATCH,
                        "$." + fieldName,
                        "Field '" + fieldName + "' expected " + declaredType + " but was " + value.getNodeType(),
                        OutputValidationDecision.BLOCK));
            }
        }
    }

    private boolean matchesType(JsonNode value, String declaredType) {
        return switch (declaredType) {
            case TYPE_OBJECT -> value.isObject();
            case TYPE_ARRAY -> value.isArray();
            case TYPE_STRING -> value.isTextual();
            case TYPE_NUMBER -> value.isNumber();
            case TYPE_INTEGER -> value.isIntegralNumber();
            case TYPE_BOOLEAN -> value.isBoolean();
            default -> true;
        };
    }

    private String textOrNull(JsonNode node) {
        return node == null || !node.isTextual() ? null : node.asText();
    }

    private String stripWhitespace(String content) {
        return content == null ? "" : content.strip();
    }
}
