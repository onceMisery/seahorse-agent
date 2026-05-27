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
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalCandidateRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.EvalCandidateStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcEvalCandidateRepositoryAdapter implements EvalCandidateRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEvalCandidateRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<EvalCandidate> findById(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT candidate_id, run_id, message_id, user_query, assistant_response,
                       feedback_reason, status, reviewer_note, created_at, decided_at
                FROM sa_eval_candidate
                WHERE candidate_id = ?
                """, this::mapCandidate, candidateId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public void save(EvalCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        jdbcTemplate.update("""
                INSERT INTO sa_eval_candidate
                (candidate_id, run_id, message_id, user_query, assistant_response,
                 feedback_reason, status, reviewer_note, created_at, decided_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (candidate_id) DO UPDATE SET
                  status = EXCLUDED.status,
                  reviewer_note = EXCLUDED.reviewer_note,
                  decided_at = EXCLUDED.decided_at
                """,
                candidate.candidateId(),
                candidate.runId(),
                candidate.messageId(),
                candidate.userQuery(),
                candidate.assistantResponse(),
                candidate.feedbackReason(),
                candidate.status().name(),
                candidate.reviewerNote(),
                timestamp(candidate.createdAt()),
                candidate.decidedAt() != null ? Timestamp.from(candidate.decidedAt()) : null);
    }

    private EvalCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new EvalCandidate(
                rs.getString("candidate_id"),
                rs.getString("run_id"),
                rs.getString("message_id"),
                rs.getString("user_query"),
                rs.getString("assistant_response"),
                rs.getString("feedback_reason"),
                EvalCandidateStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer_note"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("decided_at")));
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(Objects.requireNonNullElse(instant, Instant.now()));
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
