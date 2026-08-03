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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryOutboxRepositoryAdapter implements MemoryOutboxPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public JdbcMemoryOutboxRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this(dataSource, objectMapper, null);
    }

    public JdbcMemoryOutboxRepositoryAdapter(DataSource dataSource,
                                             ObjectMapper objectMapper,
                                             PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
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
                JdbcMemorySupport.toLongId(task.userId()),
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

    private static final int CLAIM_TIMEOUT_SECONDS = 120;

    @Override
    public List<MemoryOutboxTask> pollPending(int limit) {
        int safeLimit = limit > 0 ? limit : 100;
        reclaimExpiredClaims();
        if (transactionTemplate == null) {
            return pollPendingWithoutClaim(safeLimit);
        }
        // 原子 claim：在同一事务内用 FOR UPDATE SKIP LOCKED 锁定待处理行并将其标记为
        // CLAIMED。两个 relay 实例同时 poll 时，第二个实例的 FOR UPDATE SKIP LOCKED
        // 会跳过已被第一个实例锁定的行，从而避免跨实例重复投递外部副作用。
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            int claimed = jdbcTemplate.update("""
                    UPDATE t_memory_outbox
                    SET status = 'CLAIMED',
                        update_time = ?
                    WHERE id IN (
                        SELECT id
                        FROM t_memory_outbox
                        WHERE status = 'PENDING'
                          AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP)
                        ORDER BY create_time ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                    )
                    """,
                    JdbcMemorySupport.timestamp(now),
                    safeLimit);
            if (claimed == 0) {
                return List.of();
            }
            return jdbcTemplate.query("""
                    SELECT id, user_id, tenant_id, task_type, target_id, payload_json, last_error,
                           next_retry_time, create_time
                    FROM t_memory_outbox
                    WHERE status = 'CLAIMED'
                      AND update_time >= ?
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
                    JdbcMemorySupport.timestamp(now.minusMillis(1)),
                    safeLimit);
        });
    }

    private void reclaimExpiredClaims() {
        // 进程在 handler 执行后崩溃、markSucceeded/markFailed 前会留下 CLAIMED 任务；
        // 超时后将其重置回 PENDING，使其他实例或重启后的实例能重新投递。这保证
        // CLAIMED 不是永久卡死状态（设计 §9 restart recovery）。
        jdbcTemplate.update("""
                UPDATE t_memory_outbox
                SET status = 'PENDING',
                    update_time = ?
                WHERE status = 'CLAIMED'
                  AND update_time <= ?
                """,
                JdbcMemorySupport.timestamp(Instant.now()),
                JdbcMemorySupport.timestamp(Instant.now().minusSeconds(CLAIM_TIMEOUT_SECONDS)));
    }

    private List<MemoryOutboxTask> pollPendingWithoutClaim(int limit) {
        // 无事务管理器的回退路径：先用 UPDATE 原子地标记待处理任务为 CLAIMED，
        // 再查询本次被标记的任务。与事务路径共享同一状态机
        // （PENDING -> CLAIMED -> SUCCEEDED/FAILED），保证
        // markSucceeded/markFailed 的 WHERE status = 'CLAIMED' 条件始终成立。
        // SELECT 用本次 UPDATE 的时间戳精确过滤，避免把之前已 claim 的任务重复返回。
        Instant now = Instant.now();
        int claimed = jdbcTemplate.update("""
                UPDATE t_memory_outbox
                SET status = 'CLAIMED',
                    update_time = ?
                WHERE id IN (
                    SELECT id
                    FROM t_memory_outbox
                    WHERE status = 'PENDING'
                      AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP)
                    ORDER BY create_time ASC
                    LIMIT ?
                )
                """,
                JdbcMemorySupport.timestamp(now),
                limit);
        if (claimed == 0) {
            // 本次没有 claim 到任何任务（全部已被其他实例/先前 poll 标记为 CLAIMED），
            // 直接返回空，避免通过时间窗口把之前 claim 的任务误当成本次的。
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, user_id, tenant_id, task_type, target_id, payload_json, last_error,
                       next_retry_time, create_time
                FROM t_memory_outbox
                WHERE status = 'CLAIMED'
                  AND update_time >= ?
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
                JdbcMemorySupport.timestamp(now.minusMillis(1)),
                limit);
    }

    @Override
    public void markSucceeded(String taskId) {
        jdbcTemplate.update("""
                UPDATE t_memory_outbox
                SET status = 'SUCCEEDED',
                    last_error = NULL,
                    update_time = ?
                WHERE id = ?
                  AND status = 'CLAIMED'
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
                  AND status = 'CLAIMED'
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
                JdbcMemorySupport.toLongId(task.userId()),
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
