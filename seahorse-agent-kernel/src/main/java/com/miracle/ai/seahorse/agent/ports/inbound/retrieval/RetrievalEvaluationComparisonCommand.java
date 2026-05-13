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

package com.miracle.ai.seahorse.agent.ports.inbound.retrieval;

import java.util.List;
import java.util.Objects;

/**
 * 多策略检索评测对比命令。
 *
 * @param baselineStrategyName 基线策略名称，为空时使用第一个策略作为基线
 * @param topK                 默认评测截断深度
 * @param strategies           待对比策略列表
 * @param cases                共用评测样本集合
 */
public record RetrievalEvaluationComparisonCommand(
        String baselineStrategyName,
        int topK,
        List<RetrievalEvaluationStrategy> strategies,
        List<RetrievalEvaluationCase> cases
) {

    public RetrievalEvaluationComparisonCommand {
        baselineStrategyName = Objects.requireNonNullElse(baselineStrategyName, "");
        topK = topK > 0 ? topK : 5;
        strategies = List.copyOf(Objects.requireNonNullElse(strategies, List.of()));
        cases = List.copyOf(Objects.requireNonNullElse(cases, List.of()));
    }
}
