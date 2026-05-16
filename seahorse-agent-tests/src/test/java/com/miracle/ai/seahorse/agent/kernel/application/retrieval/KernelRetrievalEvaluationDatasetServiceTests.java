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

import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategy;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationRunRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelRetrievalEvaluationDatasetServiceTests {

    @Test
    void shouldManageAndRunSavedEvaluationDataset() {
        InMemoryDatasetRepository repository = new InMemoryDatasetRepository();
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        RecordingEvaluationPort evaluationPort = new RecordingEvaluationPort();
        KernelRetrievalEvaluationDatasetService service =
                new KernelRetrievalEvaluationDatasetService(repository, runRepository, evaluationPort);

        RetrievalEvaluationDataset dataset = service.upsertDataset("kb-1", new RetrievalEvaluationDatasetPayload(
                "",
                "上线前回归集",
                "覆盖高频问法",
                true,
                List.of(caseRecord("case-1"))));
        RetrievalEvaluationReport report = service.evaluateDataset("kb-1",
                new RetrievalEvaluationDatasetRunCommand(dataset.datasetId(), "hybrid", 3, null));

        assertThat(service.listDatasets("kb-1", false))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.name()).isEqualTo("上线前回归集");
                    assertThat(summary.caseCount()).isEqualTo(1);
                });
        assertThat(service.getDataset("kb-1", dataset.datasetId()).cases()).hasSize(1);
        assertThat(report.strategyName()).isEqualTo("hybrid");
        assertThat(service.listRuns("kb-1", dataset.datasetId(), 10))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.datasetId()).isEqualTo(dataset.datasetId());
                    assertThat(run.strategyName()).isEqualTo("hybrid");
                    assertThat(run.recallAtK()).isEqualTo(1D);
                });
        assertThat(service.getRun("kb-1", dataset.datasetId(), "run-1").report().strategyName())
                .isEqualTo("hybrid");
        assertThat(evaluationPort.lastCommand.cases()).extracting(RetrievalEvaluationCase::caseId)
                .containsExactly("case-1");

        RetrievalEvaluationComparisonReport comparison = service.compareDataset("kb-1",
                new RetrievalEvaluationDatasetComparisonCommand(
                        dataset.datasetId(),
                        "baseline",
                        3,
                        List.of(
                                new RetrievalEvaluationStrategy("baseline", 3, null),
                                new RetrievalEvaluationStrategy("candidate", 3, null))));
        assertThat(comparison.reports()).extracting(RetrievalEvaluationReport::strategyName)
                .containsExactly("baseline", "candidate");
        assertThat(service.listRuns("kb-1", dataset.datasetId(), 10)).hasSize(3);
    }

    private RetrievalEvaluationCase caseRecord(String caseId) {
        return new RetrievalEvaluationCase(caseId, "什么是制度 A", List.of("kb-1"), List.of("doc-1"),
                List.of("chunk-1"), null, null);
    }

    private static final class RecordingEvaluationPort implements RetrievalEvaluationInboundPort {

        private RetrievalEvaluationCommand lastCommand;

        @Override
        public RetrievalEvaluationReport evaluate(RetrievalEvaluationCommand command) {
            lastCommand = command;
            return new RetrievalEvaluationReport(command.strategyName(), command.topK(), command.cases().size(),
                    command.cases().size(), 1D, 1D, 1D, 0D, 10D, 10D, List.of());
        }

        @Override
        public RetrievalEvaluationComparisonReport compare(RetrievalEvaluationComparisonCommand command) {
            return new RetrievalEvaluationComparisonReport(
                    "baseline",
                    "candidate",
                    List.of(
                            new RetrievalEvaluationReport("baseline", command.topK(), command.cases().size(),
                                    command.cases().size(), 0.8D, 0.8D, 0.8D, 0D, 10D, 10D, List.of()),
                            new RetrievalEvaluationReport("candidate", command.topK(), command.cases().size(),
                                    command.cases().size(), 0.9D, 0.9D, 0.9D, 0D, 11D, 11D, List.of())),
                    List.of());
        }
    }

    private static final class InMemoryDatasetRepository implements RetrievalEvaluationDatasetRepositoryPort {

        private final Map<String, RetrievalEvaluationDataset> datasets = new LinkedHashMap<>();

        @Override
        public List<RetrievalEvaluationDatasetSummary> listDatasets(String knowledgeBaseId, boolean includeDisabled) {
            return datasets.values().stream()
                    .filter(dataset -> dataset.knowledgeBaseId().equals(knowledgeBaseId))
                    .filter(dataset -> includeDisabled || dataset.enabled())
                    .map(RetrievalEvaluationDataset::summary)
                    .toList();
        }

        @Override
        public Optional<RetrievalEvaluationDataset> findDataset(String knowledgeBaseId, String datasetId) {
            RetrievalEvaluationDataset dataset = datasets.get(datasetId);
            if (dataset == null || !dataset.knowledgeBaseId().equals(knowledgeBaseId)) {
                return Optional.empty();
            }
            return Optional.of(dataset);
        }

        @Override
        public RetrievalEvaluationDataset upsertDataset(String knowledgeBaseId,
                                                        RetrievalEvaluationDatasetPayload payload) {
            String datasetId = payload.datasetId().isBlank() ? "dataset-1" : payload.datasetId();
            RetrievalEvaluationDataset dataset = new RetrievalEvaluationDataset(
                    datasetId,
                    knowledgeBaseId,
                    payload.name(),
                    payload.description(),
                    !Boolean.FALSE.equals(payload.enabled()),
                    payload.cases(),
                    Instant.EPOCH,
                    Instant.EPOCH);
            datasets.put(datasetId, dataset);
            return dataset;
        }

        @Override
        public boolean deleteDataset(String knowledgeBaseId, String datasetId) {
            return datasets.remove(datasetId) != null;
        }
    }

    private static final class InMemoryRunRepository implements RetrievalEvaluationRunRepositoryPort {

        private final List<RetrievalEvaluationRunRecord> runs = new ArrayList<>();

        @Override
        public RetrievalEvaluationRunRecord saveRun(String knowledgeBaseId, String datasetId,
                                                    RetrievalEvaluationReport report) {
            RetrievalEvaluationRunRecord record = new RetrievalEvaluationRunRecord(
                    "run-" + (runs.size() + 1), knowledgeBaseId, datasetId, report, Instant.EPOCH);
            runs.add(record);
            return record;
        }

        @Override
        public List<RetrievalEvaluationRunSummary> listRuns(String knowledgeBaseId, String datasetId, int limit) {
            return runs.stream()
                    .filter(run -> run.knowledgeBaseId().equals(knowledgeBaseId))
                    .filter(run -> run.datasetId().equals(datasetId))
                    .limit(limit)
                    .map(RetrievalEvaluationRunRecord::summary)
                    .toList();
        }

        @Override
        public Optional<RetrievalEvaluationRunRecord> findRun(String knowledgeBaseId, String datasetId, String runId) {
            return runs.stream()
                    .filter(run -> run.knowledgeBaseId().equals(knowledgeBaseId))
                    .filter(run -> run.datasetId().equals(datasetId))
                    .filter(run -> run.runId().equals(runId))
                    .findFirst();
        }
    }
}
