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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Slice 1d：Markdown 必备章节校验。
 *
 * <p>schemaJson 约定为 JSON 数组，元素是必备章节标题（含 leading 井号，如 {@code "## Overview"}）。
 * 当所有 required heading 都出现于 markdown 内容（按整行 trim 后匹配）时返回 PASS，否则按
 * {@link OutputStructuralValidationPolicy} 配置返回 BLOCK 或 WARN。
 *
 * <p>本 validator 不解析完整 Markdown AST：只用行扫描 + 精确 heading 匹配，足以覆盖
 * "必须包含 ## Overview / ## Approach" 这类 review 模板诉求；高级语义（heading 大小写归一化、
 * 嵌套结构）由后续切片补充。
 */
public final class MarkdownStructureOutputValidator implements OutputValidatorPort {

    public static final String VALIDATOR_NAME = "markdown-structure";

    static final String CODE_MARKDOWN_SCHEMA_INVALID = "MARKDOWN_SCHEMA_INVALID";
    static final String CODE_MARKDOWN_REQUIRED_SECTION_MISSING = "MARKDOWN_REQUIRED_SECTION_MISSING";

    private final ObjectMapper objectMapper;

    public MarkdownStructureOutputValidator() {
        this(new ObjectMapper());
    }

    public MarkdownStructureOutputValidator(ObjectMapper objectMapper) {
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
        return request.artifactType() == OutputArtifactType.MARKDOWN
                && request.schemaJson() != null
                && !request.schemaJson().isBlank();
    }

    @Override
    public OutputValidationResult validate(OutputValidationRequest request) {
        List<String> required;
        try {
            required = parseRequiredHeadings(request.schemaJson());
        } catch (Exception ex) {
            return OutputValidationResult.block(List.of(new OutputValidationIssue(
                    CODE_MARKDOWN_SCHEMA_INVALID,
                    "$schema",
                    "Markdown schemaJson must be a JSON array of required heading strings: "
                            + (ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()),
                    OutputValidationDecision.BLOCK)));
        }
        if (required.isEmpty()) {
            return OutputValidationResult.pass(request.content());
        }
        Set<String> presentHeadings = collectHeadings(request.content());
        OutputValidationDecision decision = OutputStructuralValidationPolicy.decisionForViolation(request);
        List<OutputValidationIssue> issues = new ArrayList<>();
        for (String heading : required) {
            if (!presentHeadings.contains(heading)) {
                issues.add(new OutputValidationIssue(
                        CODE_MARKDOWN_REQUIRED_SECTION_MISSING,
                        "$." + heading,
                        "Markdown required section missing: " + heading,
                        decision));
            }
        }
        if (issues.isEmpty()) {
            return OutputValidationResult.pass(request.content());
        }
        return decision == OutputValidationDecision.WARN
                ? OutputValidationResult.warn(issues)
                : OutputValidationResult.block(issues);
    }

    private List<String> parseRequiredHeadings(String schemaJson) throws Exception {
        JsonNode schema = objectMapper.readTree(schemaJson);
        if (!schema.isArray()) {
            throw new IllegalArgumentException("schemaJson must be a JSON array");
        }
        List<String> headings = new ArrayList<>();
        for (JsonNode entry : schema) {
            if (!entry.isTextual()) {
                throw new IllegalArgumentException("schemaJson array elements must be strings");
            }
            String value = entry.asText().trim();
            if (!value.isEmpty()) {
                headings.add(value);
            }
        }
        return headings;
    }

    private Set<String> collectHeadings(String content) {
        Set<String> headings = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return headings;
        }
        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.startsWith("#")) {
                headings.add(line);
            }
        }
        return headings;
    }
}
