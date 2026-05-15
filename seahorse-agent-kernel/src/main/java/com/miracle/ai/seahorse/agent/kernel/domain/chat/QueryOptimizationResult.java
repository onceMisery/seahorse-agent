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

package com.miracle.ai.seahorse.agent.kernel.domain.chat;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 查询优化结果。
 *
 * @param originalQuestion  用户原始输入，不可丢
 * @param optimizedQuestion 给 QueryRewritePort 使用的输入
 * @param protectedTerms    后续 rewrite 必须尊重的保护词（key=原始词, value=保护原因）
 * @param expandedTerms     术语归一化后的扩展词列表
 * @param appliedRules      trace/debug 使用，记录应用了哪些规则
 */
public record QueryOptimizationResult(
        String originalQuestion,
        String optimizedQuestion,
        Map<String, String> protectedTerms,
        List<String> expandedTerms,
        List<String> appliedRules
) {

    public QueryOptimizationResult {
        originalQuestion = Objects.requireNonNullElse(originalQuestion, "");
        optimizedQuestion = Objects.requireNonNullElse(optimizedQuestion, "");
        protectedTerms = Map.copyOf(Objects.requireNonNullElse(protectedTerms, Map.of()));
        expandedTerms = List.copyOf(Objects.requireNonNullElse(expandedTerms, List.of()));
        appliedRules = List.copyOf(Objects.requireNonNullElse(appliedRules, List.of()));
    }
}
