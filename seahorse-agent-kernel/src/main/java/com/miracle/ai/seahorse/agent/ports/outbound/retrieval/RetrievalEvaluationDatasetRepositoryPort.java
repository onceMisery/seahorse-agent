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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;

import java.util.List;
import java.util.Optional;

/**
 * 检索评测集仓储端口。
 */
public interface RetrievalEvaluationDatasetRepositoryPort {

    List<RetrievalEvaluationDatasetSummary> listDatasets(String knowledgeBaseId, boolean includeDisabled);

    Optional<RetrievalEvaluationDataset> findDataset(String knowledgeBaseId, String datasetId);

    RetrievalEvaluationDataset upsertDataset(String knowledgeBaseId, RetrievalEvaluationDatasetPayload payload);

    boolean deleteDataset(String knowledgeBaseId, String datasetId);

    static RetrievalEvaluationDatasetRepositoryPort empty() {
        return new RetrievalEvaluationDatasetRepositoryPort() {
            @Override
            public List<RetrievalEvaluationDatasetSummary> listDatasets(String knowledgeBaseId,
                                                                        boolean includeDisabled) {
                return List.of();
            }

            @Override
            public Optional<RetrievalEvaluationDataset> findDataset(String knowledgeBaseId, String datasetId) {
                return Optional.empty();
            }

            @Override
            public RetrievalEvaluationDataset upsertDataset(String knowledgeBaseId,
                                                            RetrievalEvaluationDatasetPayload payload) {
                throw new UnsupportedOperationException("retrieval evaluation dataset repository is read-only");
            }

            @Override
            public boolean deleteDataset(String knowledgeBaseId, String datasetId) {
                return false;
            }
        };
    }
}
