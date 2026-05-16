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
 * 检索评测运行历史详情。
 *
 * <p>详情保留完整 {@link RetrievalEvaluationReport}，用于回看单次策略回归的命中明细。
 */
public record RetrievalEvaluationRunRecord(
        String runId,
        String knowledgeBaseId,
        String datasetId,
        RetrievalEvaluationReport report,
        Instant createTime
) {

    public RetrievalEvaluationRunRecord {
        runId = Objects.requireNonNullElse(runId, "");
        knowledgeBaseId = Objects.requireNonNullElse(knowledgeBaseId, "");
        datasetId = Objects.requireNonNullElse(datasetId, "");
        report = report == null
                ? new RetrievalEvaluationReport("", 0, 0, 0, 0D, 0D, 0D, 0D, 0D, 0D, java.util.List.of())
                : report;
        createTime = createTime == null ? Instant.EPOCH : createTime;
    }

    public RetrievalEvaluationRunSummary summary() {
        return new RetrievalEvaluationRunSummary(
                runId,
                knowledgeBaseId,
                datasetId,
                report.strategyName(),
                report.topK(),
                report.caseCount(),
                report.evaluableCaseCount(),
                report.recallAtK(),
                report.mrr(),
                report.ndcgAtK(),
                report.emptyRecallRate(),
                report.averageLatencyMs(),
                report.p95LatencyMs(),
                createTime);
    }
}
