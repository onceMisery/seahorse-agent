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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;

import java.util.List;
import java.util.Objects;

/**
 * 检索评测集管理服务。
 *
 * <p>评测集只保存强类型评测样本；实际指标计算继续复用 {@link RetrievalEvaluationInboundPort}。
 */
public class KernelRetrievalEvaluationDatasetService implements RetrievalEvaluationDatasetInboundPort {

    private final RetrievalEvaluationDatasetRepositoryPort repositoryPort;
    private final RetrievalEvaluationInboundPort evaluationPort;

    public KernelRetrievalEvaluationDatasetService(RetrievalEvaluationDatasetRepositoryPort repositoryPort,
                                                   RetrievalEvaluationInboundPort evaluationPort) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                RetrievalEvaluationDatasetRepositoryPort.empty());
        this.evaluationPort = evaluationPort;
    }

    @Override
    public List<RetrievalEvaluationDatasetSummary> listDatasets(String knowledgeBaseId, boolean includeDisabled) {
        return repositoryPort.listDatasets(requireText(knowledgeBaseId, "knowledgeBaseId must not be blank"),
                includeDisabled);
    }

    @Override
    public RetrievalEvaluationDataset getDataset(String knowledgeBaseId, String datasetId) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        String safeDatasetId = requireText(datasetId, "datasetId must not be blank");
        return repositoryPort.findDataset(safeKnowledgeBaseId, safeDatasetId)
                .orElseThrow(() -> new IllegalArgumentException("retrieval evaluation dataset not found: "
                        + safeDatasetId));
    }

    @Override
    public RetrievalEvaluationDataset upsertDataset(String knowledgeBaseId, RetrievalEvaluationDatasetPayload payload) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        RetrievalEvaluationDatasetPayload safePayload = validate(payload);
        return repositoryPort.upsertDataset(safeKnowledgeBaseId, safePayload);
    }

    @Override
    public boolean deleteDataset(String knowledgeBaseId, String datasetId) {
        return repositoryPort.deleteDataset(
                requireText(knowledgeBaseId, "knowledgeBaseId must not be blank"),
                requireText(datasetId, "datasetId must not be blank"));
    }

    @Override
    public RetrievalEvaluationReport evaluateDataset(String knowledgeBaseId,
                                                     RetrievalEvaluationDatasetRunCommand command) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        RetrievalEvaluationDatasetRunCommand safeCommand = command == null
                ? new RetrievalEvaluationDatasetRunCommand("", "", 5, null)
                : command;
        RetrievalEvaluationDataset dataset = getDataset(safeKnowledgeBaseId, safeCommand.datasetId());
        RetrievalEvaluationCommand evaluationCommand = new RetrievalEvaluationCommand(
                defaultText(safeCommand.strategyName(), dataset.name()),
                safeCommand.topK(),
                safeCommand.options(),
                dataset.cases());
        if (evaluationPort == null) {
            return new RetrievalEvaluationReport(evaluationCommand.strategyName(), safeCommand.topK(),
                    dataset.cases().size(), 0, 0D, 0D, 0D, 1D, 0D, 0D, List.of());
        }
        return evaluationPort.evaluate(evaluationCommand);
    }

    private RetrievalEvaluationDatasetPayload validate(RetrievalEvaluationDatasetPayload payload) {
        RetrievalEvaluationDatasetPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        requireText(safePayload.name(), "name must not be blank");
        return safePayload;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
