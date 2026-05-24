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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1d：Markdown / Mermaid validator 决策路径覆盖。
 */
class MarkdownAndMermaidValidatorTests {

    private final MarkdownStructureOutputValidator markdownValidator = new MarkdownStructureOutputValidator();
    private final MermaidSyntaxOutputValidator mermaidValidator = new MermaidSyntaxOutputValidator();

    @Test
    void markdownPassesWhenAllRequiredHeadingsPresent() {
        OutputValidationRequest request = newMarkdownRequest(
                "[\"## Overview\",\"## Approach\"]",
                "## Overview\nintro\n## Approach\nimpl\n",
                Map.of());

        OutputValidationResult result = markdownValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
    }

    @Test
    void markdownBlocksWhenRequiredHeadingMissing() {
        OutputValidationRequest request = newMarkdownRequest(
                "[\"## Overview\",\"## Approach\",\"## Risks\"]",
                "## Overview\nintro\n## Approach\nimpl\n",
                Map.of());

        OutputValidationResult result = markdownValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).code())
                .isEqualTo(MarkdownStructureOutputValidator.CODE_MARKDOWN_REQUIRED_SECTION_MISSING);
        assertThat(result.issues().get(0).message()).contains("## Risks");
    }

    @Test
    void markdownDowngradesToWarnWhenStrictFlagIsFalse() {
        OutputValidationRequest request = newMarkdownRequest(
                "[\"## Risks\"]",
                "## Overview\n",
                Map.of(OutputStructuralValidationPolicy.ATTRIBUTE_STRICT, false));

        OutputValidationResult result = markdownValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.WARN);
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).decision()).isEqualTo(OutputValidationDecision.WARN);
    }

    @Test
    void markdownBlocksWhenSchemaJsonIsNotArray() {
        OutputValidationRequest request = newMarkdownRequest(
                "{\"requiredSections\":[\"## Overview\"]}",
                "## Overview\n",
                Map.of());

        OutputValidationResult result = markdownValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues().get(0).code())
                .isEqualTo(MarkdownStructureOutputValidator.CODE_MARKDOWN_SCHEMA_INVALID);
    }

    @Test
    void markdownEmptySchemaArrayPassesAnything() {
        OutputValidationRequest request = newMarkdownRequest("[]", "no headings", Map.of());

        OutputValidationResult result = markdownValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
    }

    @Test
    void mermaidPassesOnRecognizedDiagramToken() {
        OutputValidationRequest request = newMermaidRequest(
                "flowchart TD\n  A --> B",
                Map.of());

        OutputValidationResult result = mermaidValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(result.normalizedContent()).startsWith("flowchart");
    }

    @Test
    void mermaidStripsFencedCodeBlockBeforeChecking() {
        OutputValidationRequest request = newMermaidRequest(
                "```mermaid\nsequenceDiagram\n  A->>B: hi\n```",
                Map.of());

        OutputValidationResult result = mermaidValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(result.normalizedContent()).startsWith("sequenceDiagram");
    }

    @Test
    void mermaidBlocksOnUnknownDiagramToken() {
        OutputValidationRequest request = newMermaidRequest(
                "warpDiagram x => y",
                Map.of());

        OutputValidationResult result = mermaidValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues().get(0).code())
                .isEqualTo(MermaidSyntaxOutputValidator.CODE_MERMAID_UNKNOWN_DIAGRAM_TYPE);
    }

    @Test
    void mermaidDowngradesToWarnWhenStrictFlagIsFalse() {
        OutputValidationRequest request = newMermaidRequest(
                "warpDiagram x => y",
                Map.of(OutputStructuralValidationPolicy.ATTRIBUTE_STRICT, false));

        OutputValidationResult result = mermaidValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.WARN);
    }

    @Test
    void mermaidBlocksOnEmptyContent() {
        OutputValidationRequest request = newMermaidRequest("```mermaid\n```", Map.of());

        OutputValidationResult result = mermaidValidator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues().get(0).code())
                .isEqualTo(MermaidSyntaxOutputValidator.CODE_MERMAID_EMPTY_CONTENT);
    }

    private static OutputValidationRequest newMarkdownRequest(String schemaJson,
                                                              String content,
                                                              Map<String, Object> attributes) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                OutputArtifactType.MARKDOWN,
                schemaJson,
                content,
                attributes);
    }

    private static OutputValidationRequest newMermaidRequest(String content, Map<String, Object> attributes) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                OutputArtifactType.MERMAID,
                null,
                content,
                attributes);
    }
}
