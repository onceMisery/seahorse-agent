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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputValidationResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1a：JSON schema validator 边界用例。
 */
class JsonSchemaOutputValidatorTests {

    private final JsonSchemaOutputValidator validator = new JsonSchemaOutputValidator();

    @Test
    void doesNotSupportPlainTextRequest() {
        OutputValidationRequest request = request(OutputArtifactType.PLAIN_TEXT, null, "hello");

        assertThat(validator.supports(request)).isFalse();
    }

    @Test
    void doesNotSupportJsonRequestWithoutSchema() {
        OutputValidationRequest request = request(OutputArtifactType.JSON, " ", "{}");

        assertThat(validator.supports(request)).isFalse();
    }

    @Test
    void supportsJsonRequestWithSchema() {
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\"}",
                "{}");

        assertThat(validator.supports(request)).isTrue();
    }

    @Test
    void returnsPassWhenAllRequiredFieldsArePresent() {
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\",\"required\":[\"title\",\"steps\"]}",
                "{\"title\":\"Plan\",\"steps\":[\"a\"]}");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void blocksWhenRequiredFieldIsMissing() {
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\",\"required\":[\"title\",\"steps\"]}",
                "{\"title\":\"only title\"}");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).code())
                .isEqualTo(JsonSchemaOutputValidator.CODE_JSON_REQUIRED_FIELD_MISSING);
        assertThat(result.issues().get(0).path()).isEqualTo("$.steps");
    }

    @Test
    void blocksWhenJsonContentIsNotParseable() {
        JsonSchemaOutputValidator validator = new JsonSchemaOutputValidator(new ObjectMapper() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode readTree(String content) throws JsonProcessingException {
                throw new JsonProcessingException("Authorization: Bearer json-content-token api_key=json-content-secret") {
                };
            }
        });
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\"}",
                "{not-json");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).code())
                .isEqualTo(JsonSchemaOutputValidator.CODE_JSON_PARSE_FAILED);
        assertThat(result.issues().get(0).message())
                .contains("[REDACTED]")
                .doesNotContain("json-content-token")
                .doesNotContain("json-content-secret");
    }

    @Test
    void blocksWhenRootTypeMismatchesSchema() {
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\"}",
                "[1,2,3]");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues().get(0).code())
                .isEqualTo(JsonSchemaOutputValidator.CODE_JSON_ROOT_TYPE_MISMATCH);
    }

    @Test
    void blocksWhenPropertyTypeMismatchesSchema() {
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}",
                "{\"title\":123}");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues().get(0).code())
                .isEqualTo(JsonSchemaOutputValidator.CODE_JSON_FIELD_TYPE_MISMATCH);
        assertThat(result.issues().get(0).path()).isEqualTo("$.title");
    }

    @Test
    void blocksWhenConfiguredSchemaIsItselfInvalid() {
        AtomicInteger invocations = new AtomicInteger();
        JsonSchemaOutputValidator validator = new JsonSchemaOutputValidator(new ObjectMapper() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode readTree(String content) throws JsonProcessingException {
                if (invocations.incrementAndGet() == 1) {
                    return super.readTree(content);
                }
                throw new JsonProcessingException("Authorization: Bearer json-schema-token api_key=json-schema-secret") {
                };
            }
        });
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{not a schema",
                "{}");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues().get(0).code())
                .isEqualTo(JsonSchemaOutputValidator.CODE_JSON_SCHEMA_INVALID);
        assertThat(result.issues().get(0).message())
                .contains("[REDACTED]")
                .doesNotContain("json-schema-token")
                .doesNotContain("json-schema-secret");
    }

    @Test
    void normalizesContentByTrimmingSurroundingWhitespace() {
        OutputValidationRequest request = request(
                OutputArtifactType.JSON,
                "{\"type\":\"object\"}",
                "   {\"ok\":true}  \n");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(result.normalizedContent()).isEqualTo("{\"ok\":true}");
    }

    private static OutputValidationRequest request(OutputArtifactType type, String schemaJson, String content) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                type,
                schemaJson,
                content,
                Map.of());
    }
}
