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

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 负责 quarantine 子域的 JDBC 读写与状态维护，
 * 让主适配器不再直接持有隔离区分页和重试细节。
 */
final class JdbcMetadataQuarantineSupport {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;

    JdbcMetadataQuarantineSupport(JdbcTemplate jdbcTemplate, JdbcMetadataJsonSupport jsonSupport) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    void quarantine(MetadataQuarantineItem item) {
        MetadataQuarantineItem safeItem = Objects.requireNonNull(item, "item must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_quarantine_item(
                        id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                        source_snapshot, retry_count, resolved, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), safeItem.tenantId(), safeItem.knowledgeBaseId(),
                    safeItem.documentId(), safeItem.taskId(), safeItem.stage(), safeItem.reasonCode(),
                    safeItem.reasonMessage(), jsonSupport.json(safeItem.sourceSnapshot()));
        } catch (DataAccessException ignored) {
        }
    }

    MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
        MetadataQuarantineQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = quarantineWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_quarantine_item " + where.sql(), where.args());
        if (total <= 0) {
            return MetadataQuarantinePage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataQuarantineRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                       source_snapshot, retry_count, next_retry_time, resolved, resolved_by,
                       resolved_time, create_time, update_time
                FROM t_metadata_quarantine_item
                """ + where.sql() + """
                ORDER BY resolved ASC, update_time DESC, create_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::toQuarantineRecord, args.toArray());
        return new MetadataQuarantinePage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
        if (blank(itemId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                           source_snapshot, retry_count, next_retry_time, resolved, resolved_by,
                           resolved_time, create_time, update_time
                    FROM t_metadata_quarantine_item
                    WHERE id = ?
                    """, this::toQuarantineRecord, itemId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
        MetadataQuarantineResolution safeResolution = Objects.requireNonNull(resolution,
                "resolution must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_quarantine_item
                SET resolved = 1,
                    resolved_by = ?,
                    resolved_time = CURRENT_TIMESTAMP,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, safeResolution.operator(), safeResolution.itemId());
        if (updated <= 0) {
            throw new IllegalArgumentException("元数据隔离项不存在: " + safeResolution.itemId());
        }
        return findQuarantineItem(safeResolution.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据隔离项不存在: " + safeResolution.itemId()));
    }

    MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
        MetadataQuarantineRetry safeRetry = Objects.requireNonNull(retry, "retry must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_quarantine_item
                SET retry_count = retry_count + 1,
                    next_retry_time = ?,
                    resolved = 0,
                    resolved_by = NULL,
                    resolved_time = NULL,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, Timestamp.from(safeRetry.nextRetryTime()), safeRetry.itemId());
        if (updated <= 0) {
            throw new IllegalArgumentException("元数据隔离项不存在: " + safeRetry.itemId());
        }
        return findQuarantineItem(safeRetry.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据隔离项不存在: " + safeRetry.itemId()));
    }

    private MetadataQuarantineRecord toQuarantineRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataQuarantineRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("job_id"),
                rs.getString("stage"),
                rs.getString("reason_code"),
                rs.getString("reason_message"),
                jsonSupport.readMap(rs.getString("source_snapshot")),
                rs.getInt("retry_count"),
                nullableInstant(rs.getTimestamp("next_retry_time")),
                bool(rs, "resolved"),
                rs.getString("resolved_by"),
                nullableInstant(rs.getTimestamp("resolved_time")),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private SqlWhere quarantineWhere(MetadataQuarantineQuery query) {
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
        if (query.resolved() != null) {
            sql.append(" AND resolved = ?");
            args.add(Boolean.TRUE.equals(query.resolved()) ? 1 : 0);
        }
        if (!blank(query.stage())) {
            sql.append(" AND stage = ?");
            args.add(query.stage());
        }
        if (!blank(query.reasonCode())) {
            sql.append(" AND reason_code = ?");
            args.add(query.reasonCode());
        }
        if (!blank(query.documentId())) {
            sql.append(" AND doc_id = ?");
            args.add(query.documentId());
        }
        if (!blank(query.jobId())) {
            sql.append(" AND job_id = ?");
            args.add(query.jobId());
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

    private boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) == 1;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    record SqlWhere(String sql, List<Object> args) {
    }
}
