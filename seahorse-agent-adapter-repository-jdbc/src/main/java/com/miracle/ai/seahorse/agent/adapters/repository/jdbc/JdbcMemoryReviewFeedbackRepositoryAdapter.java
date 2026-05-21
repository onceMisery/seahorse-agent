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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryReviewFeedbackRepositoryAdapter implements MemoryReviewFeedbackRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryReviewFeedbackRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void save(MemoryReviewFeedbackSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        jdbcTemplate.update("""
                INSERT INTO t_memory_review_feedback_sample
                (id, candidate_id, operation_id, user_id, tenant_id, requested_action, review_status,
                 reviewer_id, reviewer_comment, target_layer, target_kind, target_key,
                 rejected_content, chosen_content, rejected_metadata, chosen_metadata, source_message_ids,
                 reviewed_memory_id, reviewed_layer, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON),
                        ?, ?, ?)
                """,
                sample.sampleId(),
                sample.candidateId(),
                sample.operationId(),
                sample.userId(),
                sample.tenantId(),
                sample.requestedAction(),
                sample.reviewStatus().name(),
                sample.reviewerId(),
                sample.reviewComment(),
                sample.targetLayer(),
                sample.targetKind(),
                sample.targetKey(),
                sample.rejectedContent(),
                sample.chosenContent(),
                JdbcMemorySupport.writeJson(objectMapper, sample.rejectedMetadata()),
                JdbcMemorySupport.writeJson(objectMapper, sample.chosenMetadata()),
                JdbcMemorySupport.writeJson(objectMapper, Map.of("ids", sample.sourceMessageIds())),
                sample.reviewedMemoryId(),
                sample.reviewedLayer(),
                JdbcMemorySupport.timestamp(sample.createdAt()));
    }

    @Override
    public List<MemoryReviewFeedbackSample> listByCandidate(String candidateId, int limit) {
        int safeLimit = limit > 0 ? limit : 20;
        return jdbcTemplate.query("""
                SELECT *
                FROM t_memory_review_feedback_sample
                WHERE candidate_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """,
                this::mapRecord,
                Objects.requireNonNullElse(candidateId, ""),
                safeLimit);
    }

    private MemoryReviewFeedbackSample mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryReviewFeedbackSample(
                text(rs.getString("id")),
                text(rs.getString("candidate_id")),
                text(rs.getString("operation_id")),
                text(rs.getString("tenant_id")),
                text(rs.getString("user_id")),
                text(rs.getString("requested_action")),
                reviewStatus(rs.getString("review_status")),
                text(rs.getString("reviewer_id")),
                text(rs.getString("reviewer_comment")),
                text(rs.getString("target_layer")),
                text(rs.getString("target_kind")),
                text(rs.getString("target_key")),
                text(rs.getString("rejected_content")),
                text(rs.getString("chosen_content")),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("rejected_metadata")),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("chosen_metadata")),
                sourceIds(rs.getString("source_message_ids")),
                text(rs.getString("reviewed_memory_id")),
                text(rs.getString("reviewed_layer")),
                JdbcMemorySupport.instant(rs.getTimestamp("create_time")));
    }

    private List<String> sourceIds(String json) {
        Object ids = JdbcMemorySupport.parseJson(objectMapper, json).get("ids");
        if (ids instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    private MemoryReviewStatus reviewStatus(String status) {
        if (!JdbcMemorySupport.hasText(status)) {
            return MemoryReviewStatus.PENDING;
        }
        try {
            return MemoryReviewStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return MemoryReviewStatus.PENDING;
        }
    }

    private String text(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
