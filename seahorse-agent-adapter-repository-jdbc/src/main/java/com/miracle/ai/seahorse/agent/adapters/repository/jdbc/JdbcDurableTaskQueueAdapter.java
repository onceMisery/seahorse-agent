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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTaskQueuePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBC 的持久化任务队列实现。
 *
 * <p>使用 PostgreSQL FOR UPDATE SKIP LOCKED 实现无锁竞争的任务认领。
 */
public class JdbcDurableTaskQueueAdapter implements DurableTaskQueuePort {

    private static final String SQL_ENQUEUE = """
            INSERT INTO sa_durable_task_queue
            (task_id, run_id, step_type, status, attempt_count, payload_json, created_at)
            VALUES (?, ?, ?, 'PENDING', 0, ?, ?)
            """;

    private static final String SQL_CLAIM_NEXT = """
            UPDATE sa_durable_task_queue
            SET status = 'CLAIMED', worker_id = ?, claimed_at = NOW(), attempt_count = attempt_count + 1
            WHERE task_id = (
                SELECT task_id FROM sa_durable_task_queue
                WHERE status = 'PENDING' AND (retry_at IS NULL OR retry_at <= NOW())
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING task_id, run_id, step_type, attempt_count, created_at, claimed_at, payload_json
            """;

    private static final String SQL_ACK = """
            UPDATE sa_durable_task_queue SET status = 'COMPLETED', completed_at = NOW() WHERE task_id = ?
            """;

    private static final String SQL_RETRY = """
            UPDATE sa_durable_task_queue SET status = 'PENDING', retry_at = ?, last_error = ?, worker_id = NULL WHERE task_id = ?
            """;

    private static final String SQL_FAIL = """
            UPDATE sa_durable_task_queue SET status = 'FAILED', last_error = ?, completed_at = NOW() WHERE task_id = ?
            """;

    private static final String SQL_CANCEL = """
            UPDATE sa_durable_task_queue SET status = 'CANCELLED', completed_at = NOW()
            WHERE run_id = ? AND status IN ('PENDING', 'CLAIMED')
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDurableTaskQueueAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void enqueue(DurableTask task) {
        Objects.requireNonNull(task, "task must not be null");
        jdbcTemplate.update(SQL_ENQUEUE,
                task.taskId(),
                task.runId(),
                task.stepType(),
                task.payloadJson(),
                Timestamp.from(task.createdAt() != null ? task.createdAt() : Instant.now()));
    }

    @Override
    public Optional<DurableTask> claimNext(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_CLAIM_NEXT, this::mapTask, workerId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public void ack(String taskId) {
        if (taskId == null || taskId.isBlank()) return;
        jdbcTemplate.update(SQL_ACK, taskId.trim());
    }

    @Override
    public void retry(String taskId, Instant retryAt, String reason) {
        if (taskId == null || taskId.isBlank()) return;
        jdbcTemplate.update(SQL_RETRY,
                retryAt != null ? Timestamp.from(retryAt) : null,
                reason,
                taskId.trim());
    }

    @Override
    public void fail(String taskId, String reason) {
        if (taskId == null || taskId.isBlank()) return;
        jdbcTemplate.update(SQL_FAIL, reason, taskId.trim());
    }

    @Override
    public void cancel(String runId) {
        if (runId == null || runId.isBlank()) return;
        jdbcTemplate.update(SQL_CANCEL, runId.trim());
    }

    private DurableTask mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new DurableTask(
                rs.getString("task_id"),
                rs.getString("run_id"),
                rs.getString("step_type"),
                rs.getInt("attempt_count"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("claimed_at")),
                rs.getString("payload_json"));
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
