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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 检索评测对比批次仓储端口。
 */
public interface RetrievalEvaluationComparisonRepositoryPort {

    RetrievalEvaluationComparisonRecord saveComparison(String knowledgeBaseId, String datasetId,
                                                       RetrievalEvaluationComparisonReport report);

    List<RetrievalEvaluationComparisonSummary> listComparisons(String knowledgeBaseId, String datasetId, int limit);

    Optional<RetrievalEvaluationComparisonRecord> findComparison(String knowledgeBaseId, String datasetId,
                                                                 String comparisonId);

    static RetrievalEvaluationComparisonRepositoryPort empty() {
        return new RetrievalEvaluationComparisonRepositoryPort() {
            @Override
            public RetrievalEvaluationComparisonRecord saveComparison(String knowledgeBaseId, String datasetId,
                                                                     RetrievalEvaluationComparisonReport report) {
                return new RetrievalEvaluationComparisonRecord(UUID.randomUUID().toString(),
                        knowledgeBaseId, datasetId, report, Instant.now());
            }

            @Override
            public List<RetrievalEvaluationComparisonSummary> listComparisons(String knowledgeBaseId, String datasetId,
                                                                              int limit) {
                return List.of();
            }

            @Override
            public Optional<RetrievalEvaluationComparisonRecord> findComparison(String knowledgeBaseId,
                                                                                String datasetId,
                                                                                String comparisonId) {
                return Optional.empty();
            }
        };
    }
}
