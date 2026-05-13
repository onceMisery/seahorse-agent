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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategy;

import java.util.List;
import java.util.Objects;

/**
 * 多策略检索评测对比请求。
 *
 * <p>请求中的样本集合共用同一批强类型过滤条件，策略只表达检索参数差异，避免绕过内核 Filter Compiler。</p>
 */
public record RetrievalEvaluationComparisonRequest(
        String tenantId,
        String baselineStrategyName,
        Integer topK,
        List<StrategyRequest> strategies,
        List<RetrievalEvaluationRequest.CaseRequest> cases
) {

    public RetrievalEvaluationComparisonCommand toCommand(String kbId) {
        int resolvedTopK = topK == null || topK <= 0 ? 5 : topK;
        List<RetrievalEvaluationStrategy> evaluationStrategies =
                Objects.requireNonNullElse(strategies, List.<StrategyRequest>of()).stream()
                        .map(strategy -> strategy.toStrategy(resolvedTopK))
                        .toList();
        List<RetrievalEvaluationCase> evaluationCases =
                Objects.requireNonNullElse(cases, List.<RetrievalEvaluationRequest.CaseRequest>of()).stream()
                        .map(caseRequest -> caseRequest.toCase(kbId, tenantId))
                        .toList();
        return new RetrievalEvaluationComparisonCommand(
                baselineStrategyName,
                resolvedTopK,
                evaluationStrategies,
                evaluationCases);
    }

    public record StrategyRequest(
            String strategyName,
            Integer topK,
            RetrievalOptions options
    ) {

        private RetrievalEvaluationStrategy toStrategy(int defaultTopK) {
            int resolvedTopK = topK == null || topK <= 0 ? defaultTopK : topK;
            return new RetrievalEvaluationStrategy(strategyName, resolvedTopK, options);
        }
    }
}
