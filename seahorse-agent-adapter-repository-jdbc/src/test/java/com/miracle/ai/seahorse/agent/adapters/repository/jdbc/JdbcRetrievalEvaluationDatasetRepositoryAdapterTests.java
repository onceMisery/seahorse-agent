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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseDiagnostics;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseResult;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationChunkDiagnostic;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationRunRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategyDelta;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRetrievalEvaluationDatasetRepositoryAdapterTests {

    @Test
    void shouldPersistListAndSoftDeleteEvaluationDataset() {
        DriverManagerDataSource dataSource = dataSource();
        createTable(dataSource);
        JdbcRetrievalEvaluationDatasetRepositoryAdapter adapter =
                new JdbcRetrievalEvaluationDatasetRepositoryAdapter(dataSource, new ObjectMapper());

        RetrievalEvaluationDataset created = adapter.upsertDataset("kb-1", payload("dataset-1", true,
                List.of(caseRecord("case-1"))));
        RetrievalEvaluationDataset disabled = adapter.upsertDataset("kb-1", payload("dataset-1", false,
                List.of(caseRecord("case-2"))));

        assertThat(created.datasetId()).isEqualTo("dataset-1");
        assertThat(disabled.enabled()).isFalse();
        assertThat(adapter.listDatasets("kb-1", false)).isEmpty();
        assertThat(adapter.listDatasets("kb-1", true))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.datasetId()).isEqualTo("dataset-1");
                    assertThat(summary.caseCount()).isEqualTo(1);
                    assertThat(summary.enabled()).isFalse();
                });
        assertThat(adapter.findDataset("kb-1", "dataset-1"))
                .get()
                .satisfies(dataset -> assertThat(dataset.cases()).extracting(RetrievalEvaluationCase::caseId)
                        .containsExactly("case-2"));
        RetrievalEvaluationRunRecord run = adapter.saveRun("kb-1", "dataset-1", report("hybrid"));
        assertThat(adapter.listRuns("kb-1", "dataset-1", 10))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.runId()).isEqualTo(run.runId());
                    assertThat(summary.strategyName()).isEqualTo("hybrid");
                    assertThat(summary.recallAtK()).isEqualTo(1D);
                });
        assertThat(adapter.findRun("kb-1", "dataset-1", run.runId()))
                .get()
                .satisfies(record -> {
                    assertThat(record.report().cases()).extracting(RetrievalEvaluationCaseResult::caseId)
                            .containsExactly("case-1");
                    assertThat(record.report().cases().get(0).diagnostics().missingExpectedChunkIds())
                            .containsExactly("missing-1");
                    assertThat(record.report().cases().get(0).diagnostics().retrievedChunks())
                            .extracting(RetrievalEvaluationChunkDiagnostic::chunkId)
                            .containsExactly("chunk-1");
                });
        RetrievalEvaluationComparisonRecord comparison = adapter.saveComparison("kb-1", "dataset-1",
                comparisonReport());
        assertThat(adapter.listComparisons("kb-1", "dataset-1", 10))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.comparisonId()).isEqualTo(comparison.comparisonId());
                    assertThat(summary.winnerStrategyName()).isEqualTo("candidate");
                });
        assertThat(adapter.findComparison("kb-1", "dataset-1", comparison.comparisonId()))
                .get()
                .satisfies(record -> assertThat(record.report().deltas()).extracting(
                        RetrievalEvaluationStrategyDelta::strategyName).containsExactly("candidate"));

        assertThat(adapter.deleteDataset("kb-1", "dataset-1")).isTrue();
        assertThat(adapter.findDataset("kb-1", "dataset-1")).isEmpty();
    }

    private RetrievalEvaluationDatasetPayload payload(String datasetId,
                                                      boolean enabled,
                                                      List<RetrievalEvaluationCase> cases) {
        return new RetrievalEvaluationDatasetPayload(datasetId, "回归集", "用于上线前评测", enabled, cases);
    }

    private RetrievalEvaluationCase caseRecord(String caseId) {
        return new RetrievalEvaluationCase(caseId, "问题", List.of("kb-1"), List.of("doc-1"),
                List.of("chunk-1"), null, null);
    }

    private RetrievalEvaluationReport report(String strategyName) {
        return new RetrievalEvaluationReport(strategyName, 3, 1, 1, 1D, 1D, 1D, 0D, 12D, 12D,
                List.of(new RetrievalEvaluationCaseResult(
                        "case-1", "问题", List.of("chunk-1"), List.of("doc-1"), 1, 1,
                        1D, 1D, 1D, 12L, "SUCCESS", "", 1D, 0, List.of(),
                        new RetrievalEvaluationCaseDiagnostics(
                                List.of("chunk-1", "missing-1"),
                                List.of("doc-1"),
                                List.of("kb-1"),
                                List.of("missing-1"),
                                List.of(),
                                List.of(),
                                List.of(new RetrievalEvaluationChunkDiagnostic(
                                        1, "chunk-1", "doc-1", "kb-1", 1D,
                                        List.of("chunk:chunk-1", "doc:doc-1", "kb:kb-1"), false))))));
    }

    private RetrievalEvaluationComparisonReport comparisonReport() {
        return new RetrievalEvaluationComparisonReport(
                "baseline",
                "candidate",
                List.of(report("baseline"), report("candidate")),
                List.of(new RetrievalEvaluationStrategyDelta("candidate", 0.1D, 0.1D, 0.1D,
                        0D, 1D, 1D)));
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:retrieval-evaluation-dataset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void createTable(DriverManagerDataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_retrieval_evaluation_dataset");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_retrieval_evaluation_run");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_retrieval_evaluation_comparison");
        jdbcTemplate.execute("""
                CREATE TABLE t_retrieval_evaluation_dataset (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64) NOT NULL,
                    dataset_name VARCHAR(128) NOT NULL,
                    description VARCHAR(1024),
                    cases_json CLOB NOT NULL,
                    enabled SMALLINT NOT NULL DEFAULT 1,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_retrieval_evaluation_run (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64) NOT NULL,
                    dataset_id VARCHAR(64) NOT NULL,
                    strategy_name VARCHAR(128) NOT NULL,
                    top_k INTEGER NOT NULL DEFAULT 0,
                    case_count INTEGER NOT NULL DEFAULT 0,
                    evaluable_case_count INTEGER NOT NULL DEFAULT 0,
                    recall_at_k DOUBLE PRECISION NOT NULL DEFAULT 0,
                    mrr DOUBLE PRECISION NOT NULL DEFAULT 0,
                    ndcg_at_k DOUBLE PRECISION NOT NULL DEFAULT 0,
                    empty_recall_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
                    avg_latency_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
                    p95_latency_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
                    report_json CLOB NOT NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_retrieval_evaluation_comparison (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64) NOT NULL,
                    dataset_id VARCHAR(64) NOT NULL,
                    baseline_strategy_name VARCHAR(128) NOT NULL,
                    winner_strategy_name VARCHAR(128) NOT NULL,
                    strategy_count INTEGER NOT NULL DEFAULT 0,
                    case_count INTEGER NOT NULL DEFAULT 0,
                    report_json CLOB NOT NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
