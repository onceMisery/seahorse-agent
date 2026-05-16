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
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDataset;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetPayload;
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

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:retrieval-evaluation-dataset;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void createTable(DriverManagerDataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_retrieval_evaluation_dataset");
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
    }
}
