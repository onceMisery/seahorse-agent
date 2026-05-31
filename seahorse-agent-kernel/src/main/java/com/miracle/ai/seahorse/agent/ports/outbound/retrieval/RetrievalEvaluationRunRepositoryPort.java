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

package com.miracle.ai.seahorse.agent.ports.outbound.retrieval;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 检索评测运行结果仓储端口。
 *
 * <p>端口只保存强类型评测报告，不接收原始查询 Map，避免绕过内核评测与过滤治理边界。
 */
public interface RetrievalEvaluationRunRepositoryPort {

    RetrievalEvaluationRunRecord saveRun(String knowledgeBaseId, String datasetId,
                                         RetrievalEvaluationReport report);

    List<RetrievalEvaluationRunSummary> listRuns(String knowledgeBaseId, String datasetId, int limit);

    Optional<RetrievalEvaluationRunRecord> findRun(String knowledgeBaseId, String datasetId, String runId);

    static RetrievalEvaluationRunRepositoryPort empty() {
        return new RetrievalEvaluationRunRepositoryPort() {
            @Override
            public RetrievalEvaluationRunRecord saveRun(String knowledgeBaseId, String datasetId,
                                                        RetrievalEvaluationReport report) {
                return new RetrievalEvaluationRunRecord(SnowflakeIds.nextIdString(),
                        knowledgeBaseId, datasetId, report, Instant.now());
            }

            @Override
            public List<RetrievalEvaluationRunSummary> listRuns(String knowledgeBaseId, String datasetId, int limit) {
                return List.of();
            }

            @Override
            public Optional<RetrievalEvaluationRunRecord> findRun(String knowledgeBaseId, String datasetId,
                                                                  String runId) {
                return Optional.empty();
            }
        };
    }
}
