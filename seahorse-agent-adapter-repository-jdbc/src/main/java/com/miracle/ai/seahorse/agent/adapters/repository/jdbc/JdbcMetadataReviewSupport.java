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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 负责 review 子域的 JDBC 读写与状态迁移，
 * 主适配器只保留跨子域编排和端口门面。
 */
final class JdbcMetadataReviewSupport {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;

    JdbcMetadataReviewSupport(JdbcTemplate jdbcTemplate, JdbcMetadataJsonSupport jsonSupport) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    void enqueue(MetadataReviewItem item) {
        MetadataReviewItem safeItem = Objects.requireNonNull(item, "item must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_item(
                        id, tenant_id, kb_id, doc_id, result_id, review_status, priority, reason_code,
                        reason_message, suggested_metadata, review_context, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, SnowflakeIds.nextIdString(), safeItem.tenantId(), safeItem.knowledgeBaseId(),
                    safeItem.documentId(), safeItem.resultId(), safeItem.reasonCode(), safeItem.reasonMessage(),
                    jsonSupport.json(safeItem.suggestedMetadata()), jsonSupport.json(safeItem.reviewContext()));
        } catch (DataAccessException ignored) {
        }
    }

    MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
        MetadataReviewQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = reviewWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_review_item " + where.sql(), where.args());
        if (total <= 0) {
            return MetadataReviewPage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataReviewRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, doc_id, result_id, review_status, priority,
                       reason_code, reason_message, suggested_metadata, review_context, corrected_metadata,
                       reviewer_id, review_comment, create_time, update_time
                FROM t_metadata_review_item
                """ + where.sql() + """
                ORDER BY priority DESC, update_time DESC, create_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::toReviewRecord, args.toArray());
        return new MetadataReviewPage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    Optional<MetadataReviewRecord> findReviewItem(String itemId) {
        if (blank(itemId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, doc_id, result_id, review_status, priority,
                           reason_code, reason_message, suggested_metadata, review_context, corrected_metadata,
                           reviewer_id, review_comment, create_time, update_time
                    FROM t_metadata_review_item
                    WHERE id = ?
                    """, this::toReviewRecord, itemId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
        if (blank(itemId)) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, review_item_id, tenant_id, kb_id, doc_id, result_id,
                           from_status, to_status, reviewer_id, review_comment,
                           previous_metadata, updated_metadata, decision_metadata, create_time
                    FROM t_metadata_review_audit
                    WHERE review_item_id = ?
                    ORDER BY create_time ASC, id ASC
                    """, this::toReviewAuditRecord, itemId);
        } catch (DataAccessException ex) {
            return listReviewAuditsLegacy(itemId);
        }
    }

    ReviewDecisionResult applyReviewDecision(MetadataReviewDecision decision) {
        MetadataReviewDecision safeDecision = Objects.requireNonNull(decision, "decision must not be null");
        MetadataReviewRecord current = findReviewItem(safeDecision.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据复核项不存在: " + safeDecision.itemId()));
        Map<String, Object> approvedMetadata = approvedMetadata(current, safeDecision);
        String correctedJson = MetadataReviewStatus.CORRECTED.equals(safeDecision.reviewStatus())
                ? jsonSupport.json(approvedMetadata)
                : null;
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_review_item
                SET review_status = ?,
                    corrected_metadata = ?,
                    reviewer_id = ?,
                    review_comment = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, safeDecision.reviewStatus().name(), correctedJson, safeDecision.reviewerId(),
                safeDecision.reviewComment(), safeDecision.itemId());
        if (updated <= 0) {
            throw new IllegalArgumentException("元数据复核项不存在: " + safeDecision.itemId());
        }
        insertReviewAudit(current, safeDecision, decisionAuditMetadata(safeDecision, approvedMetadata));
        return new ReviewDecisionResult(safeDecision.itemId(), current.resultId(), safeDecision.reviewStatus(),
                approvedMetadata, safeDecision.reviewerId());
    }

    private List<MetadataReviewAuditRecord> listReviewAuditsLegacy(String itemId) {
        try {
            return jdbcTemplate.query("""
                    SELECT id, review_item_id, tenant_id, kb_id, doc_id, result_id,
                           from_status, to_status, reviewer_id, review_comment,
                           decision_metadata, create_time
                    FROM t_metadata_review_audit
                    WHERE review_item_id = ?
                    ORDER BY create_time ASC, id ASC
                    """, this::toReviewAuditRecordLegacy, itemId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private Map<String, Object> decisionAuditMetadata(MetadataReviewDecision decision,
                                                      Map<String, Object> approvedMetadata) {
        if (MetadataReviewStatus.RE_EXTRACTING.equals(decision.reviewStatus())) {
            // RE_EXTRACT 的 correctedMetadata 只作为调度审计上下文，不写回 approved_metadata。
            return decision.correctedMetadata();
        }
        return approvedMetadata;
    }

    private void insertReviewAudit(MetadataReviewRecord current,
                                   MetadataReviewDecision decision,
                                   Map<String, Object> decisionMetadata) {
        Map<String, Object> previousMetadata = previousAuditMetadata(current);
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_audit(
                        id, review_item_id, tenant_id, kb_id, doc_id, result_id,
                        from_status, to_status, reviewer_id, review_comment,
                        previous_metadata, updated_metadata, decision_metadata, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    SnowflakeIds.nextIdString(),
                    current.id(),
                    current.tenantId(),
                    current.knowledgeBaseId(),
                    current.documentId(),
                    current.resultId(),
                    current.reviewStatus().name(),
                    decision.reviewStatus().name(),
                    decision.reviewerId(),
                    decision.reviewComment(),
                    jsonSupport.json(previousMetadata),
                    jsonSupport.json(decisionMetadata),
                    jsonSupport.json(decisionMetadata));
        } catch (DataAccessException ex) {
            insertReviewAuditLegacy(current, decision, decisionMetadata);
        }
    }

    private Map<String, Object> previousAuditMetadata(MetadataReviewRecord current) {
        if (current.correctedMetadata() != null && !current.correctedMetadata().isEmpty()) {
            return current.correctedMetadata();
        }
        return current.suggestedMetadata();
    }

    private void insertReviewAuditLegacy(MetadataReviewRecord current,
                                         MetadataReviewDecision decision,
                                         Map<String, Object> decisionMetadata) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_audit(
                        id, review_item_id, tenant_id, kb_id, doc_id, result_id,
                        from_status, to_status, reviewer_id, review_comment,
                        decision_metadata, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    SnowflakeIds.nextIdString(),
                    current.id(),
                    current.tenantId(),
                    current.knowledgeBaseId(),
                    current.documentId(),
                    current.resultId(),
                    current.reviewStatus().name(),
                    decision.reviewStatus().name(),
                    decision.reviewerId(),
                    decision.reviewComment(),
                    jsonSupport.json(decisionMetadata));
        } catch (DataAccessException ignored) {
        }
    }

    private Map<String, Object> approvedMetadata(MetadataReviewRecord current, MetadataReviewDecision decision) {
        if (MetadataReviewStatus.APPROVED.equals(decision.reviewStatus())) {
            return current.suggestedMetadata();
        }
        if (MetadataReviewStatus.CORRECTED.equals(decision.reviewStatus())) {
            return Objects.requireNonNullElse(decision.correctedMetadata(), Map.of());
        }
        return Map.of();
    }

    private MetadataReviewRecord toReviewRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataReviewRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                enumValue(MetadataReviewStatus.class, rs.getString("review_status"), MetadataReviewStatus.PENDING),
                rs.getInt("priority"),
                rs.getString("reason_code"),
                rs.getString("reason_message"),
                jsonSupport.readMap(rs.getString("suggested_metadata")),
                jsonSupport.readMap(rs.getString("review_context")),
                jsonSupport.readMap(rs.getString("corrected_metadata")),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private MetadataReviewAuditRecord toReviewAuditRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataReviewAuditRecord(
                rs.getString("id"),
                rs.getString("review_item_id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                jsonSupport.readMap(rs.getString("previous_metadata")),
                jsonSupport.readMap(rs.getString("updated_metadata")),
                jsonSupport.readMap(rs.getString("decision_metadata")),
                instant(rs.getTimestamp("create_time")));
    }

    private MetadataReviewAuditRecord toReviewAuditRecordLegacy(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> decisionMetadata = jsonSupport.readMap(rs.getString("decision_metadata"));
        return new MetadataReviewAuditRecord(
                rs.getString("id"),
                rs.getString("review_item_id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                Map.of(),
                decisionMetadata,
                decisionMetadata,
                instant(rs.getTimestamp("create_time")));
    }

    private SqlWhere reviewWhere(MetadataReviewQuery query) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (!blank(query.tenantId())) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (!blank(query.knowledgeBaseId())) {
            sql.append(" AND kb_id = ?");
            args.add(query.knowledgeBaseId());
        }
        if (query.reviewStatus() != null) {
            sql.append(" AND review_status = ?");
            args.add(query.reviewStatus().name());
        }
        if (!blank(query.reasonCode())) {
            sql.append(" AND reason_code = ?");
            args.add(query.reasonCode());
        }
        if (!blank(query.documentId())) {
            sql.append(" AND doc_id = ?");
            args.add(query.documentId());
        }
        return new SqlWhere(sql.toString(), args);
    }

    private long countLong(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private long pages(long total, long size) {
        return size <= 0 ? 0L : (total + size - 1) / size;
    }

    private Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E defaultValue) {
        if (blank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    record SqlWhere(String sql, List<Object> args) {
    }

    record ReviewDecisionResult(String itemId,
                                String resultId,
                                MetadataReviewStatus reviewStatus,
                                Map<String, Object> approvedMetadata,
                                String reviewerId) {
    }
}
