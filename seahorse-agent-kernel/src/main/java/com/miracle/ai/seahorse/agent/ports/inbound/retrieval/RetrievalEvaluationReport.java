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
 * 检索评测汇总报表。
 */
public record RetrievalEvaluationReport(
        String strategyName,
        int topK,
        int caseCount,
        int evaluableCaseCount,
        double recallAtK,
        double mrr,
        double ndcgAtK,
        double emptyRecallRate,
        double averageLatencyMs,
        double p95LatencyMs,
        List<RetrievalEvaluationCaseResult> cases
) {

    public RetrievalEvaluationReport {
        strategyName = Objects.requireNonNullElse(strategyName, "");
        topK = Math.max(0, topK);
        caseCount = Math.max(0, caseCount);
        evaluableCaseCount = Math.max(0, evaluableCaseCount);
        cases = List.copyOf(Objects.requireNonNullElse(cases, List.of()));
    }
}
