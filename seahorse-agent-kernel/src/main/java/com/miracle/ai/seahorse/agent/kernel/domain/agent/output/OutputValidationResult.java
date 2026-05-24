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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.output;

import java.util.List;
import java.util.Objects;

/**
 * 单个 validator 的校验结果。
 *
 * @param decision          决策
 * @param issues            问题列表，可为空
 * @param normalizedContent 规范化后的内容；若 validator 不做规范化则为 {@code null}
 */
public record OutputValidationResult(
        OutputValidationDecision decision,
        List<OutputValidationIssue> issues,
        String normalizedContent) {

    public OutputValidationResult {
        Objects.requireNonNull(decision, "decision must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static OutputValidationResult pass() {
        return new OutputValidationResult(OutputValidationDecision.PASS, List.of(), null);
    }

    public static OutputValidationResult pass(String normalizedContent) {
        return new OutputValidationResult(OutputValidationDecision.PASS, List.of(), normalizedContent);
    }

    public static OutputValidationResult block(List<OutputValidationIssue> issues) {
        return new OutputValidationResult(OutputValidationDecision.BLOCK, issues, null);
    }

    public static OutputValidationResult warn(List<OutputValidationIssue> issues) {
        return new OutputValidationResult(OutputValidationDecision.WARN, issues, null);
    }
}
