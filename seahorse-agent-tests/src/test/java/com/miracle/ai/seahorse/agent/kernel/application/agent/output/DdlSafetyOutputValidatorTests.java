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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1b：DDL 安全黑名单校验。
 */
class DdlSafetyOutputValidatorTests {

    private final DdlSafetyOutputValidator validator = new DdlSafetyOutputValidator();

    @Test
    void doesNotSupportNonDdlRequest() {
        OutputValidationRequest request = request(OutputArtifactType.JSON,
                "CREATE TABLE t_demo (id BIGINT)");

        assertThat(validator.supports(request)).isFalse();
    }

    @Test
    void doesNotSupportBlankContent() {
        OutputValidationRequest request = request(OutputArtifactType.DDL, "   \n  ");

        assertThat(validator.supports(request)).isFalse();
    }

    @Test
    void passesValidCreateTable() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "CREATE TABLE t_demo (id BIGINT PRIMARY KEY, name VARCHAR(64));");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.PASS);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void blocksDropTable() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "DROP TABLE t_user;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_DROP_FORBIDDEN);
    }

    @Test
    void blocksDropDatabaseCaseInsensitive() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "drop database production;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_DROP_FORBIDDEN);
    }

    @Test
    void blocksTruncate() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "TRUNCATE TABLE t_audit;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_TRUNCATE_FORBIDDEN);
    }

    @Test
    void blocksDeleteFrom() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "DELETE FROM t_audit WHERE id < 100;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_DELETE_FORBIDDEN);
    }

    @Test
    void blocksAlterTableDropColumn() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "ALTER TABLE t_user DROP COLUMN nickname;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_ALTER_DROP_FORBIDDEN);
    }

    @Test
    void blocksGrant() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "GRANT ALL PRIVILEGES ON t_user TO public;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_GRANT_FORBIDDEN);
    }

    @Test
    void aggregatesMultipleForbiddenStatementsInSamePayload() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "DROP TABLE t_user;\nTRUNCATE TABLE t_audit;\nDELETE FROM t_log;");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactlyInAnyOrder(
                        DdlSafetyOutputValidator.CODE_DDL_DROP_FORBIDDEN,
                        DdlSafetyOutputValidator.CODE_DDL_TRUNCATE_FORBIDDEN,
                        DdlSafetyOutputValidator.CODE_DDL_DELETE_FORBIDDEN);
    }

    @Test
    void ignoresDropInsideStringLiteralIsAcceptableForFirstSliceWideNet() {
        OutputValidationRequest request = request(OutputArtifactType.DDL,
                "CREATE TABLE t_demo (note VARCHAR(64) DEFAULT 'mention of DROP TABLE in docstring');");

        OutputValidationResult result = validator.validate(request);

        assertThat(result.decision()).isEqualTo(OutputValidationDecision.BLOCK);
        assertThat(result.issues())
                .extracting(OutputValidationIssue::code)
                .containsExactly(DdlSafetyOutputValidator.CODE_DDL_DROP_FORBIDDEN);
    }

    private static OutputValidationRequest request(OutputArtifactType type, String content) {
        return new OutputValidationRequest(
                "run-1",
                "agent-1",
                "tenant-1",
                "user-1",
                type,
                null,
                content,
                Map.of());
    }
}
