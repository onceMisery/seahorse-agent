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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationComparisonRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationRunRepositoryPort;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 检索评测集管理服务。
 *
 * <p>评测集只保存强类型评测样本；实际指标计算继续复用 {@link RetrievalEvaluationInboundPort}。
 */
public class KernelRetrievalEvaluationDatasetService implements RetrievalEvaluationDatasetInboundPort {

    private final RetrievalEvaluationDatasetRepositoryPort repositoryPort;
    private final RetrievalEvaluationComparisonRepositoryPort comparisonRepositoryPort;
    private final RetrievalEvaluationRunRepositoryPort runRepositoryPort;
    private final RetrievalEvaluationInboundPort evaluationPort;

    public KernelRetrievalEvaluationDatasetService(RetrievalEvaluationDatasetRepositoryPort repositoryPort,
                                                   RetrievalEvaluationInboundPort evaluationPort) {
        this(repositoryPort, RetrievalEvaluationComparisonRepositoryPort.empty(),
                RetrievalEvaluationRunRepositoryPort.empty(), evaluationPort);
    }

    public KernelRetrievalEvaluationDatasetService(RetrievalEvaluationDatasetRepositoryPort repositoryPort,
                                                   RetrievalEvaluationRunRepositoryPort runRepositoryPort,
                                                   RetrievalEvaluationInboundPort evaluationPort) {
        this(repositoryPort, RetrievalEvaluationComparisonRepositoryPort.empty(), runRepositoryPort, evaluationPort);
    }

    public KernelRetrievalEvaluationDatasetService(RetrievalEvaluationDatasetRepositoryPort repositoryPort,
                                                   RetrievalEvaluationComparisonRepositoryPort comparisonRepositoryPort,
                                                   RetrievalEvaluationRunRepositoryPort runRepositoryPort,
                                                   RetrievalEvaluationInboundPort evaluationPort) {
        this.repositoryPort = Objects.requireNonNullElse(repositoryPort,
                RetrievalEvaluationDatasetRepositoryPort.empty());
        this.comparisonRepositoryPort = Objects.requireNonNullElse(comparisonRepositoryPort,
                RetrievalEvaluationComparisonRepositoryPort.empty());
        this.runRepositoryPort = Objects.requireNonNullElse(runRepositoryPort,
                RetrievalEvaluationRunRepositoryPort.empty());
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
            RetrievalEvaluationReport fallbackReport = new RetrievalEvaluationReport(evaluationCommand.strategyName(), safeCommand.topK(),
                    dataset.cases().size(), 0, 0D, 0D, 0D, 1D, 0D, 0D, List.of());
            recordRun(safeKnowledgeBaseId, safeCommand.datasetId(), fallbackReport);
            return fallbackReport;
        }
        RetrievalEvaluationReport report = evaluationPort.evaluate(evaluationCommand);
        recordRun(safeKnowledgeBaseId, safeCommand.datasetId(), report);
        return report;
    }

    @Override
    public RetrievalEvaluationComparisonReport compareDataset(String knowledgeBaseId,
                                                              RetrievalEvaluationDatasetComparisonCommand command) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        RetrievalEvaluationDatasetComparisonCommand safeCommand = command == null
                ? new RetrievalEvaluationDatasetComparisonCommand("", "", 5, List.of())
                : command;
        RetrievalEvaluationDataset dataset = getDataset(safeKnowledgeBaseId, safeCommand.datasetId());
        RetrievalEvaluationComparisonCommand comparisonCommand = new RetrievalEvaluationComparisonCommand(
                safeCommand.baselineStrategyName(),
                safeCommand.topK(),
                safeCommand.strategies(),
                dataset.cases());
        if (evaluationPort == null) {
            return new RetrievalEvaluationComparisonReport("", "", List.of(), List.of());
        }
        RetrievalEvaluationComparisonReport report = Objects.requireNonNullElse(
                evaluationPort.compare(comparisonCommand),
                new RetrievalEvaluationComparisonReport("", "", List.of(), List.of()));
        recordComparison(safeKnowledgeBaseId, safeCommand.datasetId(), report);
        Objects.requireNonNullElse(report.reports(), List.<RetrievalEvaluationReport>of())
                .forEach(strategyReport -> recordRun(safeKnowledgeBaseId, safeCommand.datasetId(), strategyReport));
        return report;
    }

    @Override
    public List<RetrievalEvaluationComparisonSummary> listComparisons(String knowledgeBaseId, String datasetId,
                                                                      int limit) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        String safeDatasetId = requireText(datasetId, "datasetId must not be blank");
        getDataset(safeKnowledgeBaseId, safeDatasetId);
        return comparisonRepositoryPort.listComparisons(safeKnowledgeBaseId, safeDatasetId, normalizeLimit(limit));
    }

    @Override
    public RetrievalEvaluationComparisonRecord getComparison(String knowledgeBaseId, String datasetId,
                                                             String comparisonId) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        String safeDatasetId = requireText(datasetId, "datasetId must not be blank");
        String safeComparisonId = requireText(comparisonId, "comparisonId must not be blank");
        getDataset(safeKnowledgeBaseId, safeDatasetId);
        return comparisonRepositoryPort.findComparison(safeKnowledgeBaseId, safeDatasetId, safeComparisonId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "retrieval evaluation comparison not found: " + safeComparisonId));
    }

    @Override
    public List<RetrievalEvaluationRunSummary> listRuns(String knowledgeBaseId, String datasetId, int limit) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        String safeDatasetId = requireText(datasetId, "datasetId must not be blank");
        getDataset(safeKnowledgeBaseId, safeDatasetId);
        return runRepositoryPort.listRuns(safeKnowledgeBaseId, safeDatasetId, normalizeLimit(limit));
    }

    @Override
    public RetrievalEvaluationRunRecord getRun(String knowledgeBaseId, String datasetId, String runId) {
        String safeKnowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId must not be blank");
        String safeDatasetId = requireText(datasetId, "datasetId must not be blank");
        String safeRunId = requireText(runId, "runId must not be blank");
        getDataset(safeKnowledgeBaseId, safeDatasetId);
        return runRepositoryPort.findRun(safeKnowledgeBaseId, safeDatasetId, safeRunId)
                .orElseThrow(() -> new IllegalArgumentException("retrieval evaluation run not found: " + safeRunId));
    }

    private RetrievalEvaluationDatasetPayload validate(RetrievalEvaluationDatasetPayload payload) {
        RetrievalEvaluationDatasetPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        requireText(safePayload.name(), "name must not be blank");
        List<?> cases = Objects.requireNonNullElse(safePayload.cases(), List.of());
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }
        Map<String, Integer> questionIndexes = new LinkedHashMap<>();
        for (int index = 0; index < safePayload.cases().size(); index++) {
            RetrievalEvaluationCase evaluationCase = safePayload.cases().get(index);
            validateCase(index, evaluationCase);
            String normalizedQuestion = normalizeQuestion(evaluationCase.question());
            Integer previousIndex = questionIndexes.putIfAbsent(normalizedQuestion, index);
            if (previousIndex != null) {
                throw new IllegalArgumentException("cases[" + index + "].question duplicates cases["
                        + previousIndex + "].question");
            }
        }
        return safePayload;
    }

    private void validateCase(int index, RetrievalEvaluationCase evaluationCase) {
        if (evaluationCase == null) {
            throw new IllegalArgumentException("cases[" + index + "] must not be null");
        }
        requireText(evaluationCase.question(), "cases[" + index + "].question must not be blank");
        if (evaluationCase.expectedKbIds().isEmpty()
                && evaluationCase.expectedDocIds().isEmpty()
                && evaluationCase.expectedChunkIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "cases[" + index + "] must define at least one expected kb, document, or chunk target");
        }
        validateThreshold(index, "minRecall", evaluationCase.minRecall());
        validateThreshold(index, "minPrecision", evaluationCase.minPrecision());
    }

    private void validateThreshold(int index, String field, Double value) {
        if (value != null && (value < 0D || value > 1D)) {
            throw new IllegalArgumentException("cases[" + index + "]." + field + " must be between 0 and 1");
        }
    }

    private String normalizeQuestion(String question) {
        return Objects.requireNonNullElse(question, "").trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }

    private void recordRun(String knowledgeBaseId, String datasetId, RetrievalEvaluationReport report) {
        try {
            // 评测历史不能反向阻断实时评测，旧库未迁移或历史表短暂不可用时保留主链路返回。
            runRepositoryPort.saveRun(knowledgeBaseId, datasetId, report);
        } catch (RuntimeException ex) {
            // 当前内核无日志端口，后续可接入低基数 observation 记录持久化失败类型。
        }
    }

    private void recordComparison(String knowledgeBaseId, String datasetId, RetrievalEvaluationComparisonReport report) {
        try {
            comparisonRepositoryPort.saveComparison(knowledgeBaseId, datasetId, report);
        } catch (RuntimeException ex) {
            // 与运行历史相同：对比历史失败不阻断本次 compare 返回。
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
