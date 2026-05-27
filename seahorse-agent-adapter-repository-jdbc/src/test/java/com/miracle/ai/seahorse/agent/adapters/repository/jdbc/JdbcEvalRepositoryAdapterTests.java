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

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalCandidate;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalCandidateStatus;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalSample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEvalRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcEvalCandidateRepositoryAdapter candidateAdapter;
    private JdbcEvalDatasetRepositoryAdapter datasetAdapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:eval-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        candidateAdapter = new JdbcEvalCandidateRepositoryAdapter(dataSource);
        datasetAdapter = new JdbcEvalDatasetRepositoryAdapter(dataSource);
    }

    @Test
    void shouldSaveAndFindCandidateById() {
        Instant now = Instant.now();
        EvalCandidate candidate = new EvalCandidate(
                "cand-1", "run-1", "msg-1",
                "What is Java?", "Java is a programming language.",
                "too generic", EvalCandidateStatus.PENDING, null, now, null);

        candidateAdapter.save(candidate);

        assertThat(candidateAdapter.findById("cand-1"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.candidateId()).isEqualTo("cand-1");
                    assertThat(c.runId()).isEqualTo("run-1");
                    assertThat(c.messageId()).isEqualTo("msg-1");
                    assertThat(c.userQuery()).isEqualTo("What is Java?");
                    assertThat(c.assistantResponse()).isEqualTo("Java is a programming language.");
                    assertThat(c.feedbackReason()).isEqualTo("too generic");
                    assertThat(c.status()).isEqualTo(EvalCandidateStatus.PENDING);
                    assertThat(c.reviewerNote()).isNull();
                    assertThat(c.decidedAt()).isNull();
                });
    }

    @Test
    void shouldReturnEmptyForNonExistentCandidate() {
        assertThat(candidateAdapter.findById("non-existent")).isEmpty();
        assertThat(candidateAdapter.findById(null)).isEmpty();
        assertThat(candidateAdapter.findById("  ")).isEmpty();
    }

    @Test
    void shouldUpsertCandidateStatusTransition() {
        Instant now = Instant.now();
        EvalCandidate pending = new EvalCandidate(
                "cand-2", "run-2", "msg-2",
                "Explain Spring", "Spring is a framework.",
                "incomplete", EvalCandidateStatus.PENDING, null, now, null);
        candidateAdapter.save(pending);

        Instant decidedAt = now.plusSeconds(60);
        EvalCandidate accepted = pending.accept("good enough", decidedAt);
        candidateAdapter.save(accepted);

        assertThat(candidateAdapter.findById("cand-2"))
                .isPresent()
                .hasValueSatisfying(c -> {
                    assertThat(c.status()).isEqualTo(EvalCandidateStatus.ACCEPTED);
                    assertThat(c.reviewerNote()).isEqualTo("good enough");
                    assertThat(c.decidedAt()).isNotNull();
                });
    }

    @Test
    void shouldAddSampleIdempotently() {
        EvalSample sample = new EvalSample(
                "sample-1", "dataset-1",
                "What is Java?", "Java is a programming language.",
                "too generic", "run-1");

        datasetAdapter.addSample(sample);
        datasetAdapter.addSample(sample);

        List<EvalSample> results = datasetAdapter.findByDatasetId("dataset-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).sampleId()).isEqualTo("sample-1");
    }

    @Test
    void shouldReturnSamplesOrderedByCreatedAt() throws Exception {
        datasetAdapter.addSample(new EvalSample(
                "sample-a", "ds-order", "Q1", "A1", null, "run-1"));
        Thread.sleep(10);
        datasetAdapter.addSample(new EvalSample(
                "sample-b", "ds-order", "Q2", "A2", null, "run-2"));

        List<EvalSample> results = datasetAdapter.findByDatasetId("ds-order");
        assertThat(results).extracting(EvalSample::sampleId)
                .containsExactly("sample-a", "sample-b");
    }

    @Test
    void shouldReturnEmptyListForBlankDatasetId() {
        assertThat(datasetAdapter.findByDatasetId(null)).isEmpty();
        assertThat(datasetAdapter.findByDatasetId("  ")).isEmpty();
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_eval_candidate");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_eval_sample");
        jdbcTemplate.execute("""
                CREATE TABLE sa_eval_candidate (
                  candidate_id VARCHAR(64) PRIMARY KEY,
                  run_id VARCHAR(64) NOT NULL,
                  message_id VARCHAR(64),
                  user_query TEXT NOT NULL,
                  assistant_response TEXT NOT NULL,
                  feedback_reason VARCHAR(1000),
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  reviewer_note VARCHAR(1000),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  decided_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_eval_sample (
                  sample_id VARCHAR(64) PRIMARY KEY,
                  dataset_id VARCHAR(64) NOT NULL,
                  user_query TEXT NOT NULL,
                  expected_response TEXT NOT NULL,
                  feedback_reason VARCHAR(1000),
                  source_run_id VARCHAR(64),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}