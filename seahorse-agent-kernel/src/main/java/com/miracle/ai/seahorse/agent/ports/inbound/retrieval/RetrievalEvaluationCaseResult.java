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
 * 单条评测样本结果。
 */
public record RetrievalEvaluationCaseResult(
        @JsonProperty("caseId") String caseId,
        @JsonProperty("question") String question,
        @JsonProperty("retrievedChunkIds") List<String> retrievedChunkIds,
        @JsonProperty("retrievedDocIds") List<String> retrievedDocIds,
        @JsonProperty("retrievedCount") int retrievedCount,
        @JsonProperty("hitCount") int hitCount,
        @JsonProperty("recallAtK") double recallAtK,
        @JsonProperty("reciprocalRank") double reciprocalRank,
        @JsonProperty("ndcgAtK") double ndcgAtK,
        @JsonProperty("latencyMs") long latencyMs,
        @JsonProperty("status") String status,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("precisionAtK") double precisionAtK,
        @JsonProperty("negativeHitCount") int negativeHitCount,
        @JsonProperty("negativeHitChunkIds") List<String> negativeHitChunkIds
) {

    public RetrievalEvaluationCaseResult(String caseId,
                                         String question,
                                         List<String> retrievedChunkIds,
                                         List<String> retrievedDocIds,
                                         int retrievedCount,
                                         int hitCount,
                                         double recallAtK,
                                         double reciprocalRank,
                                         double ndcgAtK,
                                         long latencyMs,
                                         String status,
                                         String errorMessage) {
        this(caseId, question, retrievedChunkIds, retrievedDocIds, retrievedCount, hitCount, recallAtK,
                reciprocalRank, ndcgAtK, latencyMs, status, errorMessage, 0D, 0, List.of());
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RetrievalEvaluationCaseResult {
        caseId = Objects.requireNonNullElse(caseId, "");
        question = Objects.requireNonNullElse(question, "");
        retrievedChunkIds = List.copyOf(Objects.requireNonNullElse(retrievedChunkIds, List.of()));
        retrievedDocIds = List.copyOf(Objects.requireNonNullElse(retrievedDocIds, List.of()));
        retrievedCount = Math.max(0, retrievedCount);
        hitCount = Math.max(0, hitCount);
        latencyMs = Math.max(0, latencyMs);
        status = Objects.requireNonNullElse(status, "");
        errorMessage = Objects.requireNonNullElse(errorMessage, "");
        precisionAtK = normalizeRatio(precisionAtK);
        negativeHitChunkIds = List.copyOf(Objects.requireNonNullElse(negativeHitChunkIds, List.of()));
        negativeHitCount = Math.max(0, Math.max(negativeHitCount, negativeHitChunkIds.size()));
    }

    private static double normalizeRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
