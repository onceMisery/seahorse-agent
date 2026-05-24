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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDL 安全 validator。
 *
 * <p>Slice 1b 最小黑名单：识别 {@code DROP}、{@code TRUNCATE}、{@code DELETE FROM}、
 * {@code ALTER TABLE ... DROP} 等高风险语句并直接 BLOCK。命中后会逐条产生 issue，
 * 调用方据此决定是否进入 confirmed snapshot。
 *
 * <p>本 validator 不实现完整 SQL parser；任何更细粒度的 DDL 语义分析需要引入真正的
 * SQL 解析器（例如 jsqlparser），属后续切片范围。
 */
public final class DdlSafetyOutputValidator implements OutputValidatorPort {

    public static final String VALIDATOR_NAME = "ddl-safety";

    static final String CODE_DDL_DROP_FORBIDDEN = "DDL_DROP_FORBIDDEN";
    static final String CODE_DDL_TRUNCATE_FORBIDDEN = "DDL_TRUNCATE_FORBIDDEN";
    static final String CODE_DDL_DELETE_FORBIDDEN = "DDL_DELETE_FORBIDDEN";
    static final String CODE_DDL_ALTER_DROP_FORBIDDEN = "DDL_ALTER_DROP_FORBIDDEN";
    static final String CODE_DDL_GRANT_FORBIDDEN = "DDL_GRANT_FORBIDDEN";

    private static final List<ForbiddenPattern> FORBIDDEN_PATTERNS = List.of(
            new ForbiddenPattern(
                    Pattern.compile("(?i)\\bDROP\\s+(TABLE|DATABASE|SCHEMA|INDEX|VIEW|TRIGGER|FUNCTION|PROCEDURE)\\b"),
                    CODE_DDL_DROP_FORBIDDEN,
                    "DROP statements are forbidden in generated DDL"),
            new ForbiddenPattern(
                    Pattern.compile("(?i)\\bTRUNCATE\\s+TABLE\\b"),
                    CODE_DDL_TRUNCATE_FORBIDDEN,
                    "TRUNCATE TABLE statements are forbidden in generated DDL"),
            new ForbiddenPattern(
                    Pattern.compile("(?i)\\bDELETE\\s+FROM\\b"),
                    CODE_DDL_DELETE_FORBIDDEN,
                    "DELETE FROM statements are forbidden in generated DDL"),
            new ForbiddenPattern(
                    Pattern.compile("(?i)\\bALTER\\s+TABLE\\b[^;]*?\\bDROP\\b"),
                    CODE_DDL_ALTER_DROP_FORBIDDEN,
                    "ALTER TABLE ... DROP statements are forbidden in generated DDL"),
            new ForbiddenPattern(
                    Pattern.compile("(?i)\\bGRANT\\s+.*\\bTO\\b"),
                    CODE_DDL_GRANT_FORBIDDEN,
                    "GRANT statements are forbidden in generated DDL"));

    @Override
    public String name() {
        return VALIDATOR_NAME;
    }

    @Override
    public boolean supports(OutputValidationRequest request) {
        return request != null
                && request.artifactType() == OutputArtifactType.DDL
                && request.content() != null
                && !request.content().isBlank();
    }

    @Override
    public OutputValidationResult validate(OutputValidationRequest request) {
        String content = request.content();
        List<OutputValidationIssue> issues = new ArrayList<>();
        for (ForbiddenPattern forbidden : FORBIDDEN_PATTERNS) {
            Matcher matcher = forbidden.pattern().matcher(content);
            while (matcher.find()) {
                issues.add(new OutputValidationIssue(
                        forbidden.code(),
                        "$[" + matcher.start() + "]",
                        forbidden.message() + ": '" + matcher.group() + "'",
                        OutputValidationDecision.BLOCK));
            }
        }
        if (issues.isEmpty()) {
            return OutputValidationResult.pass();
        }
        return OutputValidationResult.block(issues);
    }

    private record ForbiddenPattern(Pattern pattern, String code, String message) {
    }
}
