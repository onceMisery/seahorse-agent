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

import java.time.Instant;
import java.util.Objects;

/**
 * 已保存评测集的对比批次摘要。
 */
public record RetrievalEvaluationComparisonSummary(
        String comparisonId,
        String knowledgeBaseId,
        String datasetId,
        String baselineStrategyName,
        String winnerStrategyName,
        int strategyCount,
        int caseCount,
        Instant createTime
) {

    public RetrievalEvaluationComparisonSummary {
        comparisonId = Objects.requireNonNullElse(comparisonId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        datasetId = Objects.requireNonNullElse(datasetId, "");
        baselineStrategyName = Objects.requireNonNullElse(baselineStrategyName, "");
        winnerStrategyName = Objects.requireNonNullElse(winnerStrategyName, "");
        strategyCount = Math.max(0, strategyCount);
        caseCount = Math.max(0, caseCount);
        createTime = createTime == null ? Instant.EPOCH : createTime;
    }
}
