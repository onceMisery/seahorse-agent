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
 * 检索评测运行历史摘要。
 *
 * <p>摘要只保留管理端列表筛选需要的低成本指标，完整用例明细保存在运行详情中。
 */
public record RetrievalEvaluationRunSummary(
        String runId,
        String knowledgeBaseId,
        String datasetId,
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
        Instant createTime
) {

    public RetrievalEvaluationRunSummary {
        runId = Objects.requireNonNullElse(runId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        datasetId = Objects.requireNonNullElse(datasetId, "");
        strategyName = Objects.requireNonNullElse(strategyName, "");
        topK = Math.max(0, topK);
        caseCount = Math.max(0, caseCount);
        evaluableCaseCount = Math.max(0, evaluableCaseCount);
        createTime = createTime == null ? Instant.EPOCH : createTime;
    }
}
