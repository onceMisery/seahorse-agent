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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationIssue;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidatorPort;

import java.util.List;
import java.util.Set;

/**
 * Slice 1d：Mermaid 基础语法校验。
 *
 * <p>校验范围（最小可用）：
 * <ul>
 *     <li>剥离首尾 fenced code block（{@code ```mermaid ... ```} / {@code ``` ... ```}）。</li>
 *     <li>剩余内容首个非空行必须以 {@link #SUPPORTED_DIAGRAM_TOKENS} 中的图类型 token 开头。</li>
 *     <li>token 不识别时按 {@link OutputStructuralValidationPolicy} 配置返回 BLOCK 或 WARN。</li>
 * </ul>
 *
 * <p>本 validator 不执行 Mermaid 完整渲染（spec §6 明确规避），实际渲染交由前端/CI。
 */
public final class MermaidSyntaxOutputValidator implements OutputValidatorPort {

    public static final String VALIDATOR_NAME = "mermaid-syntax";

    static final String CODE_MERMAID_EMPTY_CONTENT = "MERMAID_EMPTY_CONTENT";
    static final String CODE_MERMAID_UNKNOWN_DIAGRAM_TYPE = "MERMAID_UNKNOWN_DIAGRAM_TYPE";

    /**
     * Mermaid 官方支持的图类型起始 token 集合。
     */
    public static final Set<String> SUPPORTED_DIAGRAM_TOKENS = Set.of(
            "graph",
            "flowchart",
            "sequenceDiagram",
            "classDiagram",
            "stateDiagram",
            "stateDiagram-v2",
            "erDiagram",
            "journey",
            "gantt",
            "pie",
            "mindmap",
            "timeline",
            "gitGraph",
            "C4Context",
            "C4Container",
            "C4Component",
            "C4Dynamic",
            "requirementDiagram");

    @Override
    public String name() {
        return VALIDATOR_NAME;
    }

    @Override
    public boolean supports(OutputValidationRequest request) {
        return request != null && request.artifactType() == OutputArtifactType.MERMAID;
    }

    @Override
    public OutputValidationResult validate(OutputValidationRequest request) {
        String stripped = stripCodeFence(request.content());
        if (stripped.isBlank()) {
            return OutputValidationResult.block(List.of(new OutputValidationIssue(
                    CODE_MERMAID_EMPTY_CONTENT,
                    "$",
                    "Mermaid content is empty after fence stripping",
                    OutputValidationDecision.BLOCK)));
        }
        String firstNonEmptyLine = firstNonEmptyLine(stripped);
        if (firstNonEmptyLine == null) {
            return OutputValidationResult.block(List.of(new OutputValidationIssue(
                    CODE_MERMAID_EMPTY_CONTENT,
                    "$",
                    "Mermaid content has no non-empty line",
                    OutputValidationDecision.BLOCK)));
        }
        String diagramToken = firstToken(firstNonEmptyLine);
        if (SUPPORTED_DIAGRAM_TOKENS.contains(diagramToken)) {
            return OutputValidationResult.pass(stripped);
        }
        OutputValidationDecision decision = OutputStructuralValidationPolicy.decisionForViolation(request);
        OutputValidationIssue issue = new OutputValidationIssue(
                CODE_MERMAID_UNKNOWN_DIAGRAM_TYPE,
                "$.diagramType",
                "Mermaid diagram type not recognized: '" + diagramToken + "'",
                decision);
        return decision == OutputValidationDecision.WARN
                ? OutputValidationResult.warn(List.of(issue))
                : OutputValidationResult.block(List.of(issue));
    }

    private static String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return "";
        }
        String body = trimmed.substring(firstNewline + 1);
        if (body.endsWith("```")) {
            body = body.substring(0, body.length() - 3);
        }
        return body.strip();
    }

    private static String firstNonEmptyLine(String content) {
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static String firstToken(String line) {
        int splitAt = line.indexOf(' ');
        if (splitAt < 0) {
            return line;
        }
        return line.substring(0, splitAt);
    }
}
