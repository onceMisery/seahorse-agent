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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryOutboxRepositoryAdapter implements MemoryOutboxPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryOutboxRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void enqueue(MemoryOutboxTask task) {
        Objects.requireNonNull(task, "task must not be null");
        if (isDerivedIndexDelete(task) && hasExistingDerivedIndexDelete(task)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_memory_outbox
                (id, user_id, tenant_id, task_type, target_id, payload_json, status, attempt_count,
                 last_error, next_retry_time, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?)
                """,
                task.id(),
                task.userId(),
                task.tenantId(),
                task.taskType(),
                task.targetId(),
                JdbcMemorySupport.writeJson(objectMapper, task.payload()),
                "PENDING",
                0,
                task.errorMessage(),
                timestampOrNull(task.nextRetryAt()),
                JdbcMemorySupport.timestamp(task.createdAt()),
                JdbcMemorySupport.timestamp(Instant.now()));
    }

    @Override
    public List<MemoryOutboxTask> pollPending(int limit) {
        int safeLimit = limit > 0 ? limit : 100;
        return jdbcTemplate.query("""
                SELECT id, user_id, tenant_id, task_type, target_id, payload_json, last_error,
                       next_retry_time, create_time
                FROM t_memory_outbox
                WHERE status = 'PENDING'
                  AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP)
                ORDER BY create_time ASC
                LIMIT ?
                """,
                (rs, rowNum) -> new MemoryOutboxTask(
                        rs.getString("id"),
                        rs.getString("task_type"),
                        rs.getString("target_id"),
                        rs.getString("user_id"),
                        rs.getString("tenant_id"),
                        parsePayload(rs.getString("payload_json")),
                        rs.getString("last_error"),
                        instantOrNull(rs.getTimestamp("next_retry_time")),
                        JdbcMemorySupport.instant(rs.getTimestamp("create_time"))),
                safeLimit);
    }

    @Override
    public void markSucceeded(String taskId) {
        jdbcTemplate.update("""
                UPDATE t_memory_outbox
                SET status = 'SUCCEEDED',
                    last_error = NULL,
                    update_time = ?
                WHERE id = ?
                """,
                JdbcMemorySupport.timestamp(Instant.now()),
                taskId);
    }

    @Override
    public void markFailed(String taskId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE t_memory_outbox
                SET status = 'PENDING',
                    attempt_count = attempt_count + 1,
                    last_error = ?,
                    next_retry_time = ?,
                    update_time = ?
                WHERE id = ?
                """,
                Objects.requireNonNullElse(errorMessage, ""),
                JdbcMemorySupport.timestamp(Instant.now().plusSeconds(60)),
                JdbcMemorySupport.timestamp(Instant.now()),
                taskId);
    }

    private boolean isDerivedIndexDelete(MemoryOutboxTask task) {
        return MemoryOutboxTaskTypes.VECTOR_DELETE.equals(task.taskType())
                || MemoryOutboxTaskTypes.KEYWORD_DELETE.equals(task.taskType())
                || MemoryOutboxTaskTypes.GRAPH_DELETE.equals(task.taskType());
    }

    private boolean hasExistingDerivedIndexDelete(MemoryOutboxTask task) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_memory_outbox
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND task_type = ?
                  AND target_id = ?
                  AND status IN ('PENDING', 'SUCCEEDED')
                """,
                Integer.class,
                task.userId(),
                task.tenantId(),
                task.taskType(),
                task.targetId());
        return count != null && count > 0;
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        return JdbcMemorySupport.parseJson(objectMapper, payloadJson);
    }

    private Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : JdbcMemorySupport.timestamp(instant);
    }
}
