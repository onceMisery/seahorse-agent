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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 检索评测汇总报表。
 */
public record RetrievalEvaluationReport(
        @JsonProperty("strategyName") String strategyName,
        @JsonProperty("topK") int topK,
        @JsonProperty("caseCount") int caseCount,
        @JsonProperty("evaluableCaseCount") int evaluableCaseCount,
        @JsonProperty("recallAtK") double recallAtK,
        @JsonProperty("precisionAtK") double precisionAtK,
        @JsonProperty("mrr") double mrr,
        @JsonProperty("ndcgAtK") double ndcgAtK,
        @JsonProperty("emptyRecallRate") double emptyRecallRate,
        @JsonProperty("averageLatencyMs") double averageLatencyMs,
        @JsonProperty("p95LatencyMs") double p95LatencyMs,
        @JsonProperty("cases") List<RetrievalEvaluationCaseResult> cases
) {

    public RetrievalEvaluationReport(String strategyName,
                                     int topK,
                                     int caseCount,
                                     int evaluableCaseCount,
                                     double recallAtK,
                                     double mrr,
                                     double ndcgAtK,
                                     double emptyRecallRate,
                                     double averageLatencyMs,
                                     double p95LatencyMs,
                                     List<RetrievalEvaluationCaseResult> cases) {
        this(strategyName, topK, caseCount, evaluableCaseCount, recallAtK, 0D, mrr, ndcgAtK,
                emptyRecallRate, averageLatencyMs, p95LatencyMs, cases);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RetrievalEvaluationReport {
        strategyName = Objects.requireNonNullElse(strategyName, "");
        topK = Math.max(0, topK);
        caseCount = Math.max(0, caseCount);
        evaluableCaseCount = Math.max(0, evaluableCaseCount);
        precisionAtK = normalizeRatio(precisionAtK);
        cases = List.copyOf(Objects.requireNonNullElse(cases, List.of()));
    }

    private static double normalizeRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
