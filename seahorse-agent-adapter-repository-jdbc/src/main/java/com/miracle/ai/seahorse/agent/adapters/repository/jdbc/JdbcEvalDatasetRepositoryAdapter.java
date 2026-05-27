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

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetQueryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalSample;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JdbcEvalDatasetRepositoryAdapter implements EvalDatasetRepositoryPort, EvalDatasetQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEvalDatasetRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void addSample(EvalSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sa_eval_sample WHERE sample_id = ?",
                Integer.class, sample.sampleId());
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sa_eval_sample
                (sample_id, dataset_id, user_query, expected_response, feedback_reason, source_run_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                sample.sampleId(),
                sample.datasetId(),
                sample.userQuery(),
                sample.expectedResponse(),
                sample.feedbackReason(),
                sample.sourceRunId(),
                Timestamp.from(Instant.now()));
    }

    @Override
    public List<EvalSample> findByDatasetId(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT sample_id, dataset_id, user_query, expected_response, feedback_reason, source_run_id
                FROM sa_eval_sample
                WHERE dataset_id = ?
                ORDER BY created_at
                """, this::mapSample, datasetId.trim());
    }

    private EvalSample mapSample(ResultSet rs, int rowNum) throws SQLException {
        return new EvalSample(
                rs.getString("sample_id"),
                rs.getString("dataset_id"),
                rs.getString("user_query"),
                rs.getString("expected_response"),
                rs.getString("feedback_reason"),
                rs.getString("source_run_id"));
    }
}
