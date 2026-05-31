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

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshExecutionStart;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedule;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshScheduleUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC 文档刷新调度 adapter。
 */
public class JdbcDocumentRefreshScheduleAdapter
        implements DocumentRefreshSchedulePort, DocumentRefreshStateRepositoryPort {

    private static final RowMapper<DocumentRefreshSchedule> SCHEDULE_MAPPER =
            (resultSet, rowNum) -> mapSchedule(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRefreshScheduleAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<DocumentRefreshSchedule> findByDocumentId(String docId) {
        List<DocumentRefreshSchedule> schedules = jdbcTemplate.query("""
                SELECT id, doc_id, kb_id, cron_expr, enabled, next_run_time,
                       last_content_hash, last_etag, last_modified
                FROM t_knowledge_document_schedule
                WHERE doc_id = ?
                """, SCHEDULE_MAPPER, docId);
        return schedules.stream().findFirst();
    }

    @Override
    public List<DocumentRefreshSchedule> findDueSchedules(Instant now, int limit) {
        return jdbcTemplate.query("""
                SELECT id, doc_id, kb_id, cron_expr, enabled, next_run_time,
                       last_content_hash, last_etag, last_modified
                FROM t_knowledge_document_schedule
                WHERE enabled = 1
                  AND (next_run_time IS NULL OR next_run_time <= ?)
                  AND (lock_until IS NULL OR lock_until <= ?)
                ORDER BY next_run_time ASC
                LIMIT ?
                """, SCHEDULE_MAPPER, timestamp(now), timestamp(now), Math.max(limit, 1));
    }

    @Override
    public void upsert(DocumentRefreshSchedule schedule) {
        DocumentRefreshSchedule safeSchedule = Objects.requireNonNull(schedule, "schedule must not be null");
        String id = hasText(safeSchedule.id()) ? safeSchedule.id() : nextId();
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE t_knowledge_document_schedule
                SET kb_id = ?, cron_expr = ?, enabled = ?, next_run_time = ?, update_time = ?
                WHERE doc_id = ?
                """,
                safeSchedule.kbId(),
                safeSchedule.cronExpr(),
                safeSchedule.enabled() ? 1 : 0,
                timestamp(safeSchedule.nextRunTime()),
                timestamp(now),
                safeSchedule.docId());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document_schedule
                (id, doc_id, kb_id, cron_expr, enabled, next_run_time, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                safeSchedule.docId(),
                safeSchedule.kbId(),
                safeSchedule.cronExpr(),
                safeSchedule.enabled() ? 1 : 0,
                timestamp(safeSchedule.nextRunTime()),
                timestamp(now),
                timestamp(now));
    }

    @Override
    public void updateState(DocumentRefreshScheduleUpdate update) {
        DocumentRefreshScheduleUpdate safeUpdate = Objects.requireNonNull(update, "update must not be null");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE t_knowledge_document_schedule
                SET next_run_time = ?, last_run_time = ?, last_success_time = CASE WHEN ? = 'success' THEN ? ELSE last_success_time END,
                    last_status = ?, last_error = ?, last_content_hash = ?, last_etag = ?, last_modified = ?, update_time = ?
                WHERE id = ?
                """,
                timestamp(safeUpdate.nextRunTime()),
                timestamp(safeUpdate.lastRunTime()),
                safeUpdate.status(),
                timestamp(safeUpdate.lastRunTime()),
                safeUpdate.status(),
                safeUpdate.message(),
                safeUpdate.contentHash(),
                safeUpdate.etag(),
                safeUpdate.lastModified(),
                timestamp(now),
                safeUpdate.scheduleId());
    }

    @Override
    public String start(DocumentRefreshExecutionStart execution) {
        DocumentRefreshExecutionStart safeExecution = Objects.requireNonNull(execution,
                "execution must not be null");
        String id = nextId();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document_schedule_exec
                (id, schedule_id, doc_id, kb_id, status, start_time, create_time, update_time)
                VALUES (?, ?, ?, ?, 'running', ?, ?, ?)
                """,
                id,
                safeExecution.scheduleId(),
                safeExecution.docId(),
                safeExecution.kbId(),
                timestamp(safeExecution.startTime()),
                timestamp(now),
                timestamp(now));
        return id;
    }

    @Override
    public void finish(DocumentRefreshExecutionFinish execution) {
        DocumentRefreshExecutionFinish safeExecution = Objects.requireNonNull(execution,
                "execution must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_knowledge_document_schedule_exec
                SET status = ?, message = ?, end_time = ?, file_name = ?, file_size = ?,
                    content_hash = ?, etag = ?, last_modified = ?, update_time = ?
                WHERE id = ?
                """,
                safeExecution.status(),
                safeExecution.message(),
                timestamp(safeExecution.endTime()),
                safeExecution.fileName(),
                safeExecution.fileSize(),
                safeExecution.contentHash(),
                safeExecution.etag(),
                safeExecution.lastModified(),
                timestamp(Instant.now()),
                safeExecution.executionId());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_document_schedule_exec
                (id, schedule_id, doc_id, kb_id, status, message, start_time, end_time,
                 file_name, file_size, content_hash, etag, last_modified, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                hasText(safeExecution.executionId()) ? safeExecution.executionId() : nextId(),
                safeExecution.scheduleId(),
                safeExecution.docId(),
                safeExecution.kbId(),
                safeExecution.status(),
                safeExecution.message(),
                timestamp(safeExecution.startTime()),
                timestamp(safeExecution.endTime()),
                safeExecution.fileName(),
                safeExecution.fileSize(),
                safeExecution.contentHash(),
                safeExecution.etag(),
                safeExecution.lastModified(),
                timestamp(Instant.now()),
                timestamp(Instant.now()));
    }

    private static DocumentRefreshSchedule mapSchedule(ResultSet resultSet) throws java.sql.SQLException {
        return new DocumentRefreshSchedule(
                resultSet.getString("id"),
                resultSet.getString("doc_id"),
                resultSet.getString("kb_id"),
                resultSet.getString("cron_expr"),
                resultSet.getInt("enabled") == 1,
                instant(resultSet.getTimestamp("next_run_time")),
                resultSet.getString("last_content_hash"),
                resultSet.getString("last_etag"),
                resultSet.getString("last_modified"));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String nextId() {
        return SnowflakeIds.nextIdString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
