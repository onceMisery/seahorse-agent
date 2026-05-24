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
 * Agent 输出治理结果。
 *
 * <p>由 {@code OutputGovernanceService} 汇总单个或多个 validator 的输出后产生，供
 * {@code KernelAgentLoop} 决定最终回答的去向。
 *
 * @param decision        汇总决策
 * @param governedContent 治理后建议输出的内容；PASS/WARN 时通常为原始或规范化内容，BLOCK 时为 fallback 文案
 * @param originalContent 模型原始内容
 * @param issues          所有 validator 收集到的问题集合
 * @param validatorName   产生本结果的 validator 标识，方便观测和定位
 */
public record OutputGovernanceResult(
        OutputValidationDecision decision,
        String governedContent,
        String originalContent,
        List<OutputValidationIssue> issues,
        String validatorName) {

    public OutputGovernanceResult {
        Objects.requireNonNull(decision, "decision must not be null");
        originalContent = Objects.requireNonNullElse(originalContent, "");
        governedContent = Objects.requireNonNullElse(governedContent, originalContent);
        issues = issues == null ? List.of() : List.copyOf(issues);
        validatorName = Objects.requireNonNullElse(validatorName, "");
    }

    public boolean blocked() {
        return decision == OutputValidationDecision.BLOCK;
    }
}
