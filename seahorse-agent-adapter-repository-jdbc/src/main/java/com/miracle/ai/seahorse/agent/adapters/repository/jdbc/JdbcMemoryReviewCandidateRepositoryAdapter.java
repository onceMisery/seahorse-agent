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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JdbcMemoryReviewCandidateRepositoryAdapter implements MemoryReviewManagementRepositoryPort {

    private static final MemoryReviewStatus STATUS_PENDING = MemoryReviewStatus.PENDING;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryReviewCandidateRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void save(MemoryReviewCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        jdbcTemplate.update("""
                INSERT INTO t_memory_review_candidate
                (id, operation_id, user_id, tenant_id, conversation_id, message_id, requested_action,
                 target_layer, target_kind, target_key, candidate_content, confidence_level, importance_score,
                 value_score, risk_score, reason, source_message_ids, candidate_metadata, review_status,
                 reviewer_id, reviewer_comment, chosen_content, chosen_metadata, reviewed_memory_id, reviewed_layer,
                 create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?,
                        NULL, NULL, NULL, CAST(? AS JSON), NULL, NULL, ?, ?, 0)
                """,
                candidate.candidateId(),
                candidate.operationId(),
                candidate.userId(),
                candidate.tenantId(),
                candidate.conversationId(),
                candidate.messageId(),
                candidate.requestedAction().name(),
                candidate.targetLayer(),
                candidate.targetKind(),
                candidate.targetKey(),
                candidate.content(),
                candidate.confidence(),
                candidate.importance(),
                candidate.valueScore(),
                candidate.riskScore(),
                candidate.reason(),
                JdbcMemorySupport.writeJson(objectMapper, sourceMessageIds(candidate)),
                JdbcMemorySupport.writeJson(objectMapper, candidate.metadata()),
                STATUS_PENDING.name(),
                JdbcMemorySupport.writeJson(objectMapper, Map.of()),
                JdbcMemorySupport.timestamp(candidate.createdAt()),
                JdbcMemorySupport.timestamp(Instant.now()));
    }

    @Override
    public MemoryReviewPage pageReviewCandidates(MemoryReviewQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Object> args = new ArrayList<>();
        String where = whereClause(query, args);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_memory_review_candidate WHERE " + where,
                Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.size());
        pageArgs.add(query.offset());
        List<MemoryReviewRecord> records = jdbcTemplate.query(
                "SELECT * FROM t_memory_review_candidate WHERE " + where
                        + " ORDER BY update_time DESC, create_time DESC LIMIT ? OFFSET ?",
                this::mapRecord,
                pageArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        long pages = safeTotal == 0L ? 0L : (safeTotal + query.size() - 1L) / query.size();
        return new MemoryReviewPage(records, safeTotal, query.size(), query.current(), pages);
    }

    @Override
    public Optional<MemoryReviewRecord> findReviewItem(String candidateId) {
        List<MemoryReviewRecord> records = jdbcTemplate.query(
                "SELECT * FROM t_memory_review_candidate WHERE id = ? AND deleted = 0",
                this::mapRecord,
                Objects.requireNonNullElse(candidateId, ""));
        return records.stream().findFirst();
    }

    @Override
    public MemoryReviewRecord applyReviewDecision(MemoryReviewDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_memory_review_candidate
                SET review_status = ?,
                    reviewer_id = ?,
                    reviewer_comment = ?,
                    chosen_content = ?,
                    chosen_metadata = CAST(? AS JSON),
                    reviewed_memory_id = ?,
                    reviewed_layer = ?,
                    update_time = ?
                WHERE id = ? AND deleted = 0 AND review_status = ?
                """,
                decision.reviewStatus().name(),
                decision.reviewerId(),
                decision.reviewComment(),
                decision.chosenContent(),
                JdbcMemorySupport.writeJson(objectMapper, decision.chosenMetadata()),
                decision.reviewedMemoryId(),
                decision.reviewedLayer(),
                JdbcMemorySupport.timestamp(Instant.now()),
                decision.candidateId(),
                expectedStatus(decision.reviewStatus()).name());
        if (updated == 0) {
            MemoryReviewRecord current = findReviewItem(decision.candidateId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "memory review candidate not found: " + decision.candidateId()));
            throw new IllegalStateException(staleDecisionMessage(current.candidateId(), decision.reviewStatus()));
        }
        return findReviewItem(decision.candidateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "memory review candidate not found: " + decision.candidateId()));
    }

    private MemoryReviewStatus expectedStatus(MemoryReviewStatus nextStatus) {
        return switch (nextStatus) {
            case APPLIED, PENDING -> MemoryReviewStatus.APPLYING;
            case APPLYING, REJECTED -> STATUS_PENDING;
        };
    }

    private String staleDecisionMessage(String candidateId, MemoryReviewStatus nextStatus) {
        if (nextStatus == MemoryReviewStatus.APPLIED || nextStatus == MemoryReviewStatus.PENDING) {
            return "review candidate is not applying: " + candidateId;
        }
        return "review candidate is not pending: " + candidateId;
    }

    private Map<String, Object> sourceMessageIds(MemoryReviewCandidate candidate) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("ids", candidate.sourceMessageIds());
        return values;
    }

    private String whereClause(MemoryReviewQuery query, List<Object> args) {
        StringBuilder sql = new StringBuilder("deleted = 0");
        if (JdbcMemorySupport.hasText(query.tenantId())) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (JdbcMemorySupport.hasText(query.userId())) {
            sql.append(" AND user_id = ?");
            args.add(query.userId());
        }
        if (query.reviewStatus() != null) {
            sql.append(" AND review_status = ?");
            args.add(query.reviewStatus().name());
        }
        if (JdbcMemorySupport.hasText(query.targetKind())) {
            sql.append(" AND target_kind = ?");
            args.add(query.targetKind());
        }
        if (JdbcMemorySupport.hasText(query.targetKey())) {
            sql.append(" AND target_key = ?");
            args.add(query.targetKey());
        }
        return sql.toString();
    }

    private MemoryReviewRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryReviewRecord(
                text(rs.getString("id")),
                text(rs.getString("operation_id")),
                text(rs.getString("tenant_id")),
                text(rs.getString("user_id")),
                text(rs.getString("conversation_id")),
                text(rs.getString("message_id")),
                text(rs.getString("requested_action")),
                text(rs.getString("target_layer")),
                text(rs.getString("target_kind")),
                text(rs.getString("target_key")),
                text(rs.getString("candidate_content")),
                rs.getDouble("confidence_level"),
                rs.getDouble("importance_score"),
                rs.getDouble("value_score"),
                rs.getDouble("risk_score"),
                text(rs.getString("reason")),
                sourceIds(rs.getString("source_message_ids")),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("candidate_metadata")),
                reviewStatus(rs.getString("review_status")),
                text(rs.getString("reviewer_id")),
                text(rs.getString("reviewer_comment")),
                text(rs.getString("chosen_content")),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("chosen_metadata")),
                text(rs.getString("reviewed_memory_id")),
                text(rs.getString("reviewed_layer")),
                JdbcMemorySupport.instant(rs.getTimestamp("create_time")),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
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
