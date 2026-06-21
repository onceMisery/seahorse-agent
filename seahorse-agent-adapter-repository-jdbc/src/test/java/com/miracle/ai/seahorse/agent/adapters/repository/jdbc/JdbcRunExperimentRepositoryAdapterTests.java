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

import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRunExperimentRepositoryAdapterTests {

    private JdbcRunExperimentRepositoryAdapter adapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:run-experiment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcRunExperimentRepositoryAdapter(dataSource);
    }

    @Test
    void shouldCreateAndFindExperimentWithTrials() {
        RunExperimentRecord experiment = RunExperimentRecord.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .name("Profile compare")
                .status("PENDING")
                .deleted(0)
                .build();
        List<RunExperimentTrialRecord> trials = List.of(
                RunExperimentTrialRecord.builder().runProfileId(12L).status("PENDING").deleted(0).build(),
                RunExperimentTrialRecord.builder().runProfileId(13L).status("PENDING").deleted(0).build());

        RunExperimentDetails created = adapter.create(experiment, trials);

        assertThat(created.getExperiment().getId()).isNotNull();
        assertThat(created.getTrials()).hasSize(2);
        assertThat(adapter.findById("100", created.getExperiment().getId())).hasValueSatisfying(found -> {
            assertThat(found.getExperiment().getName()).isEqualTo("Profile compare");
            assertThat(found.getExperiment().getConversationId()).isEqualTo(101L);
            assertThat(found.getTrials()).extracting(RunExperimentTrialRecord::getRunProfileId)
                    .containsExactly(12L, 13L);
        });
    }

    @Test
    void shouldCancelExperimentAndScoreTrial() {
        RunExperimentDetails created = adapter.create(RunExperimentRecord.builder()
                .userId("100")
                .conversationId(101L)
                .name("Profile compare")
                .status("PENDING")
                .deleted(0)
                .build(), List.of(RunExperimentTrialRecord.builder()
                .runProfileId(12L)
                .status("PENDING")
                .deleted(0)
                .build()));

        adapter.updateTrialScore("100", created.getExperiment().getId(), created.getTrials().get(0).getId(),
                "{\"rating\":5}");
        RunExperimentDetails cancelled = adapter.updateExperimentStatus("100", created.getExperiment().getId(),
                "CANCELLED").orElseThrow();

        assertThat(cancelled.getExperiment().getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getTrials()).extracting(RunExperimentTrialRecord::getStatus)
                .containsExactly("CANCELLED");
        assertThat(cancelled.getTrials()).extracting(RunExperimentTrialRecord::getScoreJson)
                .containsExactly("{\"rating\":5}");
    }

    @Test
    void shouldUpdateExperimentOnlyStatusAndTrialExecutionResult() {
        RunExperimentDetails created = adapter.create(RunExperimentRecord.builder()
                .userId("100")
                .conversationId(101L)
                .name("Profile compare")
                .status("PENDING")
                .deleted(0)
                .build(), List.of(RunExperimentTrialRecord.builder()
                .runProfileId(12L)
                .status("PENDING")
                .deleted(0)
                .build()));
        Long experimentId = created.getExperiment().getId();
        Long trialId = created.getTrials().get(0).getId();

        adapter.updateExperimentOnlyStatus("100", experimentId, "SUCCEEDED");
        RunExperimentDetails updated = adapter.updateTrialExecution(
                "100",
                experimentId,
                trialId,
                "SUCCEEDED",
                "run-exp-1-trial-10",
                301L,
                "{\"elapsedMs\":12}",
                null).orElseThrow();

        assertThat(updated.getExperiment().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(updated.getTrials()).singleElement().satisfies(trial -> {
            assertThat(trial.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(trial.getRunId()).isEqualTo("run-exp-1-trial-10");
            assertThat(trial.getOutputMessageId()).isEqualTo(301L);
            assertThat(trial.getMetricJson()).isEqualTo("{\"elapsedMs\":12}");
            assertThat(trial.getErrorMessage()).isNull();
        });
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_run_experiment_trial");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sa_run_experiment");
        jdbcTemplate.execute("""
                CREATE TABLE sa_run_experiment (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id BIGINT NOT NULL,
                    conversation_id BIGINT NOT NULL,
                    base_leaf_message_id BIGINT,
                    name VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_run_experiment_trial (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    experiment_id BIGINT NOT NULL,
                    run_profile_id BIGINT NOT NULL,
                    run_id VARCHAR(128),
                    output_message_id BIGINT,
                    score_json TEXT,
                    metric_json TEXT,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    error_message TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
    }
}
