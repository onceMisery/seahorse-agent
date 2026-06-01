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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 负责 backfill job 的持久化与分页查询，
 * 将主适配器中的任务仓储职责抽离为独立协作者。
 */
final class JdbcMetadataBackfillSupport {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;

    JdbcMetadataBackfillSupport(JdbcTemplate jdbcTemplate, JdbcMetadataJsonSupport jsonSupport) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    String create(MetadataBackfillJobRecord job) {
        MetadataBackfillJobRecord safeJob = Objects.requireNonNull(job, "job must not be null");
        jdbcTemplate.update("""
                INSERT INTO t_metadata_extraction_job(
                    id, tenant_id, kb_id, pipeline_id, status, current_page, checkpoint_json, batch_size,
                    processed_count, success_count, failed_count, skipped_count, review_count,
                    quarantine_count, failure_summary, operator, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, safeJob.jobId(), safeJob.tenantId(), safeJob.knowledgeBaseId(), safeJob.pipelineId(),
                safeJob.status().name(), safeJob.currentPage(), jsonSupport.json(safeJob.checkpoint()),
                safeJob.batchSize(), safeJob.processedDocuments(), safeJob.succeededDocuments(),
                safeJob.failedDocuments(), safeJob.skippedDocuments(), safeJob.reviewDocuments(),
                safeJob.quarantineDocuments(), jsonSupport.json(safeJob.failures()), safeJob.operator(),
                Timestamp.from(safeJob.createTime()), Timestamp.from(safeJob.updateTime()));
        return safeJob.jobId();
    }

    Optional<MetadataBackfillJobRecord> findById(String jobId) {
        if (blank(jobId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, pipeline_id, status, checkpoint_json, batch_size,
                           current_page,
                           processed_count, success_count, failed_count, skipped_count, review_count,
                           quarantine_count, failure_summary, operator, create_time, update_time
                    FROM t_metadata_extraction_job
                    WHERE id = ?
                    """, this::toBackfillJobRecord, jobId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
        MetadataBackfillJobQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = backfillJobWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_extraction_job " + where.sql(), where.args());
        if (total <= 0) {
            return MetadataBackfillJobPage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataBackfillJobRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, pipeline_id, status, checkpoint_json, batch_size,
                       current_page,
                       processed_count, success_count, failed_count, skipped_count, review_count,
                       quarantine_count, failure_summary, operator, create_time, update_time
                FROM t_metadata_extraction_job
                """ + where.sql() + """
                ORDER BY update_time DESC, create_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::toBackfillJobRecord, args.toArray());
        return new MetadataBackfillJobPage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    void save(MetadataBackfillJobRecord job) {
        MetadataBackfillJobRecord safeJob = Objects.requireNonNull(job, "job must not be null");
        jdbcTemplate.update("""
                UPDATE t_metadata_extraction_job
                SET status = ?,
                    current_page = ?,
                    checkpoint_json = ?,
                    batch_size = ?,
                    processed_count = ?,
                    success_count = ?,
                    failed_count = ?,
                    skipped_count = ?,
                    review_count = ?,
                    quarantine_count = ?,
                    failure_summary = ?,
                    operator = ?,
                    update_time = ?
                WHERE id = ?
                """, safeJob.status().name(), safeJob.currentPage(), jsonSupport.json(safeJob.checkpoint()),
                safeJob.batchSize(), safeJob.processedDocuments(), safeJob.succeededDocuments(),
                safeJob.failedDocuments(), safeJob.skippedDocuments(), safeJob.reviewDocuments(),
                safeJob.quarantineDocuments(), jsonSupport.json(safeJob.failures()), safeJob.operator(),
                Timestamp.from(safeJob.updateTime()), safeJob.jobId());
    }

    private SqlWhere backfillJobWhere(MetadataBackfillJobQuery query) {
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
        if (query.status() != null) {
            sql.append(" AND status = ?");
            args.add(query.status().name());
        }
        if (!blank(query.pipelineId())) {
            sql.append(" AND pipeline_id = ?");
            args.add(query.pipelineId());
        }
        if (!blank(query.operator())) {
            sql.append(" AND operator = ?");
            args.add(query.operator());
        }
        if (!blank(query.documentId())) {
            // checkpoint_json 兼容 documentIds 与 lastDocumentId 等治理字段的文本检索。
            appendJsonTextLike(sql, args, "checkpoint_json", query.documentId());
        }
        if (!blank(query.pauseReason())) {
            appendJsonTextLike(sql, args, "checkpoint_json", "\"pauseReason\"");
            appendJsonTextLike(sql, args, "checkpoint_json", query.pauseReason());
        }
        if (!blank(query.failureKeyword())) {
            appendJsonTextLike(sql, args, "failure_summary", query.failureKeyword());
        }
        if (Boolean.TRUE.equals(query.hasFailures())) {
            sql.append("""
                     AND (status = 'FAILED'
                          OR failed_count > 0
                          OR CAST(failure_summary AS VARCHAR) NOT IN ('', '[]', '{}', 'null'))
                    """);
        } else if (Boolean.FALSE.equals(query.hasFailures())) {
            sql.append("""
                     AND status <> 'FAILED'
                     AND failed_count = 0
                     AND (failure_summary IS NULL
                          OR CAST(failure_summary AS VARCHAR) IN ('', '[]', '{}', 'null'))
                    """);
        }
        if (Boolean.TRUE.equals(query.reExtract())) {
            appendJsonTextLike(sql, args, "checkpoint_json", "\"reExtract\"");
            appendJsonTextLike(sql, args, "checkpoint_json", "true");
        } else if (Boolean.FALSE.equals(query.reExtract())) {
            sql.append("""
                     AND (checkpoint_json IS NULL
                          OR CAST(checkpoint_json AS VARCHAR) NOT LIKE ?
                          OR CAST(checkpoint_json AS VARCHAR) LIKE ?)
                    """);
            args.add("%\"reExtract\"%");
            args.add("%false%");
        }
        return new SqlWhere(sql.toString(), args);
    }

    private void appendJsonTextLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (blank(value)) {
            return;
        }
        sql.append(" AND CAST(").append(column).append(" AS VARCHAR) LIKE ?");
        args.add("%" + value.trim() + "%");
    }

    private MetadataBackfillJobRecord toBackfillJobRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataBackfillJobRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getLong("kb_id"),
                rs.getString("pipeline_id"),
                enumValue(MetadataBackfillJobStatus.class, rs.getString("status"), MetadataBackfillJobStatus.PENDING),
                Math.max(1L, rs.getLong("current_page")),
                Math.max(1, rs.getInt("batch_size")),
                rs.getInt("processed_count"),
                rs.getInt("success_count"),
                rs.getInt("failed_count"),
                rs.getInt("skipped_count"),
                rs.getInt("review_count"),
                rs.getInt("quarantine_count"),
                jsonSupport.readMap(rs.getString("checkpoint_json")),
                jsonSupport.readList(rs.getString("failure_summary")),
                rs.getString("operator"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private long countLong(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private long pages(long total, long size) {
        return size <= 0 ? 0L : (total + size - 1) / size;
    }

    private Instant instant(Timestamp timestamp) {
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
}
